package com.example.learningandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import com.example.learningandroid.ui.theme.LearningAndroidTheme
import kotlin.random.Random

// Step 1: Data class to represent each touch point
data class TouchPoint(
    val id: Long,        // Unique identifier for each finger
    val position: Offset // X and Y coordinates
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LearningAndroidTheme {
                TouchDebugScreen()
            }
        }
    }
}

@Composable
fun TouchDebugScreen() {
    // Step 2: State management - track touch points and background color
    var touchPoints by remember { mutableStateOf<Map<Long, TouchPoint>>(emptyMap()) }
    var backgroundColor by remember { mutableStateOf(Color.White) }

    // Step 3: Main canvas with touch detection
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(Unit) {
                // Step 4: Detect touch events (multi-touch support)
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()

                        // Create a new map with current touches
                        val newTouchPoints = mutableMapOf<Long, TouchPoint>()

                        event.changes.forEach { change ->
                            // Add or update touch point for each active pointer
                            newTouchPoints[change.id.value] = TouchPoint(
                                id = change.id.value,
                                position = change.position
                            )

                            // Mark the change as consumed
                            change.consume()
                        }

                        // Step 5: Update state
                        touchPoints = newTouchPoints

                        // Step 6: Change background color on any touch
                        if (newTouchPoints.isNotEmpty()) {
                            backgroundColor = Color(
                                red = Random.nextFloat(),
                                green = Random.nextFloat(),
                                blue = Random.nextFloat(),
                                alpha = 1f
                            )
                        }
                    }
                }
            }
    ) {
        // Step 7: Draw circles for each active touch point
        touchPoints.values.forEach { touchPoint ->
            // Draw outer circle (semi-transparent)
            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                radius = 100f,
                center = touchPoint.position
            )

            // Draw inner circle (solid)
            drawCircle(
                color = Color.Red,
                radius = 50f,
                center = touchPoint.position
            )

            // Draw center dot
            drawCircle(
                color = Color.White,
                radius = 10f,
                center = touchPoint.position
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TouchDebugPreview() {
    LearningAndroidTheme {
        TouchDebugScreen()
    }
}