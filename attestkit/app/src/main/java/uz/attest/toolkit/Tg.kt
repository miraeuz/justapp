package uz.attest.toolkit

import android.app.Application
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class AuthStep {
    object Setup : AuthStep(); object Starting : AuthStep(); object Phone : AuthStep()
    object Code : AuthStep(); object Password : AuthStep(); object Ready : AuthStep()
    data class Other(val name: String) : AuthStep()
}

class TdError(val code: Int, msg: String) : Exception("$code: $msg")

fun String.norm() = lowercase().replace("‘", "'").replace("’", "'").replace("ʻ", "'").replace("`", "'").trim()
fun TdApi.Message.plain(): String = when (val c = content) {
    is TdApi.MessageText -> c.text.text
    is TdApi.MessagePoll -> c.poll.question.text
    else -> ""
}
fun TdApi.Message.buttons(): List<TdApi.InlineKeyboardButton> =
    (replyMarkup as? TdApi.ReplyMarkupInlineKeyboard)?.rows?.flatMap { it.toList() } ?: emptyList()

class TgClient(private val app: Application) {
    val auth = MutableStateFlow<AuthStep>(AuthStep.Setup)
    val error = MutableStateFlow<String?>(null)
    val messages = MutableSharedFlow<TdApi.Message>(extraBufferCapacity = 512)
    @Volatile private var client: Client? = null
    private var apiId = 0
    private var apiHash = ""

    fun start(id: Int, hash: String) {
        apiId = id; apiHash = hash; auth.value = AuthStep.Starting
        client = Client.create({ o -> onUpdate(o) }, null, null)
    }

    private fun onUpdate(o: TdApi.Object) {
        when (o) {
            is TdApi.UpdateAuthorizationState -> onAuth(o.authorizationState)
            is TdApi.UpdateNewMessage -> if (!o.message.isOutgoing) messages.tryEmit(o.message)
            is TdApi.UpdateMessageContent -> refetch(o.chatId, o.messageId)
            is TdApi.UpdateMessageEdited -> refetch(o.chatId, o.messageId)
            else -> {}
        }
    }

    private fun refetch(chat: Long, id: Long) {
        client?.send(TdApi.GetMessage(chat, id)) { r -> if (r is TdApi.Message && !r.isOutgoing) messages.tryEmit(r) }
    }

    private fun onAuth(s: TdApi.AuthorizationState) {
        when (s) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val p = TdApi.SetTdlibParameters()
                p.databaseDirectory = app.filesDir.absolutePath + "/td"
                p.filesDirectory = app.filesDir.absolutePath + "/tdfiles"
                p.databaseEncryptionKey = ByteArray(0)
                p.useFileDatabase = true; p.useChatInfoDatabase = true
                p.useMessageDatabase = true; p.useSecretChats = false
                p.apiId = apiId; p.apiHash = apiHash
                p.systemLanguageCode = "en"; p.deviceModel = Build.MODEL
                p.systemVersion = Build.VERSION.RELEASE; p.applicationVersion = "1.0"
                client?.send(p) { r -> reportError(r) }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> auth.value = AuthStep.Phone
            is TdApi.AuthorizationStateWaitCode -> auth.value = AuthStep.Code
            is TdApi.AuthorizationStateWaitPassword -> auth.value = AuthStep.Password
            is TdApi.AuthorizationStateReady -> auth.value = AuthStep.Ready
            is TdApi.AuthorizationStateClosed -> auth.value = AuthStep.Setup
            else -> auth.value = AuthStep.Other(s.javaClass.simpleName)
        }
    }

    private fun reportError(r: TdApi.Object) { if (r is TdApi.Error) error.value = "${r.code}: ${r.message}" }

    fun sendPhone(p: String) { error.value = null; client?.send(TdApi.SetAuthenticationPhoneNumber(p.trim(), null)) { reportError(it) } }
    fun sendCode(c: String) { error.value = null; client?.send(TdApi.CheckAuthenticationCode(c.trim())) { reportError(it) } }
    fun sendPassword(p: String) { error.value = null; client?.send(TdApi.CheckAuthenticationPassword(p)) { reportError(it) } }
    fun logout() { client?.send(TdApi.LogOut()) { } }

    @Suppress("UNCHECKED_CAST")
    suspend fun <R : TdApi.Object> call(f: TdApi.Function<R>): R = suspendCancellableCoroutine { c ->
        val cl = client ?: run { c.resumeWithException(IllegalStateException("Telegram ulanmagan")); return@suspendCancellableCoroutine }
        cl.send(f) { r ->
            if (r is TdApi.Error) c.resumeWithException(TdError(r.code, r.message)) else c.resume(r as R)
        }
    }

    suspend fun chatId(username: String): Long = call(TdApi.SearchPublicChat(username.trim().removePrefix("@"))).id

    suspend fun sendText(chat: Long, text: String) {
        val content = TdApi.InputMessageText(TdApi.FormattedText(text, emptyArray()), null, false)
        call(TdApi.SendMessage(chat, null, null, null, null, content))
    }

    /** Presses the first callback button accepted by [pred]. Returns false if no such button. */
    suspend fun click(m: TdApi.Message, pred: (TdApi.InlineKeyboardButton) -> Boolean): Boolean {
        val b = m.buttons().firstOrNull { it.type is TdApi.InlineKeyboardButtonTypeCallback && pred(it) } ?: return false
        val data = (b.type as TdApi.InlineKeyboardButtonTypeCallback).data
        try { call(TdApi.GetCallbackQueryAnswer(m.chatId, m.id, TdApi.CallbackQueryPayloadData(data))) } catch (_: Exception) { }
        return true
    }
}
