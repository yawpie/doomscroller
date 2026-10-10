package dev.autoscroll.nativeapp.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.autoscroll.nativeapp.practice.PracticeState
import dev.autoscroll.nativeapp.session.GestureSessionState

@Composable
internal fun PracticeCameraPreview(state: PracticeState, showHandTracking: Boolean, modifier: Modifier) {
    Box(modifier.clip(MaterialTheme.shapes.medium).background(Color.Black).semantics {
        contentDescription = "Practice camera preview"
    }, contentAlignment = Alignment.Center) {
        val bitmap = state.preview
        if (bitmap == null) {
            Text(if (state.session == GestureSessionState.Active || state.session == GestureSessionState.Starting)
                "Waiting for a fresh camera frame" else "Start practice to see the camera",
                color = Color.White, modifier = Modifier.padding(12.dp))
        } else {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Image(image, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = -1f })
            if (showHandTracking && state.landmarks.isNotEmpty()) Canvas(Modifier.fillMaxSize().semantics {
                contentDescription = "Detected hand landmarks"
            }) {
                val scale = minOf(size.width / bitmap.width, size.height / bitmap.height)
                val width = bitmap.width * scale
                val height = bitmap.height * scale
                val left = (size.width - width) / 2
                val top = (size.height - height) / 2
                // Domain landmarks already use mirrored selfie coordinates.
                val points = state.landmarks.map { Offset(left + it.x.toFloat() * width, top + it.y.toFloat() * height) }
                for (base in listOf(1, 5, 9, 13, 17)) {
                    drawLine(Color.Green, points[0], points[base], 2.dp.toPx())
                    for (index in base until base + 3)
                        drawLine(Color.Green, points[index], points[index + 1], 2.dp.toPx())
                }
                for (index in listOf(5, 9, 13)) drawLine(Color.Green, points[index], points[index + 4], 2.dp.toPx())
                points.forEach { drawCircle(Color.White, 3.dp.toPx(), it) }
            }
        }
    }
}
