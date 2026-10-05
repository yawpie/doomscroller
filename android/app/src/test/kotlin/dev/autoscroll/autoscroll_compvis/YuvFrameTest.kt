package dev.autoscroll.autoscroll_compvis

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class YuvFrameTest {
    private fun plane(vararg bytes: Int, row: Int, pixel: Int = 1): Map<String, Any> = mapOf(
        "bytes" to bytes.map { it.toByte() }.toByteArray(),
        "rowStride" to row,
        "pixelStride" to pixel,
    )

    @Test fun convertsBlackAndWhiteWithPaddedRows() {
        val result = YuvFrame.toArgb(2, 2, listOf(
            plane(16, 235, 0, 235, 16, row = 3),
            plane(128, row = 1),
            plane(128, row = 1),
        ))
        assertArrayEquals(intArrayOf(0xff000000.toInt(), -1, -1, 0xff000000.toInt()), result)
    }

    @Test fun planarAndInterleavedChromaMatch() {
        val y = plane(81, 81, 145, 145, row = 4)
        val planar = YuvFrame.toArgb(4, 1, listOf(
            y, plane(90, 54, row = 2), plane(240, 34, row = 2),
        ))
        val interleaved = YuvFrame.toArgb(4, 1, listOf(
            y, plane(90, 240, 54, row = 4, pixel = 2),
            plane(240, 90, 34, row = 4, pixel = 2),
        ))
        assertArrayEquals(planar, interleaved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedPlane() {
        YuvFrame.toArgb(2, 2, listOf(
            plane(16, row = 2), plane(128, row = 1), plane(128, row = 1),
        ))
    }

    @Test fun sampledConversionUsesSourceCoordinatesIncludingOddDimensions() {
        val planes = listOf(
            plane(16, 235, 16, 235, 16, 235, row = 3),
            plane(128, 128, row = 2), plane(128, 128, row = 2),
        )
        assertArrayEquals(intArrayOf(0xff000000.toInt(), 0xff000000.toInt()),
            YuvFrame.toArgb(3, 2, planes, sample = 2))
    }
}
