package app.narra.audio

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Cambia la frecuencia de muestreo por interpolación lineal, bloque a bloque. Basta para voz:
 * solo se usa si el motor cambia de frecuencia a mitad de un segmento.
 */
class LinearResampler(fromRate: Int, toRate: Int) {
    private val step = fromRate.toDouble() / toRate

    /** Posición de la próxima muestra de salida, relativa al bloque actual (-1 = última del anterior). */
    private var position = 0.0
    private var previous: Short = 0
    private var buffer = ShortArray(0)

    fun process(input: ShortArray, count: Int, output: (samples: ShortArray, count: Int) -> Unit) {
        if (count <= 0) return
        val capacity = ceil((count + 1) / step).toInt() + 1
        if (buffer.size < capacity) buffer = ShortArray(capacity)

        var produced = 0
        while (position < count - 1) {
            val index = floor(position).toInt()
            val fraction = position - index
            val a = if (index < 0) previous.toDouble() else input[index].toDouble()
            val b = input[index + 1].toDouble()
            buffer[produced++] = (a + (b - a) * fraction).toInt().toShort()
            position += step
        }
        position -= count
        previous = input[count - 1]
        if (produced > 0) output(buffer, produced)
    }
}
