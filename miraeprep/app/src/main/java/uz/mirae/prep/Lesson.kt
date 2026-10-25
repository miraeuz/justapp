package uz.mirae.prep

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private enum class Opt { Idle, Selected, Correct, Wrong, Dim }

private fun mmss(s: Int) = "%d:%02d".format(s / 60, s % 60)

@Composable
fun LessonScreen(app: PrepApp, spec: LessonSpec, onClose: () -> Unit, onFinish: (Summary) -> Unit) {
    val qs = remember(spec) { app.bank.byIds(spec.ids) }
    if (qs.isEmpty()) {
        Column(Modifier.fillMaxSize().background(P.bg).statusBarsPadding().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Mascot(120.dp, 0); VSpace(16)
            T("Bu yerda hozircha savol yo‘q", 20, 900, align = TextAlign.Center); VSpace(6)
            T("Sozlamalardagi “Faqat tasdiqlangan javoblar” filtrini tekshiring yoki boshqa mavzuni tanlang.", 14, 600, P.sub, align = TextAlign.Center)
            VSpace(20); Chunky("Orqaga", onClose, Modifier.fillMaxWidth())
        }
        return
    }
    var i by remember { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf<Int?>(null) }
    var checked by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<Boolean>() }
    var exit by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val t0 = remember { System.currentTimeMillis() }
    var remaining by remember { mutableIntStateOf(spec.timerSec) }
    val haptic = LocalHapticFeedback.current
    val shuffle = remember { app.store.shuffle }
    val orders = remember { HashMap<Int, List<Int>>() }
    fun orderOf(idx: Int) = orders.getOrPut(idx) { if (shuffle) listOf(0, 1, 2, 3).shuffled() else listOf(0, 1, 2, 3) }
    val q = qs[i]
    val order = orderOf(i)
    val correctPos = order.indexOf(q.correct)
    val marked = remember(i) { mutableStateOf(app.progress.isBookmarked(q.id)) }

    fun finish() {
        if (done) return
        done = true
        val n = qs.size; val ok = results.count { it }
        val wrong = qs.indices.filter { results.getOrNull(it) != true }.map { qs[it].id }
        val xp = ok * 10 + if (n >= 5 && ok * 10 >= n * 9) 5 else 0
        if (results.isNotEmpty()) app.store.touchStreak(today())
        if (spec.kind == Kind.SINGLE) { onClose(); return }
        app.store.addXp(xp)
        val pct = ok * 100 / n
        if (spec.kind == Kind.EXAM) app.store.setBestScore(spec.key, pct)
        onFinish(Summary(spec.title, spec.kind, n, ok, xp, ((System.currentTimeMillis() - t0) / 1000).toInt(), wrong, pct))
    }
    val fin = rememberUpdatedState { finish() }
    fun advance() { if (i + 1 < qs.size) { i++; sel = null; checked = false } else finish() }
    fun check() {
        val ok = sel == correctPos
        checked = true; results.add(ok); app.progress.record(q.id, ok, today())
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    fun examNext(skip: Boolean) {
        val s = sel
        val ok = !skip && s != null && s == correctPos
        if (s != null && !skip) app.progress.record(q.id, ok, today())
        results.add(ok); advance()
    }
    if (spec.timerSec > 0) LaunchedEffect(Unit) {
        while (remaining > 0 && !done) { delay(1000); remaining-- }
        if (!done) fin.value()
    }
    BackHandler { exit = true }
    val prog by animateFloatAsState((i + if (checked && spec.immediate) 1 else 0).toFloat() / qs.size, tween(400), label = "prog")

    Column(Modifier.fillMaxSize().background(P.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ exit = true }) { Icon(Icons.Default.Close, null, tint = P.sub, modifier = Modifier.size(28.dp)) }
            Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(P.line)) {
                Box(Modifier.fillMaxWidth(prog.coerceIn(0f, 1f)).fillMaxHeight().background(P.green, RoundedCornerShape(8.dp)))
            }
            if (spec.timerSec > 0) { HSpace(10); T(mmss(remaining), 16, 900, if (remaining < 60) P.red else P.sub) }
            IconButton({ app.progress.toggleBookmark(q.id); marked.value = !marked.value }) {
                Icon(if (marked.value) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, tint = if (marked.value) P.orange else P.sub, modifier = Modifier.size(28.dp))
            }
        }
        AnimatedContent(i, Modifier.weight(1f), transitionSpec = {
            (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260))) togetherWith (slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(160)))
        }, label = "q") { idx ->
            val qq = qs[idx]; val ord = orderOf(idx)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(qq.topic, 12, 800, P.blueD, Modifier.clip(RoundedCornerShape(8.dp)).background(P.blueSoft).padding(horizontal = 8.dp, vertical = 3.dp))
                    Spacer(Modifier.weight(1f)); T("${idx + 1} / ${qs.size}", 13, 800, P.sub)
                }
                QuestionBody(qq)
                VSpace(4)
                ord.forEachIndexed { pos, optIdx ->
                    val st = when {
                        checked && spec.immediate && pos == ord.indexOf(qq.correct) -> Opt.Correct
                        checked && spec.immediate && pos == sel -> Opt.Wrong
                        checked && spec.immediate -> Opt.Dim
                        pos == sel -> Opt.Selected
                        else -> Opt.Idle
                    }
                    OptionCard('A' + pos, qq.options[optIdx], st) { if (!checked) sel = pos }
                }
                VSpace(12)
            }
        }
        if (spec.immediate) {
            AnimatedVisibility(checked, enter = slideInVertically(tween(300)) { it } + fadeIn(), exit = fadeOut()) {
                Feedback(q, results.lastOrNull() == true, "${'A' + correctPos}) ${q.options[q.correct]}", i + 1 == qs.size) { advance() }
            }
            if (!checked) Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
                Chunky("Tekshirish", { check() }, Modifier.fillMaxWidth(), enabled = sel != null)
            }
        } else Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ examNext(true) }) { T("O‘tkazib yuborish", 14, 800, P.sub) }
            HSpace(8)
            Chunky(if (i + 1 == qs.size) "Yakunlash" else "Keyingi", { examNext(false) }, Modifier.weight(1f), enabled = sel != null)
        }
    }
    if (exit) AlertDialog({ exit = false }, containerColor = P.card,
        title = { T(if (spec.timerSec > 0) "Imtihonni yakunlaysizmi?" else "Darsni to‘xtatasizmi?", 19, 900) },
        text = { T(if (spec.timerSec > 0) "Javob berilmagan savollar noto‘g‘ri hisoblanadi." else "Javob bergan savollaringiz saqlanadi.", 14, 600, P.sub) },
        confirmButton = { TextButton({ exit = false; if (spec.timerSec > 0) finish() else onClose() }) { T(if (spec.timerSec > 0) "Yakunlash" else "Chiqish", 15, 800, P.red) } },
        dismissButton = { TextButton({ exit = false }) { T("Davom etish", 15, 800, P.blueD) } })
}

@Composable
private fun QuestionBody(q: Question) {
    val parts = q.text.split("\n", limit = 2)
    T(parts[0], 20, 800, lh = 28)
    if (parts.size > 1 && parts[1].isNotBlank()) {
        VSpace(6)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (P.dark) Color(0xFF0E1519) else Color(0xFF263238))
            .horizontalScroll(rememberScrollState()).padding(14.dp)) {
            T(parts[1].trim(), 14, 600, Color(0xFFE6EDF3), mono = true, lh = 21)
        }
    }
}

@Composable
private fun OptionCard(letter: Char, text: String, st: Opt, onClick: () -> Unit) {
    val (bg, bd, fg) = when (st) {
        Opt.Selected -> Triple(P.blueSoft, P.blue, P.blueD)
        Opt.Correct -> Triple(P.greenSoft, P.green, P.greenD)
        Opt.Wrong -> Triple(P.redSoft, P.red, P.redD)
        Opt.Dim -> Triple(P.card, P.line, P.sub)
        Opt.Idle -> Triple(P.card, P.line, P.text)
    }
    val abg by animateColorAsStateCompat(bg); val abd by animateColorAsStateCompat(bd)
    val sc by animateFloatAsState(if (st == Opt.Selected || st == Opt.Correct) 1.02f else 1f, tween(160), label = "sc")
    Row(Modifier.fillMaxWidth().scale(sc).clip(RoundedCornerShape(16.dp)).background(abg).border(2.dp, abd, RoundedCornerShape(16.dp))
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).border(2.dp, abd, CircleShape), contentAlignment = Alignment.Center) {
            when (st) {
                Opt.Correct -> Icon(Icons.Default.Check, null, tint = P.green, modifier = Modifier.size(20.dp))
                Opt.Wrong -> Icon(Icons.Default.Close, null, tint = P.red, modifier = Modifier.size(20.dp))
                else -> T("$letter", 14, 900, fg)
            }
        }
        HSpace(12)
        T(text, 16, 700, fg, Modifier.weight(1f), lh = 22)
    }
}

@Composable
private fun animateColorAsStateCompat(c: Color) = androidx.compose.animation.animateColorAsState(c, tween(200), label = "col")

@Composable
private fun Feedback(q: Question, ok: Boolean, correctText: String, last: Boolean, onNext: () -> Unit) {
    val praise = remember(q.id) { listOf("Barakalla!", "Ajoyib!", "Zo‘r!", "Mukammal!", "Tog‘ri yo‘ldasiz!").random() }
    val bg = if (ok) P.greenSoft else P.redSoft; val fg = if (ok) P.greenD else P.redD
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(bg).navigationBarsPadding().padding(20.dp)) {
        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(if (ok) P.green else P.red), contentAlignment = Alignment.Center) {
                    Icon(if (ok) Icons.Default.Check else Icons.Default.Close, null, tint = Color.White)
                }
                HSpace(10); T(if (ok) praise else "Noto‘g‘ri", 20, 900, fg)
            }
            if (!ok) { VSpace(8); T("To‘g‘ri javob:", 12, 800, fg); T(correctText, 15, 800, fg, lh = 21) }
            if (q.method.isNotBlank()) { VSpace(10); T("Yechim", 12, 800, fg); T(q.method, 14, 600, P.text, lh = 20) }
            if (q.expl.isNotBlank()) { VSpace(10); T("Izoh", 12, 800, fg); T(q.expl, 14, 600, P.text, lh = 20) }
            if (q.mistake.isNotBlank()) { VSpace(10); T("Ko‘p uchraydigan xato", 12, 800, fg); T(q.mistake, 14, 600, P.text, lh = 20) }
            if (!q.verified) { VSpace(10); T("ⓘ Bu savolning javob kaliti mustaqil tekshirilmagan. Shubha bo‘lsa, darslikdan solishtiring.", 12, 600, P.sub, lh = 17) }
        }
        VSpace(14)
        Chunky(if (last) "Yakunlash" else "Davom etish", onNext, Modifier.fillMaxWidth(), if (ok) P.green else P.red, if (ok) P.greenD else P.redD)
    }
}

@Composable
fun ResultScreen(app: PrepApp, s: Summary, nav: Nav) {
    val good = s.pct >= 70
    BackHandler { nav.home() }
    val answered = remember { app.progress.dailyAnswered(today()) }
    Box(Modifier.fillMaxSize().background(P.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            VSpace(12); Mascot(130.dp, if (good) 1 else if (s.pct >= 40) 0 else 2); VSpace(10)
            T(if (s.pct >= 90) "Mukammal natija!" else if (good) "Ajoyib natija!" else if (s.pct >= 40) "Yomon emas, davom eting!" else "Mashq qiling — albatta o‘xshaydi!", 24, 900, align = TextAlign.Center)
            T(s.title, 14, 700, P.sub); VSpace(18)
            Ring(s.pct / 100f, 150.dp, 14.dp, if (good) P.green else P.orange) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { T("${s.pct}%", 34, 900); T("${s.correct}/${s.total}", 14, 800, P.sub) }
            }
            VSpace(20)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Modifier.weight(1f), "XP", "+${s.xp}", P.yellow)
                StatTile(Modifier.weight(1f), "Vaqt", mmss(s.seconds), P.blue)
                StatTile(Modifier.weight(1f), "Seriya", "${app.store.liveStreak(today())}", P.orange)
            }
            VSpace(10)
            T("Bugun jami $answered ta savol · maqsad ${app.store.goal}", 13, 700, P.sub)
            VSpace(22)
            if (s.wrongIds.isNotEmpty()) {
                Chunky("Xatolarni ko‘rish (${s.wrongIds.size})", { nav.replace(Route.Lesson(LessonSpec(Kind.WRONG, "Xatolar tahlili", s.wrongIds))) },
                    Modifier.fillMaxWidth(), P.red, P.redD)
                VSpace(10)
            }
            Chunky("Davom etish", { nav.home() }, Modifier.fillMaxWidth())
        }
        if (good) Confetti()
    }
}
