package com.pinnr.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val background: Color,
    val content: Color,
    val onClick: () -> Unit,
)

/**
 * Row that slides left to reveal [actions] on its right edge. Tapping an action
 * closes the row and runs it. Opening one row closes the previously open one
 * via [openKey]/[onOpen].
 */
@Composable
fun SwipeActionsRow(
    key: Any,
    openKey: Any?,
    onOpen: (Any?) -> Unit,
    actions: List<SwipeAction>,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionWidth = 76.dp
    val revealPx = with(LocalDensity.current) { (actionWidth * actions.size).toPx() }
    val offset = remember(key) { Animatable(0f) }

    // Close when another row opens.
    LaunchedEffect(openKey) { if (openKey != key && offset.value != 0f) offset.animateTo(0f) }

    fun settle(velocity: Float) = scope.launch {
        val open = offset.value < -revealPx / 2 || velocity < -800f
        offset.animateTo(if (open) -revealPx else 0f)
        onOpen(if (open) key else if (openKey == key) null else openKey)
    }

    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp))) {
        Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
            actions.forEach { a ->
                Column(
                    Modifier.width(actionWidth).fillMaxHeight().background(a.background)
                        .clickable {
                            scope.launch { offset.animateTo(0f) }
                            onOpen(null)
                            a.onClick()
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(a.icon, null, tint = a.content)
                    Spacer(Modifier.height(4.dp))
                    Text(a.label, color = a.content, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { d ->
                        scope.launch { offset.snapTo((offset.value + d).coerceIn(-revealPx, 0f)) }
                    },
                    onDragStopped = { v -> settle(v) },
                )
        ) { content() }
    }
}
