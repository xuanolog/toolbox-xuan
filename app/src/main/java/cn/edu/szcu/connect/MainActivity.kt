package cn.edu.szcu.connect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model: ConnectViewModel by viewModels()
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.wifi.refresh()
        if (hasPermissions()) connectWithLocation() else model.notice("请允许精确位置与附近 Wi-Fi 权限，才能确认目标网络。可在系统应用权限中重新设置。")
    }
    private val saveNetwork = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { model.afterSaveNetwork() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { SzcuApp(model, ::requestConnect, ::openPortal, ::openLocation, ::openAppSettings) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.wifiActions.collect { action ->
                    try {
                        when (action) {
                            is WifiAction.SaveNetwork -> {
                                if (model.state.value.busy) saveNetwork.launch(Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(
                                    Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(WifiNetworkSuggestion.Builder().setSsid(action.ssid).build())))
                            }
                            WifiAction.Panel -> startActivity(Intent(Settings.Panel.ACTION_WIFI))
                        }
                    } catch (_: Exception) {
                        runCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                            .onFailure { model.notice("请手动进入设置 → WLAN 连接校园网，再返回应用") }
                    }
                }
            }
        }
    }
    override fun onResume() { super.onResume(); model.wifi.refresh() }
    private fun requiredPermissions() = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }.toTypedArray()
    private fun hasPermissions() = requiredPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    private fun requestConnect() {
        if (!hasPermissions()) {
            model.notice("识别校园 Wi-Fi 需要精确位置和附近 Wi-Fi 权限；应用不会采集或上传定位轨迹")
            permissions.launch(requiredPermissions())
        } else connectWithLocation()
    }
    private fun connectWithLocation() {
        if (!getSystemService(LocationManager::class.java).isLocationEnabled) {
            model.notice("请开启系统定位开关，以便 Android 提供 Wi-Fi 名称，再点击连接。应用不读取定位坐标。")
        } else model.connect()
    }
    private fun openLocation() { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
    private fun openAppSettings() { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    private fun openPortal() {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PortalProtocol.ROOT))) }
            .onFailure { model.notice("请在浏览器打开 http://172.16.8.22/") }
    }
}
