package com.poz.files

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * Цвета, формы и фоны PozFiles — в том же стиле, что и PozLauncher.
 * Цвет пишется как 0xAARRGGBB: AA — прозрачность.
 */
object Look {
    val Text = Color(0xFFF0F2F5)
    val TextDim = Color(0x99F0F2F5)
    val Accent = Color(0xFF4DA3FF)          // синий: выбранное, полоски заполнения
    val Danger = Color(0xFFFF7A6B)          // почти заполненная память

    val Panel = Color(0x1FFFFFFF)           // стеклянная панель слева
    val PanelLine = Color(0x33FFFFFF)
    val RowFocus = Color(0x33FFFFFF)        // строка в фокусе
    val RowSelected = Color(0x1A4DA3FF)     // выбранный накопитель
    val Track = Color(0x26FFFFFF)           // пустая часть полоски
    val Dialog = Color(0xF0161B26)

    // Фон: тёмно-синий, к правому верхнему углу — бирюзовый отсвет
    val ScreenBackground = Brush.linearGradient(
        listOf(Color(0xFF0F2A3A), Color(0xFF0E1526), Color(0xFF090C14))
    )

    // Цвета значков файлов по типу
    val Folder = Color(0xFFFFC857)
    val Video = Color(0xFFFF6B6B)
    val Audio = Color(0xFFB794F6)
    val Image = Color(0xFF4FD1C5)
    val Apk = Color(0xFF7BD389)
    val Other = Color(0xFFA0AEC0)

    val PanelShape = RoundedCornerShape(24.dp)
    val RowShape = RoundedCornerShape(14.dp)
    val BarShape = RoundedCornerShape(4.dp)
}

/** Шрифт Inter (лицензия OFL — в assets/licenses), переменный: один файл на все начертания. */
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight) = Font(
    R.font.inter,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Inter = FontFamily(inter(FontWeight.Normal), inter(FontWeight.Medium), inter(FontWeight.SemiBold))

@Composable
fun PozTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Look.Accent, onPrimary = Color.White)) {
        CompositionLocalProvider(
            LocalContentColor provides Look.Text,
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = Inter),
            content = content,
        )
    }
}
