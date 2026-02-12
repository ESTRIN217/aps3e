package aenu.aps3e.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import aenu.aps3e.R

enum class WaveSymbolType {
    SQUARE,
    TRIANGLE,
    CROSS,
    CIRCLE
}

data class WaveSymbol(
    val type: WaveSymbolType,
    val speed: Float,
    val phase: Float
)

data class WaveLine(
    val id: Int,
    val verticalOffset: Float,
    val amplitude: Float,
    val peakCount: Int,
    val thickness: Float,
    val color: Color,
    val symbols: List<WaveSymbol>,
    val phase: Float
)

@Composable
fun WaveBackground() {
    var timeSeconds by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val startTime = System.nanoTime()
        while (true) {
            withFrameNanos { frameTime ->
                timeSeconds = (frameTime - startTime) / 1_000_000_000f
            }
        }
    }

    val squarePainter = painterResource(R.drawable.ic_ps_square)
    val trianglePainter = painterResource(R.drawable.ic_ps_triangle)
    val crossPainter = painterResource(R.drawable.ic_ps_cross)
    val circlePainter = painterResource(R.drawable.ic_ps_circle)
    val symbolPainters = remember {
        mapOf(
            WaveSymbolType.SQUARE to squarePainter,
            WaveSymbolType.TRIANGLE to trianglePainter,
            WaveSymbolType.CROSS to crossPainter,
            WaveSymbolType.CIRCLE to circlePainter
        )
    }

    val waveTime = timeSeconds * ((2f * PI.toFloat()) / 30f)
    val horizontalShift = timeSeconds * ((2f * PI.toFloat()) / 20f)

    val waveLines = remember {
        val lines = mutableListOf<WaveLine>()
        repeat(8) { index ->
            lines.add(createWaveLine(index, lines))
        }
        lines.toList()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val baseY = size.height * 0.8f
        val screenWidth = size.width

        waveLines.forEach { waveLine ->
            val alpha = (0.35f + 0.2f * sin(waveTime + waveLine.phase).toFloat()).coerceIn(0.15f, 0.6f)
            drawWaveLine(
                waveLine = waveLine,
                baseY = baseY,
                screenWidth = screenWidth,
                alpha = alpha,
                horizontalShift = horizontalShift,
                timeSeconds = timeSeconds,
                symbolPainters = symbolPainters
            )
        }
    }
}

private fun createWaveLine(id: Int, existingLines: List<WaveLine> = emptyList()): WaveLine {
    val symbolCounts = mutableMapOf(
        WaveSymbolType.SQUARE to 0,
        WaveSymbolType.TRIANGLE to 0,
        WaveSymbolType.CROSS to 0,
        WaveSymbolType.CIRCLE to 0
    )

    existingLines.forEach { line ->
        line.symbols.forEach { symbol ->
            symbolCounts[symbol.type] = symbolCounts[symbol.type]!! + 1
        }
    }

    val sortedByUsage = symbolCounts.entries.sortedBy { it.value }.map { it.key }
    val symbolCount = Random.nextInt(1, 5)
    val selectedSymbols = mutableListOf<WaveSymbolType>()
    val availableSymbols = sortedByUsage.toMutableList()

    repeat(symbolCount) {
        if (availableSymbols.isEmpty()) return@repeat
        val symbol = if (Random.nextFloat() < 0.7f) {
            val topHalf = availableSymbols.take((availableSymbols.size + 1) / 2)
            topHalf.random()
        } else {
            availableSymbols.random()
        }
        selectedSymbols.add(symbol)
        availableSymbols.remove(symbol)
    }

    val symbols = selectedSymbols.map { symbolType ->
        WaveSymbol(
            type = symbolType,
            speed = Random.nextFloat() * 0.9f + 0.3f,
            phase = Random.nextFloat() * 2f * PI.toFloat()
        )
    }

    return WaveLine(
        id = id,
        verticalOffset = Random.nextFloat() * 40f + 40f,
        amplitude = Random.nextFloat() * 33.33f + 33.33f,
        peakCount = Random.nextInt(2, 5),
        thickness = Random.nextFloat() * 1f + 1.5f,
        color = if (Random.nextBoolean()) Color.White else Color(0xFFE0E0E0),
        symbols = symbols,
        phase = Random.nextFloat() * 2f * PI.toFloat()
    )
}

private fun DrawScope.drawWaveLine(
    waveLine: WaveLine,
    baseY: Float,
    screenWidth: Float,
    alpha: Float,
    horizontalShift: Float,
    timeSeconds: Float,
    symbolPainters: Map<WaveSymbolType, Painter>
) {
    val path = Path()
    val waveY = baseY + waveLine.verticalOffset
    val frequency = waveLine.peakCount * 2 * PI / screenWidth

    for (x in 0..screenWidth.toInt() step 5) {
        val y = waveY + waveLine.amplitude * sin(frequency * x + horizontalShift + waveLine.phase).toFloat()
        if (x == 0) {
            path.moveTo(x.toFloat(), y)
        } else {
            path.lineTo(x.toFloat(), y)
        }
    }

    drawPath(
        path = path,
        color = waveLine.color.copy(alpha = alpha),
        style = Stroke(width = waveLine.thickness, cap = StrokeCap.Round)
    )

    waveLine.symbols.forEach { symbol ->
        val symbolProgress = (timeSeconds * 0.02f * symbol.speed + symbol.phase) % 1f
        val symbolPainter = symbolPainters[symbol.type]
        if (symbolProgress > 0.01f && symbolPainter != null) {
            val symbolX = screenWidth * symbolProgress
            val baseSymbolY = waveY + waveLine.amplitude * sin(frequency * symbolX + horizontalShift + waveLine.phase).toFloat()
            val bobbing = sin(timeSeconds * 2.2f + symbol.phase).toFloat() * 4f
            val symbolY = baseSymbolY - 24f + bobbing
            val symbolSize = 64f
            translate(symbolX - symbolSize / 2f, symbolY - symbolSize / 2f) {
                with(symbolPainter) {
                    draw(size = Size(symbolSize, symbolSize), alpha = alpha * 0.9f)
                }
            }
        }
    }
}
