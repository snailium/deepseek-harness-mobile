package com.labteto.dshmobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.labteto.dshmobile.ui.theme.DsAnimations
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import com.labteto.dshmobile.ui.theme.DshTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Modal dialog: dim overlay (platform scrim ~ overlayMask), r24 bgLayer2 plate
 * with a hairline border. [content] receives a [ColumnScope].
 */
@Composable
fun DsDialog(
    title: String?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DsTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            // 0.92f on a phone, capped so a tablet does not get a full-width plate.
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.92f),
            shape = DsShapes.dialog,
            color = colors.bgLayer2,
            border = BorderStroke(1.dp, colors.borderL1),
            shadowElevation = 8.dp,
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                title?.let {
                    Text(it, style = DsType.large20, color = colors.labelPrimary)
                }
                content()
            }
        }
    }
}

/** What a toast is telling the user; drives its icon and how long it stays. */
enum class ToastTone { Info, Success, Error }

/** A toast with its tone; a new instance replaces the previous toast. */
data class ToastMessage(val text: String, val tone: ToastTone)

/**
 * Toast state pair: the current message ([State]) and a [show] lambda. The
 * message auto-clears after the last [show] call — errors hold a moment longer,
 * because a failure the user did not notice cannot be looked up again.
 */
@Composable
fun rememberDsToast(): Pair<State<ToastMessage?>, (String, ToastTone) -> Unit> {
    val flow = remember { MutableStateFlow<ToastMessage?>(null) }
    val state = flow.collectAsState()
    val message = state.value
    LaunchedEffect(message) {
        if (message != null) {
            delay(if (message.tone == ToastTone.Error) 4500 else 3000)
            flow.value = null
        }
    }
    return state to { text, tone -> flow.value = ToastMessage(text, tone) }
}

/** Top-center toast plate driven by [rememberDsToast]; slides in and out of view. */
@Composable
fun DsToastHost(state: Pair<State<ToastMessage?>, (String, ToastTone) -> Unit>, modifier: Modifier = Modifier) {
    val message = state.first.value
    val toneIcon = when (message?.tone) {
        ToastTone.Success -> FeatherIcons.Check
        ToastTone.Error -> FeatherIcons.AlertTriangle
        else -> FeatherIcons.Info
    }
    val toneColor = when (message?.tone) {
        ToastTone.Success -> DsTheme.colors.successSecondary
        ToastTone.Error -> DsTheme.colors.errorSecondary
        else -> Color.White.copy(alpha = 0.85f)
    }
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(DsAnimations.panelSlide) { -it } + fadeIn(DsAnimations.fade),
        exit = slideOutVertically(DsAnimations.panelSlide) { -it } + fadeOut(DsAnimations.fade),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val shown = message ?: return@Box
            Surface(
                shape = DsShapes.toast,
                color = DsTheme.colors.toastBg,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        toneIcon,
                        contentDescription = null,
                        tint = toneColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        shown.text,
                        style = DsType.small13,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/** One entry in a [DsMenu]. */
data class MenuItem(
    val text: String,
    val icon: ImageVector? = null,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Dropdown menu anchored to [anchor]; r12 bgLayer3 surface with h40 r10 cells,
 * hover fill, and danger rows in error/dangerHover.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DsMenu(anchor: @Composable () -> Unit, items: List<MenuItem>) {
    val colors = DsTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                // The anchor is usually a bare 16-20dp icon; the menu it opens is the only
                // affordance, so the tap target grows to the platform minimum while the glyph
                // keeps its size (centered by the Box).
                .minimumInteractiveComponentSize()
                .clickable(role = Role.Button, onClick = { expanded = true }),
            contentAlignment = Alignment.Center,
        ) { anchor() }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = DsShapes.menu,
            containerColor = colors.bgLayer3,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, colors.borderL1),
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            item.text,
                            style = DsType.std14,
                            color = if (item.danger) colors.error else colors.labelPrimary,
                        )
                    },
                    leadingIcon = item.icon?.let { icon ->
                        {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (item.danger) colors.error else colors.labelSecondary,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                    modifier = Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DsMenuPreview() {
    DshTheme {
        DsMenu(
            anchor = { DsButton("Menu", onClick = {}) },
            items = listOf(
                MenuItem("Open", icon = FeatherIcons.Pencil, onClick = {}),
                MenuItem("Delete", danger = true, onClick = {}),
            ),
        )
    }
}
