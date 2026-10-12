package eu.kanade.presentation.components

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.min
import androidx.compose.ui.util.lerp
import eu.kanade.presentation.manga.components.MangaCover
import kotlinx.coroutines.delay
import mihon.app.di.appGraph
import tachiyomi.presentation.core.util.collectAsState

/**
 * Plays the book opening animation before handing over to the reader.
 */
@Stable
class BookOpener internal constructor() {
    internal var request by mutableStateOf<Request?>(null)

    internal class Request(val cover: Any?, val title: String?, val intent: Intent)

    fun open(cover: Any?, title: String?, intent: Intent) {
        if (request == null) request = Request(cover, title, intent)
    }
}

/** Null when the animation is turned off. */
val LocalBookOpener = staticCompositionLocalOf<BookOpener?> { null }

/**
 * Starts the reader, through the book opening animation when [opener] is available.
 */
fun Context.openReader(opener: BookOpener?, cover: Any?, title: String?, intent: Intent) {
    if (opener != null) {
        opener.open(cover, title, intent)
    } else {
        startActivity(intent)
    }
}

@Composable
fun BookOpenAnimationHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val enabled by remember { context.appGraph.uiPreferences.readingAnimation }.collectAsState()
    val opener = remember { BookOpener() }
    CompositionLocalProvider(LocalBookOpener provides opener.takeIf { enabled }) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            opener.request?.let { request ->
                BookOpenOverlay(
                    request = request,
                    onOpened = {
                        // The reader fades in over the open page
                        val options = ActivityOptions.makeCustomAnimation(
                            context,
                            android.R.anim.fade_in,
                            android.R.anim.fade_out,
                        )
                        context.startActivity(request.intent, options.toBundle())
                    },
                    onFinished = { opener.request = null },
                )
            }
        }
    }
}

@Composable
private fun BookOpenOverlay(
    request: BookOpener.Request,
    onOpened: () -> Unit,
    onFinished: () -> Unit,
) {
    val appear = remember { Animatable(0f) }
    val open = remember { Animatable(0f) }
    LaunchedEffect(request) {
        appear.animateTo(1f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
        delay(140)
        open.animateTo(1f, tween(durationMillis = 720, easing = FastOutSlowInEasing))
        onOpened()
        // Stay over the app until the reader has covered it
        delay(800)
        onFinished()
    }
    // Nothing to go back to mid animation
    BackHandler {}

    val pageColor = Color(0xFF0E0E0E)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
            .graphicsLayer { alpha = appear.value.coerceAtMost(1f) }
            .background(Color.Black.copy(alpha = 0.55f + 0.45f * open.value)),
    ) {
        val coverWidth = min(maxWidth * 0.7f, maxHeight * 0.7f / 1.5f)
        val coverHeight = coverWidth * 1.5f
        val progress = open.value
        val pageWidth = lerp(coverWidth, maxWidth, progress)
        val pageHeight = lerp(coverHeight, maxHeight, progress)
        val grow = 0.75f + 0.25f * appear.value

        // The first page, growing to fill the screen as the cover opens
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(pageWidth, pageHeight)
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                }
                .clip(RoundedCornerShape(lerp(12.dp, 0.dp, progress)))
                .background(pageColor),
            contentAlignment = Alignment.Center,
        ) {
            if (request.title != null) {
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = (progress * 2f - 0.8f).coerceIn(0f, 0.85f)),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        }

        // The cover, hinged on its left edge so it swings open right to left
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(coverWidth)
                .graphicsLayer {
                    // Scale evenly with the page's width so the hinge stays on its edge without stretching the cover
                    val scale = grow * (pageWidth / coverWidth)
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            MangaCover.Book(
                data = request.cover,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationY = -lerp(0f, 180f, progress)
                    cameraDistance = 18f * density
                    // Once it's past edge-on we'd be looking at its back
                    alpha = if (progress < 0.5f) 1f else 0f
                    shadowElevation = 24f * (1f - progress)
                },
            )
        }
    }
}
