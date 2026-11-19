package com.poz.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * Строка, которая подсвечивается в фокусе: пункт меню слева и файл в списке.
 * selected — выбранный накопитель: лёгкая синяя подложка, даже без фокуса.
 */
@Composable
fun FocusRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(Look.RowShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Look.RowSelected else Color.Transparent,
            contentColor = Look.Text,
            focusedContainerColor = Look.RowFocus,
            focusedContentColor = Look.Text,
            pressedContainerColor = Look.RowFocus,
            pressedContentColor = Look.Text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Значок + заголовок + подпись (и, если нужно, полоска заполнения) — пункт меню слева. */
@Composable
fun SourceRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    usage: Usage? = null,
    selected: Boolean = false,
    iconTint: Color = Look.Text,
    onLongClick: (() -> Unit)? = null,
) {
    FocusRow(onClick = onClick, modifier = modifier, onLongClick = onLongClick, selected = selected) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp), tint = iconTint)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (usage != null) {
                UsageBar(usage, Modifier.padding(top = 6.dp))
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    modifier = Modifier.padding(top = 4.dp),
                    color = Look.TextDim,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Выбранный накопитель отмечаем синей чёрточкой справа
        if (selected) {
            Box(
                Modifier.padding(start = 8.dp).width(4.dp).height(22.dp)
                    .clip(Look.BarShape).background(Look.Accent)
            )
        }
    }
}

/** Полоска «занято из всего». Почти заполнено (>90%) — красная. */
@Composable
fun UsageBar(usage: Usage, modifier: Modifier = Modifier) {
    val fraction = usage.fraction.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(6.dp).clip(Look.BarShape).background(Look.Track)) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(Look.BarShape)
                .background(if (fraction > 0.9f) Look.Danger else Look.Accent)
        )
    }
}

/** Заголовок раздела в меню: «НАКОПИТЕЛИ», «СЕТЬ». */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 14.dp, top = 18.dp, bottom = 6.dp),
        color = Look.TextDim,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
    )
}
