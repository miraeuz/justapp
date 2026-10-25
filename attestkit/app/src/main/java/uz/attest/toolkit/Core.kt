package uz.attest.toolkit

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("p", Context.MODE_PRIVATE)
    fun get(k: String, d: String = "") = sp.getString(k, d) ?: d
    fun set(k: String, v: String) = sp.edit().putString(k, v).apply()
}

data class QRow(val id: Long, val variant: Int, val idx: Int, val kind: String, val question: String,
                val options: List<String>, val correct: Int, val explanation: String)

class Db(private val ctx: Context) : SQLiteOpenHelper(ctx, "questions.db", null, 2) {
    override fun onConfigure(db: SQLiteDatabase) { db.disableWriteAheadLogging() }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE questions(id INTEGER PRIMARY KEY AUTOINCREMENT, bot TEXT, subject TEXT, variant INTEGER,
            idx INTEGER, kind TEXT, question TEXT, options TEXT, correct INTEGER, explanation TEXT, created INTEGER,
            UNIQUE(bot, subject, variant, question))""")
        db.execSQL("""CREATE TABLE raw_messages(id INTEGER PRIMARY KEY AUTOINCREMENT, bot TEXT, variant INTEGER,
            msg_id INTEGER, text TEXT, buttons TEXT, created INTEGER)""")
        createDone(db)
    }
    private fun createDone(db: SQLiteDatabase) = db.execSQL("""CREATE TABLE IF NOT EXISTS variants_done(
        bot TEXT, subject TEXT, variant INTEGER, total INTEGER, created INTEGER, PRIMARY KEY(bot, subject, variant))""")
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) { if (o < 2) createDone(db) }
    fun markDone(bot: String, subject: String, variant: Int, total: Int) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO variants_done VALUES(?,?,?,?,?)", arrayOf<Any>(bot, subject, variant, total, System.currentTimeMillis()))
    }
    fun isDone(bot: String, subject: String, variant: Int): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM variants_done WHERE bot=? AND subject=? AND variant=?", arrayOf(bot, subject, variant.toString())).use { it.moveToFirst() }

    fun saveQuestion(bot: String, subject: String, variant: Int, idx: Int, kind: String, q: String,
                     options: String, correct: Int, expl: String): Long {
        val v = ContentValues().apply {
            put("bot", bot); put("subject", subject); put("variant", variant); put("idx", idx); put("kind", kind)
            put("question", q); put("options", options); put("correct", correct); put("explanation", expl)
            put("created", System.currentTimeMillis())
        }
        return writableDatabase.insertWithOnConflict("questions", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun setExplanation(id: Long, text: String) {
        if (id < 0) return
        writableDatabase.execSQL("UPDATE questions SET explanation=? WHERE id=?", arrayOf<Any>(text, id))
    }
    fun saveRaw(bot: String, variant: Int, msgId: Long, text: String, buttons: String) {
        val v = ContentValues().apply {
            put("bot", bot); put("variant", variant); put("msg_id", msgId); put("text", text)
            put("buttons", buttons); put("created", System.currentTimeMillis())
        }
        writableDatabase.insert("raw_messages", null, v)
    }
    fun counts(): Pair<Int, Int> {
        val q = readableDatabase.rawQuery("SELECT COUNT(*), COUNT(DISTINCT variant) FROM questions", null)
        q.use { it.moveToFirst(); return it.getInt(0) to it.getInt(1) }
    }
    fun latest(limit: Int = 30): List<QRow> {
        val out = ArrayList<QRow>()
        readableDatabase.rawQuery("SELECT id,variant,idx,kind,question,options,correct,explanation FROM questions ORDER BY id DESC LIMIT $limit", null).use {
            while (it.moveToNext()) {
                val arr = JSONArray(it.getString(5) ?: "[]")
                out.add(QRow(it.getLong(0), it.getInt(1), it.getInt(2), it.getString(3), it.getString(4),
                    List(arr.length()) { i -> arr.getString(i) }, it.getInt(6), it.getString(7) ?: ""))
            }
        }
        return out
    }
    private fun exportDir() = File(ctx.cacheDir, "exports").apply { mkdirs() }
    fun exportDb(): File {
        writableDatabase.rawQuery("SELECT 1", null).close()
        return File(exportDir(), "questions.db").also { ctx.getDatabasePath("questions.db").copyTo(it, overwrite = true) }
    }
    fun exportJson(): File {
        val arr = JSONArray()
        readableDatabase.rawQuery("SELECT bot,subject,variant,idx,kind,question,options,correct,explanation FROM questions ORDER BY variant, idx", null).use {
            while (it.moveToNext()) arr.put(JSONObject().apply {
                put("bot", it.getString(0)); put("subject", it.getString(1)); put("variant", it.getInt(2))
                put("idx", it.getInt(3)); put("kind", it.getString(4)); put("question", it.getString(5))
                put("options", JSONArray(it.getString(6) ?: "[]")); put("correct_index", it.getInt(7))
                put("explanation", it.getString(8) ?: "")
            })
        }
        return File(exportDir(), "questions.json").also { it.writeText(arr.toString(2)) }
    }
}

class KeepAlive : Service() {
    private var wl: android.os.PowerManager.WakeLock? = null
    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() { wl?.let { if (it.isHeld) it.release() }; super.onDestroy() }
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("job", "Ish jarayoni", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "job").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Attest Kit ishlayapti").setContentText(i?.getStringExtra("t") ?: "").build()
        startForeground(1, n)
        if (wl == null) wl = getSystemService(android.os.PowerManager::class.java)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "attest:job").apply { acquire(6 * 60 * 60 * 1000L) }
        return START_NOT_STICKY
    }
}

class Runner(private val app: App) {
    val logs = MutableStateFlow(listOf<String>())
    val busy = MutableStateFlow(false)
    val progress = MutableStateFlow("")
    private var job: Job? = null
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun log(s: String) = logs.update { (it + "${fmt.format(Date())}  $s").takeLast(300) }

    fun launch(title: String, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; progress.value = ""
        try { app.startForegroundService(Intent(app, KeepAlive::class.java).putExtra("t", title)) }
        catch (e: Exception) { log("⚠ Fon rejimi yoqilmadi (ilovani ochiq qoldiring): ${e.message}") }
        job = app.scope.launch {
            try { block() }
            catch (e: CancellationException) { log("⏹ To‘xtatildi") }
            catch (e: Exception) { log("❌ Xato: ${e.message}") }
            finally { busy.value = false; app.stopService(Intent(app, KeepAlive::class.java)) }
        }
    }
    fun stop() { job?.cancel() }
}

class App : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val tg by lazy { TgClient(this) }
    val db by lazy { Db(this) }
    val prefs by lazy { Prefs(this) }
    val runner by lazy { Runner(this) }
}
