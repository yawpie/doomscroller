package dev.autoscroll.nativeapp.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.autoscroll.nativeapp.mediapipe.HandLandmarkerEngine
import dev.autoscroll.nativeapp.mediapipe.InferenceMetrics
import dev.autoscroll.nativeapp.mediapipe.LatencyWindow
import dev.autoscroll.nativeapp.gesture.HandLandmark
import dev.autoscroll.nativeapp.gesture.HandPose
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraPipelineTest {
    private fun plane(vararg bytes: Int, row: Int, pixel: Int = 1, offset: Int = 0): YuvFrame.Plane {
        val buffer = ByteBuffer.wrap(ByteArray(offset) + bytes.map { it.toByte() }.toByteArray())
        buffer.position(offset)
        return YuvFrame.Plane(buffer, row, pixel)
    }

    @Test fun paddedRowsAndBufferOffsetsPreserveBlackAndWhite() {
        val result = YuvFrame.toUprightArgb(2, 2, listOf(
            plane(16, 235, 0, 235, 16, row = 3, offset = 2),
            plane(128, row = 1, offset = 1), plane(128, row = 1),
        ))
        assertArrayEquals(intArrayOf(0xff000000.toInt(), -1, -1, 0xff000000.toInt()), result.pixels)
    }

    @Test fun planarAndInterleavedChromaAreEquivalent() {
        val y = plane(81, 81, 145, 145, row = 4)
        val planar = YuvFrame.toUprightArgb(4, 1, listOf(
            y, plane(90, 54, row = 2), plane(240, 34, row = 2),
        ))
        val interleaved = YuvFrame.toUprightArgb(4, 1, listOf(
            y, plane(90, 240, 54, row = 4, pixel = 2),
            plane(240, 90, 34, row = 4, pixel = 2),
        ))
        assertArrayEquals(planar.pixels, interleaved.pixels)
        assertEquals(0xffff0000.toInt(), planar.pixels[0])
    }

    @Test fun rotatesSensorCoordinatesUprightForEveryQuarterTurn() {
        val planes = listOf(plane(16, 60, 100, 140, 200, 235, row = 3),
            plane(128, 128, row = 2), plane(128, 128, row = 2))
        val original = YuvFrame.toUprightArgb(3, 2, planes).pixels
        val orders = mapOf(0 to listOf(0, 1, 2, 3, 4, 5), 90 to listOf(3, 0, 4, 1, 5, 2),
            180 to listOf(5, 4, 3, 2, 1, 0), 270 to listOf(2, 5, 1, 4, 0, 3))
        for ((rotation, order) in orders) {
            val rotated = YuvFrame.toUprightArgb(3, 2, planes, rotation = rotation)
            assertEquals(if (rotation == 90 || rotation == 270) 2 else 3, rotated.width)
            assertEquals(if (rotation == 90 || rotation == 270) 3 else 2, rotated.height)
            assertArrayEquals(order.map { original[it] }.toIntArray(), rotated.pixels)
        }
    }

    @Test fun oddDimensionsSampleSourcePixelsBeforeRotation() {
        val planes = listOf(plane(16, 235, 16, 235, 16, 235, row = 3),
            plane(128, 128, row = 2), plane(128, 128, row = 2))
        val result = YuvFrame.toUprightArgb(3, 2, planes, sample = 2, rotation = 90)
        assertEquals(1, result.width)
        assertEquals(2, result.height)
        assertArrayEquals(intArrayOf(0xff000000.toInt(), 0xff000000.toInt()), result.pixels)
    }

    @Test(expected = IllegalArgumentException::class)
    fun truncatedPlanesAreRejected() {
        YuvFrame.toUprightArgb(2, 2, listOf(plane(16, row = 2), plane(128, row = 1), plane(128, row = 1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedRotationIsRejected() {
        YuvFrame.toUprightArgb(1, 1, List(3) { plane(128, row = 1) }, rotation = 45)
    }

    @Test fun frameThrottlingNeverRoundsBelowRequestedInterval() {
        assertEquals(66_666_667L, TrackingConfig().frameIntervalNs)
        assertEquals(33_333_334L, TrackingConfig(inferenceFps = 30).frameIntervalNs)
    }

    @Test fun recognitionTimestampUsesInputArrivalAndAgeIncludesInference() {
        val pose = HandPose(List(21) { HandLandmark(it, .5, .5, 0.0) })
        val sample = CameraController.FrameResult(pose,
            InferenceMetrics(inputTimestampMs = 100, completionTimestampMs = 280))
        val fresh = sample.resultAt(nowMs = 599, lostAfterMs = 500)
        assertEquals(100L, fresh.timestampMs)
        assertEquals(pose, fresh.pose)
        val expired = sample.resultAt(nowMs = 600, lostAfterMs = 500)
        assertEquals(100L, expired.timestampMs)
        assertNull(expired.pose)
    }

    @Test fun previewSurvivesInferenceAndExpiresWithItsLandmarks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val source = instrumentationFixture()
        val preview = source.copy(Bitmap.Config.ARGB_8888, false)
        val engine = HandLandmarkerEngine(instrumentation.targetContext, TrackingConfig())
        try {
            val pose = engine.detect(source, 100)
            assertTrue(source.isRecycled)
            assertFalse(preview.isRecycled)
            assertTrue(preview.getPixel(preview.width / 2, preview.height / 2) != 0)
            val sample = CameraController.FrameResult(pose,
                InferenceMetrics(inputTimestampMs = 100, completionTimestampMs = 280), preview)
            assertEquals(preview, sample.previewAt(nowMs = 599, lostAfterMs = 500))
            assertNull(sample.previewAt(nowMs = 600, lostAfterMs = 500))
            assertNull(sample.resultAt(nowMs = 600, lostAfterMs = 500).pose)
        } finally {
            engine.close()
            if (!source.isRecycled) source.recycle()
            preview.recycle()
        }
    }

    @Test fun rollingLatencySummaryIsBoundedAndUsesNearestRankPercentiles() {
        val window = LatencyWindow()
        assertEquals(0, window.summary().samples)
        repeat(130) { window.add(it.toDouble()) }
        val summary = window.summary()
        assertEquals(120, summary.samples)
        assertEquals(69.5, summary.average, 0.0)
        assertEquals(69.0, summary.p50, 0.0)
        assertEquals(123.0, summary.p95, 0.0)
    }

    @Test fun officialFixtureProducesOrderedUprightAndSelfieMirroredLandmarks() {
        val fixture = instrumentationFixture()
        fun pose(dx: Float = 0f, dy: Float = 0f): HandPose {
            val bitmap = Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xffeeeeee.toInt())
            canvas.drawBitmap(fixture, null, RectF(17.5f + dx / 2, 50f + dy / 2,
                142.5f + dx / 2, 183f + dy / 2), Paint())
            // A fresh VIDEO tracker isolates coordinates from temporal ROI tracking.
            val engine = HandLandmarkerEngine(InstrumentationRegistry.getInstrumentation().targetContext, TrackingConfig())
            try {
                val result = checkNotNull(engine.detect(bitmap, 1)) { "Official hand fixture was not detected" }
                assertEquals((0..20).toList(), result.landmarks.map { it.index })
                assertTrue(result.landmarks.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() &&
                    it.x in 0.0..1.0 && it.y in 0.0..1.0 })
                return result
            } finally {
                engine.close()
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        try {
            val center = pose()
            val up = pose(dy = -30f)
            val down = pose(dy = 30f)
            assertTrue(up.palmY < center.palmY - .035)
            assertTrue(down.palmY > center.palmY + .035)
            val left = pose(dx = -25f)
            val right = pose(dx = 25f)
            assertTrue(left.palmX > right.palmX + .04)
        } finally {
            fixture.recycle()
        }
    }

    private fun instrumentationFixture(): Bitmap =
        InstrumentationRegistry.getInstrumentation().context.assets.open("thumb_up.jpg").use {
            checkNotNull(BitmapFactory.decodeStream(it)) { "Cannot decode shared hand fixture" }
        }

    @Test fun packagedModelRunsOnWorkerAndRejectsDuplicateTimestamp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertFalse(Thread.currentThread() === android.os.Looper.getMainLooper().thread)
        val engine = HandLandmarkerEngine(instrumentation.targetContext, TrackingConfig())
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff000000.toInt())
        try {
            assertNull(engine.detect(bitmap, 1))
            try {
                engine.detect(bitmap, 1)
                fail("Duplicate timestamp was accepted")
            } catch (_: IllegalArgumentException) {
                // Expected at the MediaPipe boundary.
            }
            val nextBitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
            nextBitmap.eraseColor(0xff000000.toInt())
            try {
                assertNull(engine.detect(nextBitmap, 2))
            } finally {
                if (!nextBitmap.isRecycled) nextBitmap.recycle()
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
            engine.close()
            engine.close()
        }
    }

    @Test fun closingUnusedControllerTwiceNeedsNoCameraOrPermissions() {
        val closed = CountDownLatch(2)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val controller = CameraController(ApplicationProvider.getApplicationContext(), TrackingConfig(),
                onResult = { fail("Closed controller emitted a result") },
                onMetrics = { fail("Closed controller emitted metrics") },
                onError = { fail("Closed controller emitted an error") })
            controller.close { closed.countDown() }
            controller.close { closed.countDown() }
        }
        assertTrue("Worker disposal did not finish", closed.await(5, TimeUnit.SECONDS))
    }
}
