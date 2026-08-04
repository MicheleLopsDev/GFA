package com.michelelopsdev.gfa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalTextApi::class)
@Composable
fun RealtimeChart(
    dataPoints: List<Int>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    
    // Calcolo del valore massimo per scalare l'asse Y (arrotondato ai multipli di 10)
    val actualMax = dataPoints.maxOrNull()?.coerceAtLeast(10) ?: 10
    val maxValue = ((actualMax / 10) + 1) * 10
    
    // Colori Neon
    val lineColor = Color(0xFF00E5FF)
    val gradientColors = listOf(lineColor.copy(alpha = 0.5f), Color.Transparent)
    val gridColor = Color.LightGray.copy(alpha = 0.2f)
    val textColor = Color.LightGray

    Box(modifier = modifier.background(Color(0xFF1E1E1E)).padding(16.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val paddingX = 40.dp.toPx() // Spazio per le etichette Y
            val paddingY = 20.dp.toPx() // Spazio per le etichette X
            
            val chartWidth = width - paddingX
            val chartHeight = height - paddingY

            // 1. Disegna l'asse Y e le linee di griglia
            val stepsY = 5
            for (i in 0..stepsY) {
                val value = (maxValue * i) / stepsY
                val y = chartHeight - (chartHeight * i / stepsY)
                
                // Griglia orizzontale
                drawLine(
                    color = gridColor,
                    start = Offset(paddingX, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
                
                // Testo Asse Y
                val textLayoutResult = textMeasurer.measure(
                    text = value.toString(),
                    style = TextStyle(color = textColor, fontSize = 12.sp)
                )
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(0f, y - (textLayoutResult.size.height / 2))
                )
            }

            // 2. Disegna Asse X (Testo per i secondi recenti)
            val stepX = if (dataPoints.size > 1) chartWidth / (dataPoints.size - 1).toFloat() else chartWidth
            if (dataPoints.isNotEmpty()) {
                val startSec = maxOf(0, dataPoints.size - 50)
                val endSec = dataPoints.size
                
                val textStart = textMeasurer.measure("$startSec s", style = TextStyle(color = textColor, fontSize = 12.sp))
                val textEnd = textMeasurer.measure("$endSec s", style = TextStyle(color = textColor, fontSize = 12.sp))
                
                drawText(textStart, topLeft = Offset(paddingX, height - textStart.size.height))
                drawText(textEnd, topLeft = Offset(width - textEnd.size.width, height - textEnd.size.height))
            }

            // 3. Disegna il grafico
            if (dataPoints.isNotEmpty()) {
                val path = Path()
                val fillPath = Path()
                
                fillPath.moveTo(paddingX, chartHeight) // Punto in basso a sinistra per il gradiente

                dataPoints.forEachIndexed { index, value ->
                    val x = paddingX + (index * stepX)
                    val y = chartHeight - ((value.toFloat() / maxValue.toFloat()) * chartHeight)

                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                // Chiude il path di riempimento in basso a destra
                val lastX = paddingX + ((dataPoints.size - 1) * stepX)
                fillPath.lineTo(lastX, chartHeight)
                fillPath.close()

                // Disegna il gradiente sotto la linea
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = gradientColors,
                        startY = 0f,
                        endY = chartHeight
                    ),
                    style = Fill
                )

                // Disegna la linea brillante del grafico
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 4f)
                )
            }
        }
    }
}
