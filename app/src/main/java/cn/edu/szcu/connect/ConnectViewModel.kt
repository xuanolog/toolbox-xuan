package cn.edu.szcu.connect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException

sealed interface WifiAction {
    data class SaveNetwork(val ssid: String) : WifiAction
    data object Panel : WifiAction
}
data class AppState(val book: ProfileBook = ProfileBook(), val status: ConnectionStatus = ConnectionStatus(),
    val busy: Boolean = false, val loading: Boolean = true, val saving: Boolean = false,
    val notice: String? = null, val vaultError: Boolean = false)

class ConnectViewModel(app: Application) : AndroidViewModel(app) {
    val wifi = WifiConnector(app)
    private val store = ProfileStore(app)
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private val actions = Channel<WifiAction>(Channel.BUFFERED)
    val wifiActions = actions.receiveAsFlow()
    private var operation: Job? = null
    init {
        viewModelScope.launch {
            try { val book = withContext(Dispatchers.IO) { store.read() }; mutable.update { it.copy(book = book, loading = false) } }
            catch (_: Exception) { mutable.update { it.copy(loading = false, vaultError = true, notice = "无法解密已有配置。为保护原文件，已暂停编辑，请勿清除应用数据。") } }
        }
        wifi.refresh()
    }
    fun notice(text: String?) { mutable.update { it.copy(notice = text) } }
    private fun persist(book: ProfileBook, done: () -> Unit = {}) {
        if (mutable.value.loading || mutable.value.saving || mutable.value.busy || mutable.value.vaultError) return
        mutable.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { store.write(book) }
                mutable.update { it.copy(book = book, saving = false, notice = null) }
                done()
            } catch (_: Exception) { mutable.update { it.copy(saving = false, notice = "保存失败，原有配置已保留，请检查手机存储后重试") } }
        }
    }
    fun save(profile: Profile, done: () -> Unit) {
        try { profile.validate() } catch (e: IllegalArgumentException) { notice(e.message); return }
        val book = mutable.value.book
        val profiles = if (book.profiles.any { it.id == profile.id }) book.profiles.map { if (it.id == profile.id) profile else it }
            else book.profiles + profile
        persist(book.copy(profiles = profiles, selectedId = book.selectedId ?: profile.id), done)
    }
    fun select(id: String) {
        val book = mutable.value.book
        if (book.profiles.any { it.id == id }) persist(book.copy(selectedId = id))
    }
    fun delete(id: String) {
        val book = mutable.value.book
        val remaining = book.profiles.filterNot { it.id == id }
        persist(book.copy(profiles = remaining, selectedId = if (book.selectedId == id) remaining.firstOrNull()?.id else book.selectedId))
    }
    fun connect() {
        val state = mutable.value
        if (state.busy || state.saving || state.loading || state.vaultError) return
        val profile = state.book.profiles.find { it.id == state.book.selectedId } ?: return notice("请先添加并选择一个配置")
        mutable.update { it.copy(busy = true, notice = null, status = ConnectionStatus(Stage.WAITING_WIFI, "准备连接 ${profile.ssid}")) }
        operation = viewModelScope.launch {
            try {
                var network = wifi.find(profile.ssid)
                if (network == null) {
                    wifi.suggest(profile.ssid)
                    network = wifi.await(profile.ssid, 5000)
                }
                if (network == null) {
                    mutable.update { it.copy(status = ConnectionStatus(Stage.WAITING_WIFI, "请允许系统保存网络；如未自动连接，在 WLAN 面板选择 ${profile.ssid}，返回后将自动继续")) }
                    actions.send(WifiAction.SaveNetwork(profile.ssid))
                    network = wifi.await(profile.ssid, 90000)
                }
                if (network != null && wifi.ip(network) == null) network = wifi.await(profile.ssid, 15000)
                if (network == null) throw PortalException("等待 Wi-Fi 或地址分配超时。请确认热点在附近，并在系统 WLAN 中选择 ${profile.ssid}")
                ConnectionEngine().run(profile, NetworkPortalSession(network, profile.ssid, wifi)) { progress ->
                    mutable.update { it.copy(status = progress) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val message = when (e) {
                    is PortalException -> e.message ?: "认证未完成"
                    is SocketTimeoutException -> "校园网请求超时，请检查 Wi-Fi 后重试"
                    is SecurityException -> "缺少 Wi-Fi 或位置权限，请在应用权限中允许后重试"
                    else -> "无法访问校园网。请确认 Wi-Fi、关闭影响连接的 VPN，或打开校园网登录页检查"
                }
                mutable.update { it.copy(status = ConnectionStatus(Stage.FAILED, message)) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun cancel() {
        operation?.cancel()
        // busy stays true until the previous job releases its resources, preventing a cancel/restart race.
        mutable.update { it.copy(status = ConnectionStatus(Stage.CANCELLED, "已停止后续认证请求；已发送的请求无法撤回，Wi-Fi 保持连接")) }
    }
    fun afterSaveNetwork() {
        val selected = mutable.value.book.profiles.find { it.id == mutable.value.book.selectedId }
        if (mutable.value.busy && selected != null && wifi.find(selected.ssid) == null) openPanel()
    }
    fun openPanel() { viewModelScope.launch { actions.send(WifiAction.Panel) } }
    override fun onCleared() { wifi.close(); super.onCleared() }
}
