package com.poz.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Просмотр фото на весь экран. ←/→ — предыдущее и следующее фото в папке,
 * Назад — закрыть. Картинку уменьшаем при загрузке до размеров экрана:
 * фото с телефона 4000×3000 иначе заняло бы 48 МБ оперативной памяти.
 */
@Composable
fun ImageViewer(source: Source, images: List<Entry>, start: Int, onClose: () -> Unit) {
    var index by remember { mutableIntStateOf(start) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    BackHandler(onBack = onClose)

    LaunchedEffect(index) {
        error = null
        bitmap = try {
            loadImage(source, images[index])
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            null
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> { if (index < images.lastIndex) index++; true }
                    Key.DirectionLeft -> { if (index > 0) index--; true }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = images[index].name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        when {
            error != null -> Text(stringResource(R.string.error_open, error!!), color = Look.TextDim, fontSize = 18.sp)
            bitmap == null -> Text(stringResource(R.string.loading), color = Look.TextDim, fontSize = 18.sp)
        }
        // Внизу: имя и номер фото
        Text(
            "${images[index].name}   ${index + 1} / ${images.size}",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            color = Look.TextDim,
            fontSize = 15.sp,
        )
    }
}

private const val MAX_IMAGE_BYTES = 40 * 1024 * 1024

/** Загружаем и сразу уменьшаем до ~1920×1080. */
private suspend fun loadImage(source: Source, entry: Entry): Bitmap = withContext(Dispatchers.IO) {
    val bytes: ByteArray? = when (source) {
        is Source.Local -> null // с диска читаем напрямую, без копии в памяти
        is Source.Smb -> Smb.readBytes(source.server, entry.location, MAX_IMAGE_BYTES)
        is Source.Dlna -> URL(entry.location).openStream().use { it.readBytes() }
    }
    fun decode(options: BitmapFactory.Options): Bitmap? =
        if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        else BitmapFactory.decodeFile(entry.location, options)

    // Сначала только размеры, потом — во сколько раз уменьшить
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decode(bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 1920 || bounds.outHeight / (sample * 2) >= 1080) sample *= 2
    decode(BitmapFactory.Options().apply { inSampleSize = sample })
        ?: throw IllegalStateException("не удалось прочитать картинку")
}
