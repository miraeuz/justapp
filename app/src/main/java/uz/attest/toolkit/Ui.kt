package uz.attest.toolkit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

private val Indigo = Color(0xFF4F46E5); private val Mint = Color(0xFF10B981); private val Coral = Color(0xFFFF6B6B)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val cs = if (dark) darkColorScheme(primary = Color(0xFF8B85FF), secondary = Mint, tertiary = Coral,
        background = Color(0xFF0F1220), surface = Color(0xFF181C2E), surfaceVariant = Color(0xFF222741))
    else lightColorScheme(primary = Indigo, secondary = Mint, tertiary = Coral,
        background = Color(0xFFF5F6FC), surface = Color.White, surfaceVariant = Color(0xFFE9ECFA))
    MaterialTheme(colorScheme = cs, shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp)), content = content)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { Root() } }
    }
}

@Composable
fun Root() {
    val app = LocalContext.current.applicationContext as App
    val auth by app.tg.auth.collectAsState()
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        val id = app.prefs.get("api_id").toIntOrNull(); val h = app.prefs.get("api_hash")
        if (id != null && h.isNotBlank() && app.tg.auth.value == AuthStep.Setup) app.tg.start(id, h)
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (auth == AuthStep.Ready) Home(app) else AuthScreen(app, auth)
    }
}

@Composable
fun Field(v: String, set: (String) -> Unit, label: String, modifier: Modifier = Modifier, kb: KeyboardType = KeyboardType.Text, secret: Boolean = false) =
    OutlinedTextField(v, set, label = { Text(label) }, singleLine = true, modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = kb),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)

@Composable
fun AuthScreen(app: App, step: AuthStep) {
    val err by app.tg.error.collectAsState()
    var apiId by remember { mutableStateOf(app.prefs.get("api_id")) }
    var apiHash by remember { mutableStateOf(app.prefs.get("api_hash")) }
    var phone by remember { mutableStateOf("+998") }
    var code by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Indigo, Color(0xFF7C3AED)))).padding(20.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                Text("Attest Kit", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                when (step) {
                    AuthStep.Setup -> {
                        Text("my.telegram.org → API development tools sahifasidan api_id va api_hash oling.", fontSize = 14.sp)
                        Field(apiId, { apiId = it }, "api_id", kb = KeyboardType.Number)
                        Field(apiHash, { apiHash = it }, "api_hash")
                        Button({ app.prefs.set("api_id", apiId.trim()); app.prefs.set("api_hash", apiHash.trim()); app.tg.start(apiId.trim().toInt(), apiHash.trim()) },
                            Modifier.fillMaxWidth().height(52.dp), enabled = apiId.trim().toIntOrNull() != null && apiHash.isNotBlank()) { Text("Davom etish") }
                    }
                    AuthStep.Starting -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Ulanmoqda…") }
                    AuthStep.Phone -> {
                        Text("Telegram raqamingiz"); Field(phone, { phone = it }, "+998…", kb = KeyboardType.Phone)
                        Button({ app.tg.sendPhone(phone) }, Modifier.fillMaxWidth().height(52.dp)) { Text("Kod yuborish") }
                    }
                    AuthStep.Code -> {
                        Text("Telegramga kelgan kodni kiriting"); Field(code, { code = it }, "Kod", kb = KeyboardType.Number)
                        Button({ app.tg.sendCode(code) }, Modifier.fillMaxWidth().height(52.dp)) { Text("Tasdiqlash") }
                    }
                    AuthStep.Password -> {
                        Text("Ikki bosqichli parol"); Field(pass, { pass = it }, "Parol", secret = true)
                        Button({ app.tg.sendPassword(pass) }, Modifier.fillMaxWidth().height(52.dp)) { Text("Kirish") }
                    }
                    else -> Text("Qo‘llab-quvvatlanmaydigan bosqich: ${(step as? AuthStep.Other)?.name}")
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
fun Home(app: App) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            listOf("Yig‘ish" to Icons.Default.CloudDownload, "Raqamlar" to Icons.AutoMirrored.Filled.Send, "Baza" to Icons.Default.Storage)
                .forEachIndexed { i, (t, ic) -> NavigationBarItem(tab == i, { tab = i }, { Icon(ic, null) }, label = { Text(t) }) }
        }
    }) { pad ->
        Column(Modifier.padding(pad).statusBarsPadding().padding(horizontal = 16.dp)) {
            when (tab) { 0 -> CollectScreen(app); 1 -> NumbersScreen(app); else -> DataScreen(app) }
        }
    }
}

@Composable
fun Header(title: String, sub: String) {
    Column(Modifier.padding(vertical = 14.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(sub, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
    }
}

@Composable
fun SoftCard(content: @Composable ColumnScope.() -> Unit) =
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }

@Composable
fun RunBar(app: App, label: String, enabled: Boolean, onStart: () -> Unit) {
    val busy by app.runner.busy.collectAsState()
    val prog by app.runner.progress.collectAsState()
    if (busy) {
        Button({ app.runner.stop() }, Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.tertiary)) {
            Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text("To‘xtatish   $prog")
        }
        LinearProgressIndicator(Modifier.fillMaxWidth())
    } else Button(onStart, Modifier.fillMaxWidth().height(54.dp), enabled = enabled) {
        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(label)
    }
}

@Composable
fun LogBox(app: App, modifier: Modifier = Modifier) {
    val logs by app.runner.logs.collectAsState()
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color(0xFF12152A))) {
        LazyColumn(Modifier.padding(14.dp), reverseLayout = true) {
            items(logs.reversed()) { Text(it, color = Color(0xFFB8C0FF), fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        }
    }
}

@Composable
fun ColumnScope.CollectScreen(app: App) {
    val p = app.prefs
    var bot by remember { mutableStateOf(p.get("c_bot", "Yangi_Attestatsiya_TestlariBot")) }
    var subject by remember { mutableStateOf(p.get("c_subj", "Informatika")) }
    var pattern by remember { mutableStateOf(p.get("c_pat", "Informatika {n}-variant")) }
    var from by remember { mutableStateOf(p.get("c_from", "1")) }
    var to by remember { mutableStateOf(p.get("c_to", "50")) }
    var idle by remember { mutableStateOf(p.get("c_idle", "25")) }
    var skip by remember { mutableStateOf(true) }
    var stats by remember { mutableStateOf(0 to 0) }
    val logs by app.runner.logs.collectAsState()
    LaunchedEffect(logs.size) { stats = app.db.counts() }
    Header("Savollarni yig‘ish", "Bot orqali variantlarni avtomatik aylanib, SQLite ga saqlaydi")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
        SoftCard {
            Field(bot, { bot = it }, "Bot username")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(subject, { subject = it }, "Fan tugmasi", Modifier.weight(1f))
                Field(idle, { idle = it }, "Kutish, s", Modifier.width(110.dp), KeyboardType.Number)
            }
            Field(pattern, { pattern = it }, "Variant tugmasi ({n} = raqam)")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(from, { from = it }, "Variantdan", Modifier.weight(1f), KeyboardType.Number)
                Field(to, { to = it }, "Variantgacha", Modifier.weight(1f), KeyboardType.Number)
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(skip, { skip = it }); Spacer(Modifier.width(10.dp)); Text("Yig‘ilgan variantlarni o‘tkazib yuborish") }
            RunBar(app, "Yig‘ishni boshlash", from.toIntOrNull() != null && to.toIntOrNull() != null && bot.isNotBlank()) {
                p.set("c_bot", bot); p.set("c_subj", subject); p.set("c_pat", pattern); p.set("c_from", from); p.set("c_to", to); p.set("c_idle", idle)
                val cfg = Scraper.Cfg(bot, subject, pattern, from.toInt(), to.toInt(), idle.toIntOrNull() ?: 25, skip)
                app.runner.launch("Savollar yig‘ilmoqda") { Scraper(app.tg, app.db, app.runner::log) { app.runner.progress.value = it }.run(cfg) }
            }
        }
        Text("Bazada: ${stats.first} savol · ${stats.second} variant", fontWeight = FontWeight.SemiBold)
        LogBox(app, Modifier.weight(1f))
    }
}

@Composable
fun ColumnScope.NumbersScreen(app: App) {
    val p = app.prefs
    var bots by remember { mutableStateOf(p.get("n_bots", "").split(",").filter { it.isNotBlank() }) }
    var bot by remember { mutableStateOf(p.get("n_bot")) }
    var start by remember { mutableStateOf(p.get("n_s", "1")) }
    var end by remember { mutableStateOf(p.get("n_e", "100")) }
    var step by remember { mutableStateOf(p.get("n_st", "1")) }
    var delayMs by remember { mutableStateOf(p.get("n_d", "1500")) }
    var wait by remember { mutableStateOf(false) }
    var prefix by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    Header("Raqam yuboruvchi", "Tanlangan botga ketma-ket raqamlarni yuboradi")
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SoftCard {
            if (bots.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bots.forEach { b -> FilterChip(bot == b, { bot = b }, { Text("@$b") }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(bot, { bot = it.removePrefix("@") }, "Bot username", Modifier.weight(1f))
                FilledTonalIconButton({ if (bot.isNotBlank() && bot !in bots) { bots = bots + bot; p.set("n_bots", bots.joinToString(",")) } }) { Icon(Icons.Default.Add, "Saqlash") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(start, { start = it }, "Boshi", Modifier.weight(1f), KeyboardType.Number)
                Field(end, { end = it }, "Oxiri", Modifier.weight(1f), KeyboardType.Number)
                Field(step, { step = it }, "Qadam", Modifier.weight(0.7f), KeyboardType.Number)
            }
            Field(delayMs, { delayMs = it }, "Oraliq (ms)  — kamida 700", kb = KeyboardType.Number)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(prefix, { prefix = it }, "Oldidan", Modifier.weight(1f)); Field(suffix, { suffix = it }, "Keyin", Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(wait, { wait = it }); Spacer(Modifier.width(10.dp)); Text("Bot javobini kutib yuborish") }
            val ok = bot.isNotBlank() && start.toIntOrNull() != null && end.toIntOrNull() != null && (step.toIntOrNull() ?: 0).let { st -> (st > 0 && start.toInt() <= end.toInt()) || (st < 0 && start.toInt() >= end.toInt()) }
            RunBar(app, "Yuborishni boshlash", ok) {
                p.set("n_bot", bot); p.set("n_s", start); p.set("n_e", end); p.set("n_st", step); p.set("n_d", delayMs)
                val cfg = NumberSender.Cfg(bot, start.toInt(), end.toInt(), step.toInt(), (delayMs.toLongOrNull() ?: 1500).coerceAtLeast(700), wait, 20, prefix, suffix)
                app.runner.launch("Raqamlar yuborilmoqda") { NumberSender(app.tg, app.runner::log) { app.runner.progress.value = it }.run(cfg) }
            }
        }
        LogBox(app, Modifier.height(240.dp))
    }
}

private fun share(ctx: Context, f: File, mime: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    val i = Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    ctx.startActivity(Intent.createChooser(i, "Eksport"))
}

@Composable
fun ColumnScope.DataScreen(app: App) {
    val ctx = LocalContext.current
    val logs by app.runner.logs.collectAsState()
    var rows by remember { mutableStateOf(listOf<QRow>()) }
    var stats by remember { mutableStateOf(0 to 0) }
    LaunchedEffect(logs.size) { rows = app.db.latest(); stats = app.db.counts() }
    Header("Baza", "${stats.first} savol · ${stats.second} variant")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button({ share(ctx, app.db.exportDb(), "application/octet-stream") }, Modifier.weight(1f).height(50.dp)) { Text("SQLite (.db)") }
        FilledTonalButton({ share(ctx, app.db.exportJson(), "application/json") }, Modifier.weight(1f).height(50.dp)) { Text("JSON") }
    }
    TextButton({ app.tg.logout() }) { Text("Telegramdan chiqish", color = MaterialTheme.colorScheme.tertiary) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
        items(rows) { r ->
            SoftCard {
                Text("${r.variant}-variant · #${r.idx}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(r.question, fontWeight = FontWeight.SemiBold)
                r.options.forEachIndexed { i, o -> Text("${if (i == r.correct) "✅" else "○"}  $o", fontSize = 14.sp) }
                if (r.explanation.isNotBlank()) Text("💡 ${r.explanation}", fontSize = 13.sp)
            }
        }
    }
}
