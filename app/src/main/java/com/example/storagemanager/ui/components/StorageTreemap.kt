package com.example.storagemanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.storagemanager.util.FormatUtils

data class TreemapItem(
    val label: String,
    val value: Long,
    val color: Color,
)

private data class TreemapRect(
    val label: String,
    val value: Long,
    val color: Color,
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
)

private data class Bounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

@Composable
fun StorageTreemap(
    items: List<TreemapItem>,
    modifier: Modifier = Modifier,
) {
    val visible = items.filter { it.value > 0 }
    if (visible.isEmpty()) return

    val rects = remember(visible) {
        buildTreemap(
            visible.map { TreemapRect(it.label, it.value, it.color) },
            Bounds(0f, 0f, 1f, 1f),
            horizontal = true,
        )
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f),
        ) {
            val w = size.width
            val h = size.height
            val gap = (w + h) * 0.003f
            rects.forEach { rect ->
                val leftPx = rect.left * w + gap
                val topPx = rect.top * h + gap
                val rightPx = rect.right * w - gap
                val bottomPx = rect.bottom * h - gap
                if (rightPx - leftPx > 1f && bottomPx - topPx > 1f) {
                    drawRect(
                        color = rect.color,
                        topLeft = Offset(leftPx, topPx),
                        size = Size(rightPx - leftPx, bottomPx - topPx),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        visible.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(item.color),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = FormatUtils.formatBytes(item.value),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = " ${FormatUtils.formatPercent(item.value.toFloat() / visible.sumOf { it.value }) }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun buildTreemap(
    items: List<TreemapRect>,
    bounds: Bounds,
    horizontal: Boolean,
): List<TreemapRect> {
    if (items.isEmpty()) return emptyList()
    if (items.size == 1) {
        return listOf(
            items[0].copy(
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom,
            )
        )
    }

    val total = items.sumOf { it.value }
    var acc = 0L
    var splitIndex = 1
    for (i in items.indices) {
        acc += items[i].value
        if (acc * 2 >= total) {
            splitIndex = i + 1
            break
        }
    }
    splitIndex = splitIndex.coerceIn(1, items.lastIndex)

    val left = items.take(splitIndex)
    val right = items.drop(splitIndex)
    val leftSum = left.sumOf { it.value }
    val fraction = if (total > 0) leftSum.toFloat() / total else 0.5f

    return if (horizontal) {
        val cut = bounds.left + (bounds.right - bounds.left) * fraction
        buildTreemap(left, Bounds(bounds.left, bounds.top, cut, bounds.bottom), horizontal = false) +
            buildTreemap(right, Bounds(cut, bounds.top, bounds.right, bounds.bottom), horizontal = false)
    } else {
        val cut = bounds.top + (bounds.bottom - bounds.top) * fraction
        buildTreemap(left, Bounds(bounds.left, bounds.top, bounds.right, cut), horizontal = true) +
            buildTreemap(right, Bounds(bounds.left, cut, bounds.right, bounds.bottom), horizontal = true)
    }
}