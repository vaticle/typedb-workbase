/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.typedb.studio.framework.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.typedb.studio.framework.common.Util.fromDP
import com.typedb.studio.framework.common.Util.toDP
import com.typedb.studio.framework.common.theme.Theme
import com.typedb.studio.framework.material.Form.IconButton
import com.typedb.studio.framework.material.Form.Text
import com.typedb.studio.service.Service
import com.typedb.studio.service.common.NotificationService.Notification
import com.typedb.studio.service.common.NotificationService.Notification.Type.ERROR
import com.typedb.studio.service.common.NotificationService.Notification.Type.INFO
import com.typedb.studio.service.common.NotificationService.Notification.Type.WARNING
import com.typedb.studio.service.common.util.Label
import kotlin.math.roundToInt

object Notifications {

    private val NOTIFICATION_MARGIN = 22.dp
    private val NOTIFICATION_WIDTH = 360.dp
    private val NOTIFICATION_HEIGHT_MIN = 56.dp
    private val MESSAGE_PADDING = 8.dp
    private val MESSAGE_CLOSE_SIZE = 26.dp

    data class ColorArgs(val background: Color, val foreground: Color)

    @Composable
    fun MayShowPopup() {
        if (Service.notification.isOpen) Layout()
    }

    @Composable
    private fun Layout() {
        val scrollState = rememberScrollState()
        val margin = fromDP(NOTIFICATION_MARGIN, LocalDensity.current.density).roundToInt()
        Popup(alignment = Alignment.BottomEnd, offset = IntOffset(0, -margin)) {
            Box {
                Column(Modifier.padding(horizontal = NOTIFICATION_MARGIN).verticalScroll(scrollState)) {
                    Spacer(Modifier.height(NOTIFICATION_MARGIN))
                    if (Service.notification.queue.size > 1) DismissAllButton()
                    Service.notification.queue.forEach { notification ->
                        Notification(notification = notification)
                    }
                    Spacer(Modifier.height(NOTIFICATION_MARGIN))
                }
                Scrollbar.Vertical(rememberScrollbarAdapter(scrollState), Modifier.align(Alignment.CenterEnd))
            }
        }
    }

    @Composable
    private fun DismissAllButton() {
        val colorArgs = colorArgsOf(Service.notification.queue.first().type)
        Row(modifier = Modifier.padding(MESSAGE_PADDING).width(NOTIFICATION_WIDTH)) {
            Spacer(Modifier.weight(1f))
            Form.TextButton(
                text = Label.DISMISS_ALL,
                textColor = colorArgs.foreground,
                bgColor = colorArgs.background,
                trailingIcon = Form.IconArg(Icon.CLOSE) { colorArgs.foreground },
            ) { Service.notification.dismissAll() }
        }
    }

    @Composable
    private fun Notification(notification: Notification) {
        var height by remember { mutableStateOf(NOTIFICATION_HEIGHT_MIN) }
        val colorArgs = colorArgsOf(notification.type)
        val clipboard = LocalClipboardManager.current
        val density = LocalDensity.current.density
        Row(
            modifier = Modifier.padding(MESSAGE_PADDING)
                .width(NOTIFICATION_WIDTH).height(height)
                .background(color = colorArgs.background, shape = Theme.ROUNDED_CORNER_SHAPE)
        ) {
            Text(
                value = notification.message,
                color = colorArgs.foreground,
                overflow = TextOverflow.Visible,
                softWrap = true,
                modifier = Modifier.padding(MESSAGE_PADDING).weight(1f)
            ) {
                val textHeight = toDP(it.multiParagraph.height, density) + MESSAGE_PADDING * 2
                height = textHeight.coerceAtLeast(NOTIFICATION_HEIGHT_MIN)
            }
            Column(Modifier.fillMaxHeight()) {
                Button(Icon.CLOSE, colorArgs) {
                    Service.notification.dismiss(
                        notification
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(Icon.COPY, colorArgs) { clipboard.setText(AnnotatedString(notification.message)) }
            }
        }
    }

    @Composable
    private fun Button(closeIcon: Icon, colorArgs: ColorArgs, onClick: () -> Unit) = IconButton(
        icon = closeIcon,
        modifier = Modifier.size(MESSAGE_CLOSE_SIZE),
        iconColor = colorArgs.foreground,
        bgColor = Color.Transparent,
        onClick = onClick
    )

    @Composable
    private fun colorArgsOf(type: Notification.Type): ColorArgs = when (type) {
        INFO -> ColorArgs(Theme.studio.border, Theme.studio.onSurface)
        WARNING -> ColorArgs(Theme.studio.warningBackground, Theme.studio.onError)
        ERROR -> ColorArgs(Theme.studio.errorBackground, Theme.studio.onError)
    }
}
