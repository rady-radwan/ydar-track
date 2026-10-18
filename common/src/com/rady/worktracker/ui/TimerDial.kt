package com.rady.worktracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The timer: a thick progress ring around a 60-graduation dial.
 *  - Open timer: one full turn per hour (or per lap), hour laps as dots.
 *  - Countdown: the ring fills toward the goal; [markers] show where the
 *    halfway and 5-minutes-left alerts will sound.
 */
@Composable
fun TimerDial(
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    completedLaps: Int = 0,
    markers: List<Float> = emptyList(),
    center: @Composable BoxScope.() -> Unit,
) {
    val tick = P.rule
    val major = P.inkMuted
    val track = P.slabRaised
    val markerColor = P.alert
    val face = P.slab
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val p = progress.coerceIn(0f, 1f)
            val ringW = r * 0.085f
            val ringR = r - ringW / 2f - r * 0.01f
            val tl = Offset(c.x - ringR, c.y - ringR)
            val sz = Size(ringR * 2, ringR * 2)

            // Ring
            drawArc(track, -90f, 360f, false, tl, sz, style = Stroke(ringW))
            if (p > 0f) drawArc(accent, -90f, 360f * p, false, tl, sz, style = Stroke(ringW, cap = StrokeCap.Round))

            // Graduations inside the ring
            val outer = ringR - ringW * 1.1f
            for (i in 0 until 60) {
                val isMajor = i % 5 == 0
                val len = if (isMajor) r * 0.07f else r * 0.035f
                val a = (i / 60f) * 2 * PI.toFloat() - PI.toFloat() / 2
                val passed = i / 60f <= p
                val col = when {
                    passed && p > 0f -> accent
                    isMajor -> major
                    else -> tick
                }
                drawLine(col, Offset(c.x + cos(a) * outer, c.y + sin(a) * outer),
                    Offset(c.x + cos(a) * (outer - len), c.y + sin(a) * (outer - len)),
                    strokeWidth = if (isMajor) r * 0.018f else r * 0.01f, cap = StrokeCap.Round)
            }

            // Alert markers on the ring
            for (m in markers) {
                val a = m * 2 * PI.toFloat() - PI.toFloat() / 2
                val pos = Offset(c.x + cos(a) * ringR, c.y + sin(a) * ringR)
                drawCircle(face, ringW * 0.62f, pos)
                drawCircle(if (m <= p) accent else markerColor, ringW * 0.42f, pos)
            }

            // Leading knob
            if (p in 0.001f..0.999f) {
                val a = p * 2 * PI.toFloat() - PI.toFloat() / 2
                val pos = Offset(c.x + cos(a) * ringR, c.y + sin(a) * ringR)
                drawCircle(accent, ringW * 0.85f, pos)
                drawCircle(face, ringW * 0.38f, pos)
            }

            // Completed hours as dots
            val laps = completedLaps.coerceAtMost(12)
            if (laps > 0) {
                val dotR = r * 0.022f
                val gap = dotR * 3.2f
                val startX = c.x - (laps - 1) * gap / 2f
                for (i in 0 until laps) drawCircle(accent, dotR, Offset(startX + i * gap, c.y + r * 0.56f))
            }
        }
        center()
    }
}
