package uz.mirae.prep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PrepApp
        setContent { AppTheme { Root(app) } }
    }
}

@Composable
fun Root(app: PrepApp) {
    if (!app.store.onboarded) { Onboarding(app.store); return }
    var stack by remember { mutableStateOf<List<Route>>(listOf(Route.Main)) }
    var tab by rememberSaveable { mutableStateOf(0) }
    val nav = remember {
        Nav(push = { stack = stack + it }, pop = { if (stack.size > 1) stack = stack.dropLast(1) },
            replace = { stack = stack.dropLast(1) + it }, home = { stack = listOf(Route.Main) })
    }
    BackHandler(enabled = stack.size > 1 && stack.last() is Route.Search) { nav.pop() }
    AnimatedContent(stack.last(), transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "nav") { r ->
        when (r) {
            Route.Main -> MainScreen(app, tab, { tab = it }, nav)
            is Route.Lesson -> LessonScreen(app, r.spec, onClose = { nav.pop() }, onFinish = { nav.replace(Route.Result(it)) })
            is Route.Result -> ResultScreen(app, r.sum, nav)
            Route.Search -> SearchScreen(app, nav)
        }
    }
}
