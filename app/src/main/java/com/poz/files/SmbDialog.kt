package com.poz.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text

/**
 * Добавить или изменить SMB-сервер. Поля заполняются экранной клавиатурой:
 * встать на поле и нажать OK. Стрелки вверх/вниз — между полями.
 */
@Composable
fun SmbDialog(
    initial: SmbServer?,
    onSave: (SmbServer) -> Unit,
    onDelete: (SmbServer) -> Unit,
    onDismiss: () -> Unit,
) {
    var host by remember { mutableStateOf(initial?.host.orEmpty()) }
    var user by remember { mutableStateOf(initial?.user.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    val first = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(560.dp).clip(Look.PanelShape).background(Look.Dialog)
                .border(1.dp, Look.PanelLine, Look.PanelShape).padding(28.dp)
        ) {
            Text(stringResource(R.string.smb_title), fontSize = 24.sp)

            TvTextField(host, { host = it.trim() }, stringResource(R.string.smb_host),
                placeholder = stringResource(R.string.smb_host_hint), keyboardType = KeyboardType.Uri,
                modifier = Modifier.focusRequester(first))
            TvTextField(user, { user = it }, stringResource(R.string.smb_user))
            TvTextField(password, { password = it }, stringResource(R.string.smb_password), password = true)
            TvTextField(name, { name = it }, stringResource(R.string.smb_name), last = true)

            Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton(stringResource(R.string.save), primary = true, enabled = host.isNotBlank()) {
                    val server = (initial ?: SmbServer(host = host))
                        .copy(host = host.removePrefix("smb://").trimEnd('/'), user = user, password = password, name = name)
                    onSave(server)
                }
                DialogButton(stringResource(R.string.cancel), onClick = onDismiss)
                if (initial != null) {
                    DialogButton(stringResource(R.string.delete)) { onDelete(initial) }
                }
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
}

/** Поле ввода под пульт: подпись сверху, рамка подсвечивается в фокусе. */
@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    last: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Column(Modifier.padding(top = 16.dp)) {
        Text(label, color = Look.TextDim, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Look.Text, fontSize = 18.sp, fontFamily = Inter),
            cursorBrush = SolidColor(Look.Accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
            ),
            // «Далее» на клавиатуре — к следующему полю
            keyboardActions = KeyboardActions(onAny = { focus.moveFocus(FocusDirection.Down) }),
            modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().clip(Look.RowShape)
                        .background(if (focused) Look.RowFocus else Look.Track)
                        .border(2.dp, if (focused) Look.Accent else Color.Transparent, Look.RowShape)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, color = Look.TextDim, fontSize = 18.sp)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun DialogButton(text: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.colors(
            containerColor = if (primary) Look.Accent.copy(alpha = 0.35f) else Look.Track,
            contentColor = Look.Text,
            focusedContainerColor = Look.Accent,
            focusedContentColor = Color.White,
        ),
    ) {
        Text(text, fontSize = 16.sp)
    }
}
