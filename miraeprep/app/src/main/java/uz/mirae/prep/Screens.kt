package uz.mirae.prep

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface Route {
    object Main : Route
    data class Lesson(val spec: LessonSpec) : Route
    data class Result(val sum: Summary) : Route
    object Search : Route
}
class Nav(val push: (Route) -> Unit, val pop: () -> Unit, val replace: (Route) -> Unit, val home: () -> Unit)

fun topicIcon(n: String): ImageVector = when {
    n.startsWith("Pedagogika") -> Icons.Default.School
    n.startsWith("Excel") -> Icons.Default.TableChart
    n.startsWith("Xavfsizlik") -> Icons.Default.Security
    n.startsWith("Office") -> Icons.Default.Slideshow
    n.startsWith("HTML") -> Icons.Default.Code
    n.startsWith("Ma'lumotlar") -> Icons.Default.Storage
    n.startsWith("Tarmoq") -> Icons.Default.Public
    n.startsWith("Grafika") -> Icons.Default.Palette
    n.startsWith("Mantiq") -> Icons.Default.Calculate
    n.startsWith("Algoritm") -> Icons.Default.Terminal
    n.startsWith("Kompyuter") -> Icons.Default.Computer
    else -> Icons.Default.Lightbulb
}
@Composable fun topicColor(i: Int): Color = listOf(P.blue, P.green, P.orange, P.purple, P.red, P.yellow)[i % 6]

@Composable
fun Onboarding(store: Store) {
    var name by remember { mutableStateOf("") }
    var goal by remember { mutableIntStateOf(10) }
    Column(Modifier.fillMaxSize().background(P.bg).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        VSpace(16); Mascot(150.dp, 1); VSpace(12)
        T("Salom! Men Mira", 30, 900, align = TextAlign.Center)
        VSpace(6)
        T("Informatika attestatsiyasiga birga tayyorlanamiz. Har kuni oz-ozdan — natija katta bo‘ladi.", 16, 600, P.sub, align = TextAlign.Center, lh = 22)
        VSpace(22)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Ismingiz") }, singleLine = true, shape = RoundedCornerShape(16.dp))
        VSpace(18)
        T("Kunlik maqsad", 18, 800, modifier = Modifier.fillMaxWidth())
        VSpace(8)
        listOf(5 to "Yengil", 10 to "Odatiy", 20 to "Jiddiy").forEach { (g, l) ->
            Card2(Modifier.fillMaxWidth().padding(vertical = 4.dp), { goal = g }, if (goal == g) P.blueSoft else P.card, if (goal == g) P.blue else P.line) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("$g savol / kun", 17, 800, modifier = Modifier.weight(1f)); T(l, 15, 700, if (goal == g) P.blueD else P.sub)
                }
            }
        }
        VSpace(22)
        Chunky("Boshlaymiz", { store.finishOnboarding(name.trim(), goal) }, Modifier.fillMaxWidth())
    }
}

@Composable
fun MainScreen(app: PrepApp, tab: Int, setTab: (Int) -> Unit, nav: Nav) {
    Scaffold(containerColor = P.bg, bottomBar = {
        NavigationBar(containerColor = P.card) {
            listOf("Bosh" to Icons.Default.Home, "Mavzular" to Icons.Default.GridView, "Imtihon" to Icons.Default.Quiz, "Profil" to Icons.Default.Person)
                .forEachIndexed { i, (t, ic) ->
                    NavigationBarItem(tab == i, { setTab(i) }, { Icon(ic, null) }, label = { T(t, 12, 800, if (tab == i) P.blueD else P.sub) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = P.blueSoft, selectedIconColor = P.blueD, unselectedIconColor = P.sub))
                }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) { 0 -> HomeTab(app, nav); 1 -> TopicsTab(app, nav); 2 -> ExamTab(app, nav); else -> ProfileTab(app) }
        }
    }
}

@Composable
fun HomeTab(app: PrepApp, nav: Nav) {
    val s = app.store; val day = today(); val pr = app.progress
    val answered = remember { pr.dailyAnswered(day) }
    val due = remember { pr.dueIds(day).size }
    val weak = remember { pr.weakIds().size }
    val rec = remember {
        val acc = app.engine.topicAccuracy()
        app.bank.topics().minByOrNull { t -> acc[t.name]?.let { if (it.first >= 3) it.second.toFloat() / it.first else 0.6f } ?: 0.3f }
    }
    val tip = remember { listOf(
        "Har kuni 10 ta savol — bir oyda 300 ta savol. Barqarorlik tezlikdan muhimroq!",
        "Xato qilsangiz xafa bo‘lmang: xato savollar avtomatik takrorlashga tushadi.",
        "Kod savollarida o‘zgaruvchilar qiymatini qadamma-qadam daftarga yozib boring.",
        "Sanoq sistemalari uchun 2 ning darajalarini yod oling: 1, 2, 4, 8, 16, 32, 64, 128.",
        "Imtihon rejimida avval oson savollarga javob bering, qiyinlariga keyin qayting.").random() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T(if (s.name.isBlank()) "Salom!" else "Salom, ${s.name}!", 26, 900)
                    T("Bugun ham bir qadam oldinga", 14, 600, P.sub)
                }
                StatChip(Icons.Default.LocalFireDepartment, P.orange, "${s.liveStreak(day)}"); HSpace(12)
                StatChip(Icons.Default.Bolt, P.yellow, "${s.xp}")
            }
        }
        item {
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(answered.toFloat() / s.goal, 104.dp, 12.dp, P.green) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            T("${minOf(answered, 999)}", 24, 900); T("/ ${s.goal}", 12, 700, P.sub)
                        }
                    }
                    HSpace(16)
                    Column(Modifier.weight(1f)) {
                        T("Kunlik maqsad", 18, 900)
                        T(if (answered >= s.goal) "Bugungi maqsad bajarildi! 🎉" else "Yana ${s.goal - answered} ta savol qoldi", 14, 600, P.sub)
                        VSpace(10)
                        Chunky(if (answered == 0) "Boshlash" else "Davom etish", { nav.push(Route.Lesson(app.engine.daily(10))) }, Modifier.fillMaxWidth(), height = 46, icon = Icons.Default.PlayArrow)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MiniCard(Modifier.weight(1f), Icons.Default.Replay, P.blue, "Takrorlash", due) { nav.push(Route.Lesson(app.engine.review())) }
                MiniCard(Modifier.weight(1f), Icons.Default.ErrorOutline, P.red, "Xatolar", weak) { nav.push(Route.Lesson(app.engine.mistakes())) }
            }
        }
        if (rec != null) item {
            Card2(onClick = { nav.push(Route.Lesson(app.engine.topic(rec.name, 10))) }, color = P.blueSoft, border = P.blue) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(topicIcon(rec.name), Color.White, P.blue, 52); HSpace(14)
                    Column(Modifier.weight(1f)) { T("Sizga tavsiya", 12, 800, P.blueD); T(rec.name, 18, 900); T("Bu mavzuni mustahkamlang", 13, 600, P.sub) }
                    Icon(Icons.Default.ChevronRight, null, tint = P.blueD)
                }
            }
        }
        item {
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mascot(64.dp, 1); HSpace(12); T(tip, 14, 600, P.text, Modifier.weight(1f), lh = 20)
                }
            }
        }
    }
}

@Composable
fun MiniCard(mod: Modifier, icon: ImageVector, tint: Color, title: String, count: Int, onClick: () -> Unit) {
    Card2(mod.alpha(if (count == 0) .55f else 1f), if (count > 0) onClick else null) {
        IconBadge(icon, Color.White, tint, 44); VSpace(10)
        T(title, 16, 900); T(if (count == 0) "Hozircha yo‘q" else "$count ta savol", 13, 600, P.sub)
    }
}

@Composable
fun TopicsTab(app: PrepApp, nav: Nav) {
    val topics = remember { app.bank.topics() }
    val srs = remember { app.progress.srsMap() }
    val tm = remember { app.bank.topicMap() }
    val mastered = remember { HashMap<String, Int>().also { m -> srs.forEach { (id, r) -> if (r.box >= 3) tm[id]?.let { t -> m[t] = (m[t] ?: 0) + 1 } } } }
    var pick by remember { mutableStateOf<TopicInfo?>(null) }
    val marks = remember { app.progress.bookmarks().size }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Mavzular", 28, 900, modifier = Modifier.weight(1f))
                IconButton({ nav.push(Route.Search) }) { Icon(Icons.Default.Search, null, tint = P.blueD, modifier = Modifier.size(28.dp)) }
            }
        }
        item {
            Card2(onClick = if (marks > 0) ({ nav.push(Route.Lesson(app.engine.bookmarks())) }) else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.Bookmark, Color.White, P.orange, 48); HSpace(14)
                    Column(Modifier.weight(1f)) { T("Belgilangan savollar", 17, 900); T(if (marks == 0) "Darsda ★ ni bosib saqlang" else "$marks ta savol", 13, 600, P.sub) }
                }
            }
        }
        items(topics.size) { i ->
            val t = topics[i]; val m = mastered[t.name] ?: 0; val col = topicColor(i)
            Card2(onClick = { pick = t }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(topicIcon(t.name), Color.White, col, 52); HSpace(14)
                    Column(Modifier.weight(1f)) {
                        T(t.name, 17, 900)
                        T("${t.total} savol · $m o‘zlashtirilgan", 13, 600, P.sub)
                        VSpace(8)
                        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(P.line)) {
                            Box(Modifier.fillMaxWidth((m.toFloat() / t.total).coerceIn(0f, 1f)).fillMaxHeight().background(col))
                        }
                    }
                }
            }
        }
    }
    pick?.let { t ->
        AlertDialog({ pick = null }, confirmButton = {}, containerColor = P.card,
            title = { T(t.name, 20, 900) },
            text = {
                Column {
                    T("Qancha savol yechamiz?", 14, 600, P.sub); VSpace(14)
                    Chunky("10 ta savol", { pick = null; nav.push(Route.Lesson(app.engine.topic(t.name, 10))) }, Modifier.fillMaxWidth())
                    VSpace(8)
                    Chunky("20 ta savol", { pick = null; nav.push(Route.Lesson(app.engine.topic(t.name, 20))) }, Modifier.fillMaxWidth(), P.blue, P.blueD)
                    VSpace(8)
                    Chunky("Hammasi (${t.total})", { pick = null; nav.push(Route.Lesson(app.engine.topic(t.name, 0))) }, Modifier.fillMaxWidth(), P.purple, P.purpleD)
                }
            })
    }
}

@Composable
fun ExamTab(app: PrepApp, nav: Nav) {
    val vars = remember { app.bank.variants() }
    var pick by remember { mutableStateOf<VariantInfo?>(null) }
    @Composable fun grid(list: List<VariantInfo>) {
        list.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { v ->
                    val b = app.store.bestScore(v.key)
                    val c = when { b < 0 -> P.line; b >= 70 -> P.green; b >= 40 -> P.orange; else -> P.red }
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(P.card).border(2.dp, c, RoundedCornerShape(14.dp))
                        .clickable { pick = v }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        T(v.label, 17, 900); T(if (b < 0) "—" else "$b%", 12, 800, if (b < 0) P.sub else c)
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 24.dp)) {
        item {
            T("Imtihon rejimi", 28, 900); T("Variantni tanlang: taymer bilan imtihon yoki izohli mashq", 14, 600, P.sub); VSpace(18)
            T("Attestatsiya variantlari", 18, 900); VSpace(6)
        }
        item { grid(vars.filter { it.src == "exam" }) }
        item { VSpace(14); T("Mirae mashq to‘plamlari", 18, 900); T("Mualliflik mashqlari, izohlar bilan", 13, 600, P.sub); VSpace(6) }
        item { grid(vars.filter { it.src != "exam" }) }
    }
    pick?.let { v ->
        AlertDialog({ pick = null }, confirmButton = {}, containerColor = P.card,
            title = { T("${v.label}-variant · ${v.count} savol", 19, 900) },
            text = {
                Column {
                    Chunky("Imtihon (${v.count} daqiqa)", { pick = null; nav.push(Route.Lesson(app.engine.variant(v, true))) }, Modifier.fillMaxWidth(), P.orange, P.orangeD, icon = Icons.Default.Timer)
                    VSpace(8)
                    Chunky("Mashq (izoh bilan)", { pick = null; nav.push(Route.Lesson(app.engine.variant(v, false))) }, Modifier.fillMaxWidth())
                    VSpace(10)
                    T("Imtihonda javoblar oxirida ko‘rsatiladi.", 13, 600, P.sub)
                }
            })
    }
}

@Composable
fun ProfileTab(app: PrepApp) {
    val s = app.store; val pr = app.progress; val day = today()
    var tick by remember { mutableIntStateOf(0) }
    val totals = remember(tick) { pr.totals() }
    val days = remember(tick) { pr.lastDays(14, day) }
    val acc = remember(tick) { app.engine.topicAccuracy() }
    val topics = remember { app.bank.topics() }
    var editName by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val lvl = s.xp / 100 + 1
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(84.dp, 1); HSpace(14)
                Column(Modifier.weight(1f)) {
                    T(if (s.name.isBlank()) "O‘quvchi" else s.name, 24, 900)
                    T("$lvl-daraja · ${s.xp} XP", 14, 700, P.sub); VSpace(6)
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(P.line)) {
                        Box(Modifier.fillMaxWidth((s.xp % 100) / 100f).fillMaxHeight().background(P.yellow))
                    }
                }
                IconButton({ editName = true }) { Icon(Icons.Default.Edit, null, tint = P.sub) }
            }
        }
        item {
            val pct = if (totals.first == 0) 0 else totals.second * 100 / totals.first
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Modifier.weight(1f), "Javoblar", "${totals.first}", P.blue)
                StatTile(Modifier.weight(1f), "Aniqlik", "$pct%", P.green)
                StatTile(Modifier.weight(1f), "Eng uzun seriya", "${s.bestStreak}", P.orange)
            }
        }
        item {
            Card2 {
                T("So‘nggi 14 kun", 17, 900); VSpace(10)
                val mx = maxOf(1, days.maxOf { it.second }); val barC = P.green; val trk = P.line
                Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                    val w = size.width / days.size
                    days.forEachIndexed { i, d ->
                        val h = size.height * (d.second.toFloat() / mx).coerceAtLeast(.05f)
                        drawRoundRect(if (d.second > 0) barC else trk, Offset(i * w + w * .15f, size.height - h), Size(w * .7f, h), CornerRadius(8f, 8f))
                    }
                }
            }
        }
        item {
            Card2 {
                T("Mavzular bo‘yicha aniqlik", 17, 900); VSpace(8)
                topics.forEachIndexed { i, t ->
                    val a = acc[t.name]; val p = if (a == null || a.first == 0) -1 else a.second * 100 / a.first
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(t.name, 13, 700, modifier = Modifier.weight(1.2f), maxLines = 1)
                        HSpace(8)
                        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(P.line)) {
                            if (p >= 0) Box(Modifier.fillMaxWidth(p / 100f).fillMaxHeight().background(topicColor(i)))
                        }
                        HSpace(8); T(if (p < 0) "—" else "$p%", 12, 800, P.sub, Modifier.width(36.dp), TextAlign.End)
                    }
                }
            }
        }
        item {
            Card2 {
                T("Sozlamalar", 17, 900); VSpace(6)
                SettingRow("Faqat tasdiqlangan javoblar", "Mavzu va kunlik mashqda faqat tekshirilgan kalitli savollar", s.verifiedOnly) { s.updateVerified(it) }
                SettingRow("Variantlarni aralashtirish", "Javob variantlari tartibi har safar o‘zgaradi", s.shuffle) { s.updateShuffle(it) }
                VSpace(6); T("Kunlik maqsad", 14, 800); VSpace(6)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 20, 30).forEach { g ->
                        val on = s.goal == g
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) P.blueSoft else P.bg)
                            .border(2.dp, if (on) P.blue else P.line, RoundedCornerShape(12.dp)).clickable { s.updateGoal(g) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) { T("$g", 16, 900, if (on) P.blueD else P.sub) }
                    }
                }
            }
        }
        item {
            Card2(onClick = { showAbout = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = P.blue); HSpace(12)
                    T("Savollar bazasi haqida", 15, 800, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = P.sub)
                }
            }
        }
        item { Chunky("Progressni tiklash", { confirmReset = true }, Modifier.fillMaxWidth(), P.red, P.redD, height = 46) }
    }
    if (editName) {
        var v by remember { mutableStateOf(s.name) }
        AlertDialog({ editName = false }, containerColor = P.card, title = { T("Ismingiz", 19, 900) },
            text = { OutlinedTextField(v, { v = it }, singleLine = true, shape = RoundedCornerShape(14.dp)) },
            confirmButton = { TextButton({ s.updateName(v.trim()); editName = false }) { T("Saqlash", 15, 800, P.blueD) } })
    }
    if (confirmReset) AlertDialog({ confirmReset = false }, containerColor = P.card, title = { T("Hammasi o‘chirilsinmi?", 19, 900) },
        text = { T("Barcha javoblar tarixi, XP va seriya o‘chiriladi. Belgilangan savollar saqlanadi.", 14, 600, P.sub) },
        confirmButton = { TextButton({ pr.reset(); s.resetProgress(); tick++; confirmReset = false }) { T("O‘chirish", 15, 800, P.red) } },
        dismissButton = { TextButton({ confirmReset = false }) { T("Bekor", 15, 800, P.sub) } })
    if (showAbout) AlertDialog({ showAbout = false }, containerColor = P.card, title = { T("Savollar bazasi", 19, 900) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                T("${app.bank.total()} ta savol. Mavzular avtomatik tasniflangan.", 14, 700); VSpace(8)
                T(app.bank.note("purpose"), 13, 600, P.sub, lh = 19); VSpace(8)
                T(app.bank.note("quality_limits"), 13, 600, P.sub, lh = 19)
            }
        }, confirmButton = { TextButton({ showAbout = false }) { T("Yopish", 15, 800, P.blueD) } })
}

@Composable
fun StatTile(mod: Modifier, label: String, value: String, tint: Color) {
    Card2(mod) { T(value, 24, 900, tint); T(label, 12, 700, P.sub, maxLines = 2) }
}

@Composable
fun SettingRow(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { T(title, 15, 800); T(sub, 12, 600, P.sub, lh = 16) }
        Switch(on, set, colors = SwitchDefaults.colors(checkedTrackColor = P.green, checkedThumbColor = Color.White))
    }
}

@Composable
fun SearchScreen(app: PrepApp, nav: Nav) {
    var text by remember { mutableStateOf("") }
    val res = remember(text) { if (text.trim().length >= 2) app.bank.search(text.trim()) else emptyList() }
    Column(Modifier.fillMaxSize().background(P.bg).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ nav.pop() }) { Icon(Icons.Default.ArrowBack, null, tint = P.sub) }
            OutlinedTextField(text, { text = it }, Modifier.weight(1f).padding(end = 12.dp), placeholder = { Text("Savol matnidan qidirish") },
                singleLine = true, shape = RoundedCornerShape(16.dp))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (text.trim().length >= 2 && res.isEmpty()) item { T("Hech narsa topilmadi", 15, 700, P.sub, Modifier.padding(16.dp)) }
            items(res, key = { it.id }) { q ->
                Card2(onClick = { nav.push(Route.Lesson(LessonSpec(Kind.SINGLE, "Savol", listOf(q.id)))) }) {
                    T(q.topic, 12, 800, P.blueD); VSpace(2)
                    T(q.text.replace("\n", " "), 15, 700, maxLines = 3, lh = 21)
                }
            }
        }
    }
}
