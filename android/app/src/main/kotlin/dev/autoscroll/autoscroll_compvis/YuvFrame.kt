package dev.autoscroll.autoscroll_compvis

// Pure conversion code; respects both planar and interleaved chroma with row padding.
internal object YuvFrame {
    private class Plane(data: Map<String, Any>, width: Int, height: Int) {
        val bytes = data["bytes"] as? ByteArray ?: error("Missing plane bytes")
        val row = (data["rowStride"] as? Number)?.toInt() ?: error("Missing row stride")
        val pixel = (data["pixelStride"] as? Number)?.toInt() ?: error("Missing pixel stride")

        init {
            require(row > 0 && pixel > 0 && row >= (width - 1) * pixel + 1)
            val lastIndex = (height - 1).toLong() * row + (width - 1).toLong() * pixel
            require(lastIndex < bytes.size) { "Truncated YUV plane" }
        }

        fun at(x: Int, y: Int): Int = bytes[y * row + x * pixel].toInt() and 0xff
    }

    fun toArgb(width: Int, height: Int, data: List<Map<String, Any>>, sample: Int = 1): IntArray {
        require(width > 0 && height > 0 && data.size == 3) { "Expected YUV420 with 3 planes" }
        require(sample > 0)
        val yPlane = Plane(data[0], width, height)
        val uPlane = Plane(data[1], (width + 1) / 2, (height + 1) / 2)
        val vPlane = Plane(data[2], (width + 1) / 2, (height + 1) / 2)
        val outputWidth = (width + sample - 1) / sample
        val outputHeight = (height + sample - 1) / sample
        val pixels = IntArray(outputWidth * outputHeight)
        for (outY in 0 until outputHeight) {
            val y = outY * sample
            for (outX in 0 until outputWidth) {
                val x = outX * sample
                val luminance = (yPlane.at(x, y) - 16).coerceAtLeast(0)
                val u = uPlane.at(x / 2, y / 2) - 128
                val v = vPlane.at(x / 2, y / 2) - 128
                val r = ((298 * luminance + 409 * v + 128) shr 8).coerceIn(0, 255)
                val g = ((298 * luminance - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
                val b = ((298 * luminance + 516 * u + 128) shr 8).coerceIn(0, 255)
                pixels[outY * outputWidth + outX] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return pixels
    }
}
