package dev.autoscroll.nativeapp.camera

import java.nio.ByteBuffer

/** Direct plane reads preserve planar/interleaved chroma, row padding and buffer offsets. */
internal object YuvFrame {
    class Plane(buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int) {
        private val bytes = buffer.slice()

        fun validate(width: Int, height: Int) {
            require(rowStride > 0 && pixelStride > 0)
            require(rowStride.toLong() >= (width - 1L) * pixelStride + 1)
            require((height - 1L) * rowStride + (width - 1L) * pixelStride < bytes.limit()) {
                "Truncated YUV plane"
            }
        }

        fun at(x: Int, y: Int): Int = bytes.get(y * rowStride + x * pixelStride).toInt() and 0xff
    }

    data class Argb(val pixels: IntArray, val width: Int, val height: Int)

    fun toUprightArgb(
        width: Int,
        height: Int,
        planes: List<Plane>,
        sample: Int = 1,
        rotation: Int = 0,
    ): Argb {
        require(width in 1..1920 && height in 1..1920 && planes.size == 3)
        require(sample > 0 && rotation in listOf(0, 90, 180, 270))
        planes[0].validate(width, height)
        planes[1].validate((width + 1) / 2, (height + 1) / 2)
        planes[2].validate((width + 1) / 2, (height + 1) / 2)
        val sampledWidth = (width + sample - 1) / sample
        val sampledHeight = (height + sample - 1) / sample
        val outputWidth = if (rotation == 90 || rotation == 270) sampledHeight else sampledWidth
        val outputHeight = if (rotation == 90 || rotation == 270) sampledWidth else sampledHeight
        val pixels = IntArray(outputWidth * outputHeight)
        for (outY in 0 until sampledHeight) {
            val y = outY * sample
            for (outX in 0 until sampledWidth) {
                val x = outX * sample
                val luminance = (planes[0].at(x, y) - 16).coerceAtLeast(0)
                val u = planes[1].at(x / 2, y / 2) - 128
                val v = planes[2].at(x / 2, y / 2) - 128
                val r = ((298 * luminance + 409 * v + 128) shr 8).coerceIn(0, 255)
                val g = ((298 * luminance - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
                val b = ((298 * luminance + 516 * u + 128) shr 8).coerceIn(0, 255)
                val index = when (rotation) {
                    90 -> outX * outputWidth + sampledHeight - 1 - outY
                    180 -> (sampledHeight - 1 - outY) * outputWidth + sampledWidth - 1 - outX
                    270 -> (sampledWidth - 1 - outX) * outputWidth + outY
                    else -> outY * outputWidth + outX
                }
                pixels[index] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Argb(pixels, outputWidth, outputHeight)
    }
}
