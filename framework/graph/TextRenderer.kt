/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.typedb.studio.framework.graph

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import com.typedb.studio.framework.common.theme.Theme
import com.typedb.studio.framework.common.theme.Typography
import com.typedb.studio.framework.graph.TextRenderer.LineBreak.Reason.BLOCK_START
import com.typedb.studio.framework.graph.TextRenderer.LineBreak.Reason.OVERFLOW
import com.typedb.studio.framework.graph.TextRenderer.LineBreak.Reason.WHITESPACE
import com.typedb.studio.framework.graph.TextRenderer.LineBreak.Reason.WORD_BREAK
import com.typedb.studio.framework.material.Form
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.skia.Font
import org.jetbrains.skia.TextLine

internal class TextRenderer(private val viewport: Viewport) {

    private val _edgeLabelSizes = ConcurrentHashMap<String, DpSize>()
    val edgeLabelSizes: Map<String, DpSize> get() = _edgeLabelSizes

    fun drawSingleLine(drawScope: DrawScope, text: String, center: Offset, color: Color, typography: Typography.Theme) {
        val textLine = TextLine.make(
            text, Font(typography.fixedWidthSkiaTypeface, typography.codeSizeMedium * viewport.density)
        )
        drawScope.drawIntoCanvas {
            it.nativeCanvas.drawTextLine(
                textLine,
                center.x - textLine.width / 2,
                center.y + textLine.capHeight / 2,
                Paint().apply { this.color = color }.asFrameworkPaint()
            )
        }
    }

    fun DrawScope.drawVertexLabel(vertex: Vertex, color: Color, typography: Typography.Theme) {
        drawIntoCanvas {
            drawMultiLine(
                canvas = it,
                text = vertex.label,
                font = Font(typography.fixedWidthSkiaTypeface, typography.codeSizeMedium * density),
                center = with(viewport) { vertex.geometry.position.toViewport() },
                maxWidth = vertex.geometry.labelMaxWidth * density,
                maxLines = vertex.geometry.labelMaxLines(typography.codeSizeMedium),
                color = color
            ).also { r ->
                if (r.isTruncated && vertex.geometry.isVisiblyCollapsed)
                    vertex.geometry.contentOverflowsBaseShape = true
            }
        }
    }

    private fun drawMultiLine(
        canvas: Canvas,
        text: String,
        font: Font,
        center: Offset,
        maxWidth: Float,
        maxLines: Int,
        color: Color
    ): DrawMultiLineResult {
        var fullyDrawnWithoutTruncation = false
        val lines = mutableListOf<TextLine>()
        var remainingText = text.trim()
        while (lines.size < maxLines) {
            val remainingTextLine = TextLine.make(remainingText, font)
            val lineBreak = findLineBreak(remainingTextLine, remainingText, maxWidth)
            if (lineBreak == null) {
                lines += remainingTextLine
                fullyDrawnWithoutTruncation = true
                break
            } else {
                val breakIndex = lineBreak.index - (if (lineBreak.reason == BLOCK_START) 1 else 0)
                var lineText = remainingText.substring(0 until breakIndex)
                var textLine: TextLine
                if (lines.size == maxLines - 1) {
                    textLine = TextLine.make("$lineText…", font)
                    while (textLine.width > maxWidth) {
                        lineText = lineText.substring(0, lineText.lastIndex)
                        textLine = TextLine.make("$lineText…", font)
                    }
                } else {
                    textLine = TextLine.make(remainingText.substring(0 until breakIndex), font)
                }
                lines += textLine
                remainingText = remainingText.substring(breakIndex).trim()
            }
        }
        drawTextLines(canvas, lines, center, Paint().apply { this.color = color }.asFrameworkPaint())
        return DrawMultiLineResult(isTruncated = !fullyDrawnWithoutTruncation)
    }

    data class DrawMultiLineResult(val isTruncated: Boolean)

    private fun drawTextLines(canvas: Canvas, lines: List<TextLine>, center: Offset, paint: org.jetbrains.skia.Paint) {
        lines.forEachIndexed { index, line ->
            val yOffset = line.height * (0.5f * -(lines.size - 1) + index)
            canvas.nativeCanvas.drawTextLine(
                line, center.x - line.width / 2, center.y + line.capHeight / 2 + yOffset, paint
            )
        }
    }

    private fun findLineBreak(textLine: TextLine, text: String, lineMaxWidth: Float): LineBreak? {
        if (textLine.width < lineMaxWidth) return null
        val xPositions = textLine.positions.filterIndexed { idx, _ -> idx % 2 == 0 }
        val lineOverflowIndex = xPositions.indexOfFirst { it > lineMaxWidth }
            .let { if (it == -1) xPositions.lastIndex else (it - 1).coerceAtLeast(0) }
        var lineEndIndex = lineOverflowIndex
        while (lineEndIndex > 0) {
            val char = text[lineEndIndex]
            if (char.isWhitespace()) return LineBreak(lineEndIndex, WHITESPACE)
            if (char.isWordBreakSymbol() && lineEndIndex < lineOverflowIndex) { // don't allow line to overflow
                return LineBreak(lineEndIndex + 1, WORD_BREAK)
            }
            if (char.isBlockStartSymbol()) return LineBreak(lineEndIndex, BLOCK_START)
            lineEndIndex--
        }
        return LineBreak(index = lineOverflowIndex, OVERFLOW)
    }

    private data class LineBreak(val index: Int, val reason: Reason) {
        enum class Reason { WHITESPACE, WORD_BREAK, BLOCK_START, OVERFLOW }
    }

    private fun Char.isWordBreakSymbol() = this in "-/|"

    private fun Char.isBlockStartSymbol() = this in "({["

    // TODO: get these metrics via drawSingleLine instead of a Composable?
    @Composable
    fun EdgeLabelMeasurer(edge: Edge) {
        with(LocalDensity.current) {
            Form.Text(
                value = edge.label, textStyle = Theme.typography.code1,
                modifier = Modifier.graphicsLayer(alpha = 0f).onSizeChanged {
                    _edgeLabelSizes[edge.label] = DpSize(it.width.toDp(), it.height.toDp())
                }
            )
        }
    }
}
