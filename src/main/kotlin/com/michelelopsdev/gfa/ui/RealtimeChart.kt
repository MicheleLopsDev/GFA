package com.michelelopsdev.gfa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun RealtimeChart(
    dataPoints: List<Int>,
    modifier: Modifier = Modifier
) {
    val maxValue = dataPoints.maxOrNull()?.coerceAtLeast(10) ?: 10

    Box(modifier = modifier.background(Color(0xFF1E1E1E))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val stepX = if (dataPoints.size > 1) width / (dataPoints.size - 1).toFloat() else width

            val path = Path()

            dataPoints.forEachIndexed { index, value ->
                val x = index * stepX
                val y = height - ((value.toFloat() / maxValue.toFloat()) * height)

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = Color(0xFF00E5FF),
                style = Stroke(width = 4f)
            )
        }
    }
}
