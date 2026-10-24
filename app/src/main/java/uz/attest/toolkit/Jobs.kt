package uz.attest.toolkit

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.drinkless.tdlib.TdApi
import org.json.JSONArray

private val STOP = listOf("to'xtat", "menyu", "menu", "bosh")
private fun String.isStop() = norm().let { t -> STOP.any { t.contains(it) } }

private suspend fun waitFor(ch: Channel<TdApi.Message>, chat: Long, sec: Int, pred: (TdApi.Message) -> Boolean): TdApi.Message? =
    withTimeoutOrNull(sec * 1000L) {
        var r: TdApi.Message? = null
        while (r == null) { val m = ch.receive(); if (m.chatId == chat && pred(m)) r = m }
        r
    }

class Scraper(private val tg: TgClient, private val db: Db, private val log: (String) -> Unit, private val progress: (String) -> Unit) {
    data class Cfg(val bot: String, val subject: String, val pattern: String, val from: Int, val to: Int, val idleSec: Int, val skipDone: Boolean = true)

    suspend fun run(c: Cfg) = coroutineScope {
        val ch = Channel<TdApi.Message>(Channel.UNLIMITED)
        val feeder = launch(start = CoroutineStart.UNDISPATCHED) { tg.messages.collect { ch.trySend(it) } }
        try {
            val chat = tg.chatId(c.bot)
            for (n in c.from..c.to) {
                if (c.skipDone && db.isDone(c.bot, c.subject, n)) { log("⏭ $n-variant avval yig‘ilgan, o‘tkazildi"); continue }
                progress("Variant $n / ${c.to}"); log("▶ $n-variant boshlandi")
                try { variant(ch, chat, c, n) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { log("⚠ $n-variant: ${e.message}") }
                delay(3000)
            }
            log("✅ Hammasi tugadi")
        } finally { feeder.cancel() }
    }

    private suspend fun variant(ch: Channel<TdApi.Message>, chat: Long, c: Cfg, n: Int) {
        while (ch.tryReceive().isSuccess) { }
        tg.sendText(chat, "/start")
        val subj = c.subject.norm()
        val m1 = waitFor(ch, chat, 20) { m -> m.buttons().any { it.text.norm().contains(subj) } } ?: error("Fan tugmasi topilmadi")
        tg.click(m1) { it.text.norm().contains(subj) }
        val label = c.pattern.replace("{n}", n.toString()).norm()
        val m2 = waitFor(ch, chat, 20) { m -> m.buttons().any { it.text.norm() == label } } ?: error("“$label” tugmasi topilmadi")
        tg.click(m2) { it.text.norm() == label }

        val seen = HashSet<Long>(); var idx = 0; var lastQ = -1L
        while (true) {
            val m = withTimeoutOrNull(c.idleSec * 1000L) { ch.receive() } ?: break
            if (m.chatId != chat) continue
            val content = m.content
            val btns = m.buttons()
            val btnJson = JSONArray(btns.map { it.text }).toString()
            if (content is TdApi.MessagePoll) {
                if (!seen.add(m.id)) continue
                var poll = content.poll
                var q = poll.type as? TdApi.PollTypeQuiz
                var correct = q?.correctOptionId ?: -1
                var expl = q?.explanation?.text ?: ""
                if (correct < 0 && !poll.isClosed) {
                    try { tg.call(TdApi.SetPollAnswer(chat, m.id, intArrayOf(0))) } catch (e: Exception) { log("Javob bo‘lmadi: ${e.message}") }
                    delay(1200)
                    try {
                        val u = tg.call(TdApi.GetMessage(chat, m.id))
                        (u.content as? TdApi.MessagePoll)?.let {
                            poll = it.poll; q = poll.type as? TdApi.PollTypeQuiz
                            correct = q?.correctOptionId ?: -1; expl = q?.explanation?.text ?: ""
                        }
                    } catch (_: Exception) { }
                }
                idx++
                val opts = JSONArray(poll.options.map { it.text.text }).toString()
                lastQ = db.saveQuestion(c.bot, c.subject, n, idx, "poll", poll.question.text, opts, correct, expl)
                db.saveRaw(c.bot, n, m.id, poll.question.text, opts)
                log("  #$idx saqlandi (to‘g‘ri: ${if (correct >= 0) correct + 1 else "?"})")
            } else if (content is TdApi.MessageText) {
                val t = content.text.text
                val tn = t.norm()
                if (tn.contains("ishlanish usuli")) {
                    if (!seen.add(m.id)) continue
                    val clean = t.replace(Regex("^\\W*[Ii]shlanish usuli:?\\s*"), "").trim()
                    db.setExplanation(lastQ, clean); db.saveRaw(c.bot, n, m.id, t, btnJson)
                    log("  izoh saqlandi")
                    if (!tg.click(m) { it.text.norm().contains("tushunarli") }) tg.click(m) { !it.text.isStop() }
                } else if (btns.any { !it.text.isStop() } && t.isNotBlank()) {
                    if (!seen.add(m.id)) continue
                    idx++
                    lastQ = db.saveQuestion(c.bot, c.subject, n, idx, "text", t, btnJson, -1, "")
                    db.saveRaw(c.bot, n, m.id, t, btnJson)
                    log("  #$idx matnli savol saqlandi")
                    tg.click(m) { !it.text.isStop() }
                } else if (seen.add(m.id)) db.saveRaw(c.bot, n, m.id, t, btnJson)
            }
        }
        if (idx > 0) db.markDone(c.bot, c.subject, n, idx)
        log("■ $n-variant: $idx ta savol")
    }
}

class NumberSender(private val tg: TgClient, private val log: (String) -> Unit, private val progress: (String) -> Unit) {
    data class Cfg(val bot: String, val start: Int, val end: Int, val step: Int, val delayMs: Long,
                   val waitReply: Boolean, val replySec: Int, val prefix: String, val suffix: String)

    suspend fun run(c: Cfg) = coroutineScope {
        val ch = Channel<TdApi.Message>(Channel.UNLIMITED)
        val feeder = launch(start = CoroutineStart.UNDISPATCHED) { tg.messages.collect { ch.trySend(it) } }
        try {
            val chat = tg.chatId(c.bot)
            var n = c.start
            val up = c.step > 0
            while (if (up) n <= c.end else n >= c.end) {
                while (ch.tryReceive().isSuccess) { }
                send(chat, "${c.prefix}$n${c.suffix}")
                progress("$n  →  ${c.end}")
                if (c.waitReply) waitFor(ch, chat, c.replySec) { true }
                delay(c.delayMs)
                n += c.step
            }
            log("✅ Tugadi: ${c.start}…${c.end}")
        } finally { feeder.cancel() }
    }

    private suspend fun send(chat: Long, text: String) {
        repeat(5) {
            try { tg.sendText(chat, text); return }
            catch (e: TdError) {
                if (e.code != 429) throw e
                val s = Regex("(\\d+)").find(e.message ?: "")?.value?.toLongOrNull() ?: 5
                log("⏳ Telegram cheklovi: ${s + 1} soniya kutaman"); delay((s + 1) * 1000)
            }
        }
        error("Yuborib bo‘lmadi: $text")
    }
}
