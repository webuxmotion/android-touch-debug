package com.example.learningandroid

import android.app.Activity
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    // Get CameraManager from system services
    val context = LocalContext.current
    val activity = context as? Activity
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }

    // Get the back camera ID (usually has flash)
    val cameraId = remember {
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    // Check if device supports flashlight brightness control (Android 13+)
    val supportsFlashlightStrength = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            cameraId?.let { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)?.let { it > 1 } ?: false
            } ?: false
        } else false
    }

    // State management
    var touchPoints by remember { mutableStateOf<Map<Long, TouchPoint>>(emptyMap()) }
    var backgroundColor by remember { mutableStateOf(Color.White) }
    var isFlashlightOn by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf(1.0f) } // Screen brightness: 0.0 to 1.0
    var flashlightStrength by remember { mutableStateOf(1) } // Flashlight intensity: 1 to max

    // Clean up flashlight when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            cameraId?.let { id ->
                try {
                    cameraManager.setTorchMode(id, false)
                } catch (e: Exception) {
                    // Ignore errors on cleanup
                }
            }
        }
    }

    // Update brightness when changed
    DisposableEffect(brightness) {
        activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = brightness
        }
        onDispose { }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main canvas with touch detection
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .pointerInput(Unit) {
                    // Detect touch events (multi-touch support)
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

                            // Update state
                            touchPoints = newTouchPoints

                            // Change background color on any touch
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
            // Draw circles for each active touch point
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

        // Control sliders at the top
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Screen brightness slider
            Text(
                text = "Screen: ${(brightness * 100).toInt()}%",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Slider(
                value = brightness,
                onValueChange = { brightness = it },
                valueRange = 0.1f..1.0f,
                modifier = Modifier.fillMaxWidth()
            )

            // Flashlight intensity slider (Android 13+ only)
            if (supportsFlashlightStrength && isFlashlightOn) {
                cameraId?.let { id ->
                    val maxLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        cameraManager.getCameraCharacteristics(id)
                            .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                    } else 1

                    if (maxLevel > 1) {
                        Text(
                            text = "Flashlight: ${flashlightStrength}/${maxLevel}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Slider(
                            value = flashlightStrength.toFloat(),
                            onValueChange = { newValue ->
                                flashlightStrength = newValue.toInt()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    try {
                                        cameraManager.turnOnTorchWithStrengthLevel(id, flashlightStrength)
                                    } catch (e: Exception) {
                                        // Handle error
                                    }
                                }
                            },
                            valueRange = 1f..maxLevel.toFloat(),
                            steps = maxLevel - 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Floating action button for flashlight control
        FloatingActionButton(
            onClick = {
                cameraId?.let { id ->
                    try {
                        isFlashlightOn = !isFlashlightOn
                        if (isFlashlightOn) {
                            // Turn on with brightness control if supported (Android 13+)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && supportsFlashlightStrength) {
                                cameraManager.turnOnTorchWithStrengthLevel(id, flashlightStrength)
                            } else {
                                // Fall back to simple on/off
                                cameraManager.setTorchMode(id, true)
                            }
                        } else {
                            // Turn off
                            cameraManager.setTorchMode(id, false)
                        }
                    } catch (e: Exception) {
                        // Handle error (e.g., show toast)
                        isFlashlightOn = false
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = if (isFlashlightOn) Color.Yellow else MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = "💡",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
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