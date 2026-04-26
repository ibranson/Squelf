package app.squelf.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private const val VISIBLE_DURATION_MS = 1000L
private const val FADE_DURATION_MS = 500
private const val THUMB_MAX_WIDTH_PX = 240
private const val THUMB_MAX_HEIGHT_PX = 320

@Composable
fun ThumbnailPreview(file: File, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(file.absolutePath) {
        alpha.snapTo(1f)
        delay(VISIBLE_DURATION_MS)
        alpha.animateTo(targetValue = 0f, animationSpec = tween(FADE_DURATION_MS))
    }

    val bitmap by produceState<ImageBitmap?>(initialValue = null, file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val sample = maxOf(
                bounds.outWidth / THUMB_MAX_WIDTH_PX,
                bounds.outHeight / THUMB_MAX_HEIGHT_PX
            ).coerceAtLeast(1)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
        }
    }

    Box(
        modifier = modifier
            .alpha(alpha.value)
            .border(2.dp, Color.White)
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
