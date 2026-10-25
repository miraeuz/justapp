package uz.mirae.prep

import android.app.Application
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import java.io.File
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

const val BANK_VERSION = 1

fun today(): Int {
    val now = System.currentTimeMillis()
    return ((now + TimeZone.getDefault().getOffset(now)) / 86_400_000L).toInt()
}

data class Question(
    val id: Int, val src: String, val variant: Int, val idx: Int, val topic: String, val diff: String,
    val verified: Boolean, val text: String, val options: List<String>, val correct: Int,
    val expl: String, val method: String, val mistake: String
)
data class TopicInfo(val name: String, val total: Int, val verified: Int)
data class VariantInfo(val src: String, val variant: Int, val count: Int) {
    val key get() = "$src:$variant"
    val label get() = when (src) { "exam" -> "$variant"; "m2" -> "M${variant - 1000}"; else -> "M${variant - 1100 + 7}" }
}

class BankRepo(ctx: Context) {
    private val db: SQLiteDatabase
    private val cols = "id,src,variant,idx,topic,diff,verified,q,opts,correct,expl,method,mistake"
    private var topicCache: Map<Int, String>? = null

    init {
        val f = File(ctx.filesDir, "bank.db")
        val sp = ctx.getSharedPreferences("bank", Context.MODE_PRIVATE)
        if (!f.exists() || sp.getInt("v", 0) != BANK_VERSION) {
            ctx.assets.open("bank.db").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            sp.edit().putInt("v", BANK_VERSION).apply()
        }
        db = SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    private fun read(c: Cursor): Question {
        val a = JSONArray(c.getString(8))
        return Question(c.getInt(0), c.getString(1), c.getInt(2), c.getInt(3), c.getString(4), c.getString(5) ?: "",
            c.getInt(6) == 1, c.getString(7), List(a.length()) { a.getString(it) }, c.getInt(9),
            c.getString(10) ?: "", c.getString(11) ?: "", c.getString(12) ?: "")
    }
    private fun query(where: String, args: Array<String>? = null, order: String = "id", limit: String? = null): List<Question> {
        val out = ArrayList<Question>()
        db.query("questions", cols.split(",").toTypedArray(), where, args, null, null, order, limit).use { while (it.moveToNext()) out.add(read(it)) }
        return out
    }
    private fun ids(where: String?, args: Array<String>?): List<Int> {
        val out = ArrayList<Int>()
        db.query("questions", arrayOf("id"), where, args, null, null, "id").use { while (it.moveToNext()) out.add(it.getInt(0)) }
        return out
    }
    fun byIds(ids: List<Int>): List<Question> {
        if (ids.isEmpty()) return emptyList()
        val m = query("id IN (${ids.joinToString(",")})").associateBy { it.id }
        return ids.mapNotNull { m[it] }
    }
    fun allIds(verifiedOnly: Boolean) = ids(if (verifiedOnly) "verified=1" else null, null)
    fun topicIds(topic: String, verifiedOnly: Boolean) =
        ids("topic=?" + if (verifiedOnly) " AND verified=1" else "", arrayOf(topic))
    fun variantQs(src: String, variant: Int) = query("src=? AND variant=?", arrayOf(src, variant.toString()), "idx")
    fun search(q: String) = query("q LIKE ?", arrayOf("%$q%"), "id", "60")
    fun topics(): List<TopicInfo> {
        val out = ArrayList<TopicInfo>()
        db.rawQuery("SELECT topic, COUNT(*), SUM(verified) FROM questions GROUP BY topic ORDER BY COUNT(*) DESC", null)
            .use { while (it.moveToNext()) out.add(TopicInfo(it.getString(0), it.getInt(1), it.getInt(2))) }
        return out
    }
    fun variants(): List<VariantInfo> {
        val out = ArrayList<VariantInfo>()
        db.rawQuery("SELECT src, variant, COUNT(*) FROM questions GROUP BY src, variant ORDER BY src, variant", null)
            .use { while (it.moveToNext()) out.add(VariantInfo(it.getString(0), it.getInt(1), it.getInt(2))) }
        return out
    }
    fun topicMap(): Map<Int, String> = topicCache ?: HashMap<Int, String>().also { m ->
        db.rawQuery("SELECT id, topic FROM questions", null).use { while (it.moveToNext()) m[it.getInt(0)] = it.getString(1) }
        topicCache = m
    }
    fun total(): Int = db.rawQuery("SELECT COUNT(*) FROM questions", null).use { it.moveToFirst(); it.getInt(0) }
    fun note(k: String): String = db.rawQuery("SELECT v FROM notes WHERE k=?", arrayOf(k)).use { if (it.moveToFirst()) it.getString(0) else "" }
}

class SrsRow(val box: Int, val due: Int, val seen: Int, val hits: Int)

class ProgressDb(ctx: Context) : SQLiteOpenHelper(ctx, "progress.db", null, 1) {
    private val gaps = intArrayOf(0, 1, 2, 4, 8, 16)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE srs(qid INTEGER PRIMARY KEY, box INTEGER, due INTEGER, seen INTEGER, hits INTEGER)")
        db.execSQL("CREATE TABLE bookmarks(qid INTEGER PRIMARY KEY)")
        db.execSQL("CREATE TABLE daily(day INTEGER PRIMARY KEY, answered INTEGER, correct INTEGER)")
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    fun record(qid: Int, ok: Boolean, day: Int) {
        val db = writableDatabase
        var box = 0; var seen = 0; var hits = 0
        db.rawQuery("SELECT box,seen,hits FROM srs WHERE qid=?", arrayOf(qid.toString())).use {
            if (it.moveToFirst()) { box = it.getInt(0); seen = it.getInt(1); hits = it.getInt(2) }
        }
        val nb = if (ok) min(box + 1, 5) else 0
        db.execSQL("INSERT OR REPLACE INTO srs VALUES(?,?,?,?,?)", arrayOf<Any>(qid, nb, day + gaps[nb], seen + 1, hits + if (ok) 1 else 0))
        db.execSQL("INSERT OR IGNORE INTO daily VALUES(?,0,0)", arrayOf<Any>(day))
        db.execSQL("UPDATE daily SET answered=answered+1, correct=correct+? WHERE day=?", arrayOf<Any>(if (ok) 1 else 0, day))
    }
    fun srsMap(): Map<Int, SrsRow> {
        val m = HashMap<Int, SrsRow>()
        readableDatabase.rawQuery("SELECT qid,box,due,seen,hits FROM srs", null).use {
            while (it.moveToNext()) m[it.getInt(0)] = SrsRow(it.getInt(1), it.getInt(2), it.getInt(3), it.getInt(4))
        }
        return m
    }
    private fun intList(sql: String, args: Array<String>? = null): List<Int> {
        val out = ArrayList<Int>()
        readableDatabase.rawQuery(sql, args).use { while (it.moveToNext()) out.add(it.getInt(0)) }
        return out
    }
    fun dueIds(day: Int) = intList("SELECT qid FROM srs WHERE due<=? ORDER BY box, due", arrayOf(day.toString()))
    fun weakIds() = intList("SELECT qid FROM srs WHERE box=0 AND seen>0 ORDER BY due")
    fun bookmarks() = intList("SELECT qid FROM bookmarks ORDER BY rowid DESC")
    fun isBookmarked(q: Int) = intList("SELECT qid FROM bookmarks WHERE qid=?", arrayOf(q.toString())).isNotEmpty()
    fun toggleBookmark(q: Int) {
        if (isBookmarked(q)) writableDatabase.execSQL("DELETE FROM bookmarks WHERE qid=?", arrayOf<Any>(q))
        else writableDatabase.execSQL("INSERT OR IGNORE INTO bookmarks VALUES(?)", arrayOf<Any>(q))
    }
    fun dailyAnswered(day: Int): Int = intList("SELECT answered FROM daily WHERE day=?", arrayOf(day.toString())).firstOrNull() ?: 0
    fun lastDays(n: Int, today: Int): List<Pair<Int, Int>> {
        val m = HashMap<Int, Int>()
        readableDatabase.rawQuery("SELECT day,answered FROM daily WHERE day>?", arrayOf((today - n).toString())).use {
            while (it.moveToNext()) m[it.getInt(0)] = it.getInt(1)
        }
        return (today - n + 1..today).map { it to (m[it] ?: 0) }
    }
    fun totals(): Pair<Int, Int> = readableDatabase.rawQuery("SELECT COALESCE(SUM(answered),0), COALESCE(SUM(correct),0) FROM daily", null)
        .use { it.moveToFirst(); it.getInt(0) to it.getInt(1) }
    fun reset() { writableDatabase.execSQL("DELETE FROM srs"); writableDatabase.execSQL("DELETE FROM daily") }
}

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    var onboarded by mutableStateOf(sp.getBoolean("onb", false)); private set
    var name by mutableStateOf(sp.getString("name", "") ?: ""); private set
    var goal by mutableIntStateOf(sp.getInt("goal", 10)); private set
    var xp by mutableIntStateOf(sp.getInt("xp", 0)); private set
    var streak by mutableIntStateOf(sp.getInt("streak", 0)); private set
    var lastDay by mutableIntStateOf(sp.getInt("last", -1)); private set
    var bestStreak by mutableIntStateOf(sp.getInt("bestStreak", 0)); private set
    var verifiedOnly by mutableStateOf(sp.getBoolean("ver", false)); private set
    var shuffle by mutableStateOf(sp.getBoolean("shuf", true)); private set

    fun finishOnboarding(n: String, g: Int) {
        name = n; goal = g; onboarded = true
        sp.edit().putString("name", n).putInt("goal", g).putBoolean("onb", true).apply()
    }
    fun setName(v: String) { name = v; sp.edit().putString("name", v).apply() }
    fun setGoal(v: Int) { goal = v; sp.edit().putInt("goal", v).apply() }
    fun setVerified(v: Boolean) { verifiedOnly = v; sp.edit().putBoolean("ver", v).apply() }
    fun setShuffle(v: Boolean) { shuffle = v; sp.edit().putBoolean("shuf", v).apply() }
    fun addXp(n: Int) { xp += n; sp.edit().putInt("xp", xp).apply() }
    fun touchStreak(day: Int) {
        if (lastDay == day) return
        streak = if (lastDay == day - 1) streak + 1 else 1
        lastDay = day; bestStreak = max(bestStreak, streak)
        sp.edit().putInt("streak", streak).putInt("last", lastDay).putInt("bestStreak", bestStreak).apply()
    }
    fun liveStreak(day: Int) = if (lastDay >= day - 1) streak else 0
    fun bestScore(key: String) = sp.getInt("b_$key", -1)
    fun setBestScore(key: String, p: Int) { if (p > bestScore(key)) sp.edit().putInt("b_$key", p).apply() }
    fun resetProgress() {
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("b_") }.forEach { e.remove(it) }
        e.putInt("xp", 0).putInt("streak", 0).putInt("last", -1).putInt("bestStreak", 0).apply()
        xp = 0; streak = 0; lastDay = -1; bestStreak = 0
    }
}

enum class Kind { DAILY, TOPIC, REVIEW, MISTAKES, EXAM, PRACTICE, BOOKMARKS, SINGLE, WRONG }
data class LessonSpec(val kind: Kind, val title: String, val ids: List<Int>, val timerSec: Int = 0,
                      val immediate: Boolean = true, val key: String = "")
data class Summary(val title: String, val kind: Kind, val total: Int, val correct: Int, val xp: Int,
                   val seconds: Int, val wrongIds: List<Int>, val pct: Int)

class Engine(private val app: PrepApp) {
    private fun pool() = app.bank.allIds(app.store.verifiedOnly)

    fun topicAccuracy(): Map<String, Pair<Int, Int>> {
        val tm = app.bank.topicMap(); val m = HashMap<String, Pair<Int, Int>>()
        app.progress.srsMap().forEach { (id, r) ->
            val t = tm[id] ?: return@forEach
            val p = m[t] ?: (0 to 0)
            m[t] = (p.first + r.seen) to (p.second + r.hits)
        }
        return m
    }

    fun daily(n: Int = 10): LessonSpec {
        val pool = pool().toSet(); val srs = app.progress.srsMap(); val tm = app.bank.topicMap()
        val chosen = LinkedHashSet<Int>()
        app.progress.dueIds(today()).filter { it in pool }.shuffled().take(max(2, n / 2)).forEach { chosen.add(it) }
        val acc = topicAccuracy()
        val unseen = pool.filter { it !in srs }.map { id ->
            val a = acc[tm[id]]?.let { if (it.first > 0) it.second.toFloat() / it.first else 0.5f } ?: 0.5f
            val w = (1.6f - a).toDouble()
            id to Random.nextDouble().pow(1.0 / w)
        }.sortedByDescending { it.second }.map { it.first }
        for (id in unseen) { if (chosen.size >= n) break; chosen.add(id) }
        if (chosen.size < n) pool.filter { it !in chosen }.shuffled().sortedBy { srs[it]?.box ?: 0 }
            .take(n - chosen.size).forEach { chosen.add(it) }
        return LessonSpec(Kind.DAILY, "Kunlik mashq", chosen.toList().shuffled())
    }
    fun topic(name: String, n: Int): LessonSpec {
        val ids = app.bank.topicIds(name, app.store.verifiedOnly); val srs = app.progress.srsMap()
        val pick = if (n <= 0) ids.shuffled() else ids.map { id ->
            val r = srs[id]
            val s = when { r == null -> 1.0; r.box == 0 -> 1.2; else -> 0.4 + 0.1 * (5 - r.box) }
            id to s + Random.nextDouble() * 0.8
        }.sortedByDescending { it.second }.map { it.first }.take(n).shuffled()
        return LessonSpec(Kind.TOPIC, name, pick)
    }
    fun review() = LessonSpec(Kind.REVIEW, "Takrorlash", app.progress.dueIds(today()).take(15).shuffled())
    fun mistakes() = LessonSpec(Kind.MISTAKES, "Xatolar ustida ishlash", app.progress.weakIds().take(20).shuffled())
    fun bookmarks() = LessonSpec(Kind.BOOKMARKS, "Belgilangan savollar", app.progress.bookmarks())
    fun variant(v: VariantInfo, timed: Boolean): LessonSpec {
        val ids = app.bank.variantQs(v.src, v.variant).map { it.id }
        return LessonSpec(if (timed) Kind.EXAM else Kind.PRACTICE, "${v.label}-variant", ids,
            if (timed) ids.size * 60 else 0, !timed, v.key)
    }
}

class PrepApp : Application() {
    val bank by lazy { BankRepo(this) }
    val progress by lazy { ProgressDb(this) }
    val store by lazy { Store(this) }
    val engine by lazy { Engine(this) }
}
