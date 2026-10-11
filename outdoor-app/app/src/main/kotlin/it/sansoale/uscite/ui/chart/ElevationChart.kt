package it.sansoale.uscite.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.sansoale.uscite.track.ElevationProfile
import it.sansoale.uscite.track.ProfilePoint
import it.sansoale.uscite.ui.common.UnitFormatter

/**
 * Profilo altimetrico interattivo: toccando o trascinando sul grafico si seleziona un punto,
 * che viene comunicato con [onSelect] (per evidenziarlo sulla mappa).
 */
@Composable
fun ElevationChart(
    profile: List<ProfilePoint>,
    units: UnitFormatter,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (profile.size < 2) return
    val colors = MaterialTheme.colorScheme
    val lineColor = colors.primary
    val textColor = colors.onSurfaceVariant
    val gridColor = colors.outline.copy(alpha = 0.25f)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = textColor)
    val tooltipStyle = TextStyle(fontSize = 12.sp, color = colors.onPrimary)
    val onSelectState = rememberUpdatedState(onSelect)

    val minEle = remember(profile) { profile.minOf { it.elevationM } }
    val maxEle = remember(profile) { profile.maxOf { it.elevationM } }
    val totalDist = profile.last().distanceM.coerceAtLeast(1.0)
    // Margine verticale per non schiacciare i profili quasi piatti.
    val span = (maxEle - minEle).coerceAtLeast(50.0)
    val yMin = minEle - span * 0.08
    val yMax = maxEle + span * 0.08

    Box(modifier.fillMaxWidth().height(180.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(profile) {
                    val left = 44.dp.toPx()
                    fun select(x: Float) {
                        val w = size.width - left
                        val d = ((x - left) / w).coerceIn(0f, 1f) * totalDist
                        onSelectState.value(ElevationProfile.indexAtDistance(profile, d))
                    }
                    detectTapGestures(onTap = { select(it.x) })
                }
                .pointerInput(profile) {
                    val left = 44.dp.toPx()
                    fun select(x: Float) {
                        val w = size.width - left
                        val d = ((x - left) / w).coerceIn(0f, 1f) * totalDist
                        onSelectState.value(ElevationProfile.indexAtDistance(profile, d))
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { select(it.x) },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            select(change.position.x)
                        },
                    )
                },
        ) {
            val left = 44.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 20.dp.toPx()
            val width = size.width - left
            val height = bottom - top
            fun x(d: Double) = left + (d / totalDist * width).toFloat()
            fun y(e: Double) = top + ((yMax - e) / (yMax - yMin) * height).toFloat()

            // Griglia e etichette delle quote
            val ticks = 4
            for (i in 0..ticks) {
                val e = yMin + (yMax - yMin) * i / ticks
                val yy = y(e)
                drawLine(gridColor, Offset(left, yy), Offset(size.width, yy), strokeWidth = 1f)
                val label = textMeasurer.measure(units.elevation(e), labelStyle)
                drawText(label, topLeft = Offset(left - label.size.width - 4.dp.toPx(), yy - label.size.height / 2))
            }
            // Etichette delle distanze
            listOf(0.0, totalDist / 2, totalDist).forEachIndexed { i, d ->
                val label = textMeasurer.measure(units.shortDistance(d), labelStyle)
                val lx = when (i) {
                    0 -> x(d)
                    2 -> x(d) - label.size.width
                    else -> x(d) - label.size.width / 2
                }
                drawText(label, topLeft = Offset(lx, bottom + 4.dp.toPx()))
            }

            // Area e linea del profilo
            val line = Path()
            profile.forEachIndexed { i, p ->
                if (i == 0) line.moveTo(x(p.distanceM), y(p.elevationM)) else line.lineTo(x(p.distanceM), y(p.elevationM))
            }
            val area = Path().apply {
                addPath(line)
                lineTo(x(profile.last().distanceM), bottom)
                lineTo(x(profile.first().distanceM), bottom)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.45f), lineColor.copy(alpha = 0.05f)), startY = top, endY = bottom))
            drawPath(line, lineColor, style = Stroke(width = 2.5.dp.toPx()))

            // Punto selezionato
            val sel = selectedIndex?.let { profile.getOrNull(it) }
            if (sel != null) {
                val sx = x(sel.distanceM)
                val sy = y(sel.elevationM)
                drawLine(
                    textColor,
                    Offset(sx, top),
                    Offset(sx, bottom),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
                drawCircle(colors.surface, radius = 7.dp.toPx(), center = Offset(sx, sy))
                drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(sx, sy))

                val text = textMeasurer.measure("${units.shortDistance(sel.distanceM)} · ${units.elevation(sel.elevationM)}", tooltipStyle)
                val pad = 6.dp.toPx()
                val boxW = text.size.width + pad * 2
                val boxH = text.size.height + pad
                val bx = (sx - boxW / 2).coerceIn(left, size.width - boxW)
                drawRoundRect(lineColor, topLeft = Offset(bx, top), size = Size(boxW, boxH), cornerRadius = CornerRadius(6.dp.toPx()))
                drawText(text, topLeft = Offset(bx + pad, top + pad / 2))
            }
        }
    }
}
