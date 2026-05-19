package com.garuna.iahybridcoach.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

/* CLAUDE CODE:
 * Card con titulo + grafico de linea para series temporales (FC, velocidad,
 * etc.). El eje X se interpreta siempre como minutos desde el inicio del
 * entreno; el eje Y depende del campo que pasemos.
 *
 * Para entrenos muy largos (3600+ puntos), downsampleamos a 600 puntos
 * maximos para que el grafico no se ralentice. Se promedia por bucket.
 */
@Composable
fun WorkoutLineChart(
    title: String,
    timeOffsetsMillis: List<Long>,
    values: List<Double>,
    unit: String,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty() || timeOffsetsMillis.size != values.size) return

    // Downsample si la serie es demasiado densa.
    val (xs, ys) = remember(timeOffsetsMillis, values) {
        downsample(timeOffsetsMillis, values, maxPoints = 600)
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(xs, ys) {
        modelProducer.runTransaction {
            lineSeries { series(x = xs, y = ys) }
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Tiempo (min) vs $unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            // CLAUDE CODE: formateador del eje X: x viene en minutos, lo
            // mostramos como "Nmin" para que se entienda como duracion.
            val xFormatter = remember {
                CartesianValueFormatter { _, x, _ -> "${x.toInt()}min" }
            }
            // CLAUDE CODE: scroll y zoom desactivados para que TODO el entreno
            // entre en el ancho visible del Card. Sin esto Vico hace el chart
            // mucho mas ancho que la pantalla y el usuario debe hacer swipe.
            // CLAUDE CODE: scroll deshabilitado y zoom inicial fijo a
            // Zoom.Content para forzar a Vico a meter TODOS los datos en el
            // ancho disponible. Esto es lo que en Vico 2.x sustituye al viejo
            // HorizontalLayout.FullWidth.
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = xFormatter
                    )
                ),
                modelProducer = modelProducer,
                scrollState = rememberVicoScrollState(scrollEnabled = false),
                zoomState = rememberVicoZoomState(
                    zoomEnabled = false,
                    initialZoom = Zoom.Content
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
        }
    }
}

/* CLAUDE CODE: reduce la serie a `maxPoints` agrupando en buckets y
 * promediando. Mantiene tendencia general sin saturar Vico. */
private fun downsample(
    timesMs: List<Long>,
    vals: List<Double>,
    maxPoints: Int
): Pair<List<Double>, List<Double>> {
    val n = vals.size
    if (n <= maxPoints) {
        val xs = timesMs.map { it / 60000.0 } // ms -> minutos
        return xs to vals
    }
    val bucketSize = (n + maxPoints - 1) / maxPoints
    val outX = mutableListOf<Double>()
    val outY = mutableListOf<Double>()
    var i = 0
    while (i < n) {
        val end = (i + bucketSize).coerceAtMost(n)
        var sumX = 0.0
        var sumY = 0.0
        var count = 0
        for (j in i until end) {
            sumX += timesMs[j] / 60000.0
            sumY += vals[j]
            count++
        }
        outX += sumX / count
        outY += sumY / count
        i = end
    }
    return outX to outY
}
