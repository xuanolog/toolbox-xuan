package cn.edu.szcu.connect

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID

private val Night = Color(0xFF17132B)
private val Surface = Color(0xFF25203D)
private val Pink = Color(0xFFFF88B0)
private val Peach = Color(0xFFFFBF9E)
private val Muted = Color(0xFFB6AEC8)
private val Mint = Color(0xFF9BE6D4)
private val Ink = Color(0xFF241931)

@Composable
fun SzcuApp(model: ConnectViewModel, connect: () -> Unit, portal: () -> Unit, location: () -> Unit, permissions: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val ssid by model.wifi.ssid.collectAsStateWithLifecycle()
    var management by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    val locked = state.busy || state.loading || state.saving || state.vaultError
    MaterialTheme(colorScheme = darkColorScheme(primary = Pink, onPrimary = Ink, background = Night,
        surface = Surface, onSurface = Color(0xFFFFF4F8), secondary = Mint, outline = Muted)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = Night) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (editing) {
                    ProfileEditor(draft, state.saving, state.notice, { editing = false; model.notice(null) }) { p ->
                        model.save(p) { editing = false }
                    }
                } else {
                    BackHandler(management) { management = false }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_launcher), contentDescription = "Rockstar Games Logo", modifier = Modifier.size(38.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("SZCU CONNECT", fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Text("校园网络 · 随手连接", fontSize = 10.sp, color = Muted, letterSpacing = 1.sp)
                        }
                        TextButton(onClick = { management = !management }) { Text(if (management) "返回首页" else "账号管理", fontSize = 12.sp) }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        if (!management) {
                            SunsetHero()
                            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).background(if (ssid.startsWith("SZCU")) Mint else Muted, RoundedCornerShape(3.dp)))
                                    Spacer(Modifier.width(8.dp))
                                    Text("当前 Wi-Fi", fontSize = 12.sp, color = Muted)
                                    Spacer(Modifier.weight(1f))
                                    Text(ssid, fontSize = 12.sp, color = Mint, modifier = Modifier.weight(1.4f))
                                }
                                Text("连接配置", fontSize = 12.sp, color = Muted, letterSpacing = 2.sp)
                                if (state.book.profiles.isEmpty()) EmptyProfiles(locked) { draft = null; editing = true; model.notice(null) }
                                else state.book.profiles.forEach { profile ->
                                    ProfileCard(profile, state.book.selectedId == profile.id, !locked, { model.select(profile.id) })
                                }
                                Button(onClick = if (state.busy) model::cancel else connect,
                                    enabled = state.busy || (!locked && state.book.profiles.isNotEmpty()),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Pink, contentColor = Ink),
                                    modifier = Modifier.fillMaxWidth().height(60.dp)) {
                                    Text(if (state.busy) "取消连接" else "连接校园网", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(16.dp)); Text(if (state.busy) "×" else "↗", fontSize = 24.sp)
                                }
                                StatusCard(state.status, state.busy)
                                if (state.busy && state.status.stage == Stage.WAITING_WIFI)
                                    OutlinedButton(onClick = model::openPanel, modifier = Modifier.fillMaxWidth()) { Text("打开系统 WLAN 面板") }
                                Notice(state.notice, model::notice)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    TextButton(onClick = portal, enabled = !state.busy) { Text("校园网登录页", fontSize = 12.sp) }
                                    TextButton(onClick = model::openPanel) { Text("WLAN 设置", fontSize = 12.sp, color = Muted) }
                                }
                                if (state.notice?.contains("权限") == true || state.notice?.contains("定位") == true) {
                                    Row { TextButton(onClick = permissions) { Text("应用权限") }; TextButton(onClick = location) { Text("定位开关") } }
                                }
                                Text("账号仅保存在本机   ·   不后台自动连接", fontSize = 10.sp, color = Muted,
                                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 26.dp))
                            }
                        } else {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("你的连接方式", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                Text("宿舍、教室，或另一个运营商。\n每个配置都可以有自己的校园 Wi-Fi。", fontSize = 14.sp, color = Muted, lineHeight = 23.sp)
                                Button(onClick = { draft = null; editing = true; model.notice(null) }, enabled = !locked, modifier = Modifier.fillMaxWidth()) { Text("＋ 添加配置") }
                                if (state.book.profiles.isEmpty()) Text("还没有配置，添加后即可连接。", color = Muted)
                                state.book.profiles.forEach { profile ->
                                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(16.dp)) {
                                        ProfileCard(profile, state.book.selectedId == profile.id, !locked, { model.select(profile.id) }, nested = true)
                                        Row {
                                            TextButton(onClick = { draft = profile; editing = true; model.notice(null) }, enabled = !locked) { Text("编辑") }
                                            TextButton(onClick = { model.select(profile.id) }, enabled = !locked && state.book.selectedId != profile.id) { Text("设为默认") }
                                            Spacer(Modifier.weight(1f))
                                            TextButton(onClick = { deleting = profile }, enabled = !locked) { Text("删除", color = Peach) }
                                        }
                                    }
                                }
                                Notice(state.notice, model::notice)
                                Text("密码使用手机系统密钥加密保存。卸载应用会删除配置。", fontSize = 12.sp, color = Muted, lineHeight = 20.sp)
                            }
                        }
                    }
                }
            }
            deleting?.let { profile ->
                AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除配置？") },
                    text = { Text("将删除“${profile.name}”及其保存的账号密码，不会注销当前校园网会话。") },
                    confirmButton = { TextButton(onClick = { model.delete(profile.id); deleting = null }) { Text("删除") } },
                    dismissButton = { TextButton(onClick = { deleting = null }) { Text("保留") } })
            }
        }
    }
}

@Composable private fun SunsetHero() {
    Box(Modifier.fillMaxWidth().height(245.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRect(Brush.verticalGradient(listOf(Color(0xFF47316A), Color(0xFFAF6190), Peach, Night), endY = h * 1.13f))
            drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFDDB9), Pink)), radius = h * .27f, center = Offset(w * .76f, h * .42f))
            for (i in 0..5) drawLine(Color(0xFFAA6291), Offset(w * .50f, h * (.44f + i * .04f)), Offset(w, h * (.44f + i * .04f)), 3f + i)
            // One palm silhouette: curved trunk and long fronds echo the Vice City sunset.
            val palm = Offset(w * .91f, h * .32f)
            val trunk = Path().apply { moveTo(w * .82f, h); quadraticTo(w * .86f, h * .6f, palm.x, palm.y); lineTo(palm.x + 7f, palm.y); quadraticTo(w * .9f, h * .65f, w * .88f, h); close() }
            drawPath(trunk, Night.copy(alpha = .92f))
            for (i in -3..3) {
                val end = Offset(palm.x + i * w * .068f, palm.y + (kotlin.math.abs(i) * .024f + .08f) * h)
                val leaf = Path().apply { moveTo(palm.x, palm.y); quadraticTo((palm.x + end.x) / 2, palm.y - h * .15f, end.x, end.y); quadraticTo((palm.x + end.x) / 2, palm.y - h * .065f, palm.x, palm.y); close() }
                drawPath(leaf, Night)
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Night), startY = h * .64f))
        }
        Column(Modifier.padding(start = 26.dp, top = 28.dp)) {
            Text("SUZHOU CITY UNIVERSITY", fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 2.sp, color = Color.White.copy(alpha = .8f))
            Spacer(Modifier.height(17.dp))
            Text("STAY\nCONNECTED.", fontSize = 40.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic, letterSpacing = (-1).sp, color = Color(0xFFFFF0E8))
            Spacer(Modifier.height(12.dp))
            Text("回到校园，即刻上线。", fontSize = 13.sp, color = Color(0xFFFFE4ED), letterSpacing = 2.sp)
        }
    }
}

@Composable private fun ProfileCard(profile: Profile, selected: Boolean, enabled: Boolean, select: () -> Unit, nested: Boolean = false) {
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(if (nested) Color.Transparent else Surface)
        .then(if (!nested) Modifier.border(1.dp, if (selected) Pink.copy(alpha = .65f) else Color(0xFF3C3452), shape) else Modifier)
        .clickable(enabled = enabled, onClick = select).padding(if (nested) 0.dp else 17.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(36.dp)) {
            for (i in 1..3) {
                val r = size.width * .14f * i
                drawArc(if (selected) Pink else Muted, 220f, 100f, false,
                    topLeft = Offset(size.width / 2 - r, size.height * .8f - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(3f, cap = StrokeCap.Round))
            }
            drawCircle(if (selected) Pink else Muted, 2.5f, Offset(size.width / 2, size.height * .8f))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("${profile.ssid} · ${profile.carrier.label}", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 5.dp))
            Text(maskAccount(profile.account), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
        }
        Text(if (selected) "已选" else "选择", color = if (selected) Pink else Muted, fontSize = 11.sp)
    }
}
private fun maskAccount(account: String) = if (account.length < 5) "••••" else account.take(2) + "••••" + account.takeLast(2)

@Composable private fun EmptyProfiles(locked: Boolean, add: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(20.dp)) {
        Text("先保存你的校园账号", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("选择运营商，填写学工号和数字门户密码。下次连接无需重复输入。", color = Muted, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(vertical = 12.dp))
        TextButton(onClick = add, enabled = !locked, contentPadding = PaddingValues(0.dp)) { Text("＋ 添加第一个配置", color = Pink) }
    }
}
@Composable private fun StatusCard(status: ConnectionStatus, busy: Boolean) {
    val good = status.stage in listOf(Stage.CONNECTED, Stage.CAMPUS, Stage.ALREADY)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1F1A33)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Pink); Spacer(Modifier.width(10.dp)) }
            Text(status.stage.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (good) Mint else Peach)
        }
        Text(status.detail, fontSize = 12.sp, color = Muted, lineHeight = 20.sp, modifier = Modifier.padding(top = 7.dp))
    }
}
@Composable private fun Notice(text: String?, dismiss: (String?) -> Unit) {
    if (text != null) {
        Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(12.dp)).padding(14.dp)) {
            Text(text, fontSize = 13.sp, color = Peach, lineHeight = 21.sp)
            TextButton(onClick = { dismiss(null) }) { Text("知道了", fontSize = 12.sp) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ProfileEditor(profile: Profile?, saving: Boolean, notice: String?, back: () -> Unit, save: (Profile) -> Unit) {
    val id = remember { profile?.id ?: UUID.randomUUID().toString() }
    var name by remember { mutableStateOf(profile?.name.orEmpty()) }
    var ssid by remember { mutableStateOf(profile?.ssid ?: "SZCU-313-5G") }
    var carrier by remember { mutableStateOf(profile?.carrier) }
    var account by remember { mutableStateOf(profile?.account.orEmpty()) }
    var password by remember { mutableStateOf(profile?.password.orEmpty()) }
    var visible by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val window = checkNotNull(LocalActivity.current).window
    DisposableEffect(Unit) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE); password = "" }
    }
    BackHandler(!saving) { back() }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        TextButton(onClick = back, enabled = !saving, contentPadding = PaddingValues(0.dp)) { Text("← 返回") }
        Text(if (profile == null) "添加校园账号" else "编辑连接配置", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("保存一次，下次轻点连接。", color = Muted, fontSize = 14.sp)
        OutlinedTextField(name, { name = it }, label = { Text("配置名称") }, placeholder = { Text("例如：宿舍 · 电信") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ssid, { ssid = it }, label = { Text("Wi-Fi 名称") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
        ExposedDropdownMenuBox(expanded, { if (!saving) expanded = !expanded }) {
            OutlinedTextField(carrier?.label ?: "请选择运营商", {}, readOnly = true, label = { Text("运营商") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
            ExposedDropdownMenu(expanded, { expanded = false }) {
                Carrier.entries.forEach { c -> DropdownMenuItem(text = { Text(c.label) }, onClick = { carrier = c; expanded = false }) }
            }
        }
        OutlinedTextField(account, { account = it }, label = { Text("学工号") }, supportingText = { Text("不需要填写 @telecom 等运营商后缀") },
            singleLine = true, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("数字门户密码") }, singleLine = true, enabled = !saving,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "隐藏" else "显示", fontSize = 12.sp) } }, modifier = Modifier.fillMaxWidth())
        Text("密码仅加密保存在这台手机。校园网使用原有 HTTP 认证接口，应用只向已确认的校园认证地址提交。", fontSize = 12.sp, color = Muted, lineHeight = 20.sp)
        (error ?: notice)?.let { Text(it, color = Peach, fontSize = 13.sp) }
        Button(onClick = {
            val chosen = carrier
            if (chosen == null) error = "请选择运营商"
            else {
                val p = Profile(id, name.trim(), ssid, chosen, account.trim(), password)
                try { p.validate(); error = null; save(p) } catch (e: IllegalArgumentException) { error = e.message }
            }
        }, enabled = !saving, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(if (saving) "正在保存…" else "保存配置", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}
