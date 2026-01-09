package com.example.learningandroid

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.core.content.ContextCompat
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.random.Random

// Step 1: Data class to represent each touch point
data class TouchPoint(
    val id: Long,        // Unique identifier for each finger
    val position: Offset // X and Y coordinates
)

// Weather API data classes for Open-Meteo
@Serializable
data class WeatherResponse(
    val latitude: Float,
    val longitude: Float,
    val current_weather: CurrentWeather
)

@Serializable
data class CurrentWeather(
    val temperature: Float,
    val windspeed: Float,
    val weathercode: Int
)

class MainActivity : ComponentActivity() {
    lateinit var fusedLocationClient: FusedLocationProviderClient
    private var hasLocationPermission = false

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Request location permissions
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            LearningAndroidTheme {
                TouchDebugScreen()
            }
        }
    }
}

// HTTP Client for API calls
val httpClient = HttpClient(Android) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
}

// Function to fetch weather using Open-Meteo (free, no API key required)
suspend fun fetchWeather(latitude: Float = 51.5074f, longitude: Float = -0.1278f): WeatherResponse? {
    return try {
        // Open-Meteo API - completely free, no API key required
        httpClient.get("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current_weather=true").body()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// Function to get city name from coordinates using Geocoder
fun getCityName(context: Context, latitude: Double, longitude: Double): String {
    return try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
        if (addresses != null && addresses.isNotEmpty()) {
            val address = addresses[0]
            // Try to get city name, fallback to locality or subadmin area
            address.locality ?: address.subAdminArea ?: address.adminArea ?: "Unknown Location"
        } else {
            "Unknown Location"
        }
    } catch (e: Exception) {
        e.printStackTrace()
        "Location unavailable"
    }
}

// Function to get current location
suspend fun getCurrentLocation(
    context: Context,
    fusedLocationClient: FusedLocationProviderClient
): Pair<Double, Double>? {
    return try {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                continuation.resume(location?.let { Pair(it.latitude, it.longitude) })
            }.addOnFailureListener {
                continuation.resume(null)
            }

            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun TouchDebugScreen() {
    // Get CameraManager from system services
    val context = LocalContext.current
    val activity = context as? Activity
    val mainActivity = activity as? MainActivity
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val scope = rememberCoroutineScope()

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
    var weatherText by remember { mutableStateOf("Loading location...") }
    var cityName by remember { mutableStateOf("") }

    // Fetch current location and weather on start
    LaunchedEffect(Unit) {
        mainActivity?.let { main ->
            // Get current location
            val location = getCurrentLocation(context, main.fusedLocationClient)

            if (location != null) {
                val (lat, lon) = location

                // Get city name from coordinates
                cityName = getCityName(context, lat, lon)

                // Fetch weather for current location
                val weather = fetchWeather(lat.toFloat(), lon.toFloat())
                weatherText = weather?.let {
                    "$cityName: ${it.current_weather.temperature}°C, Wind: ${it.current_weather.windspeed} km/h"
                } ?: "Failed to load weather for $cityName"
            } else {
                weatherText = "Location permission denied or unavailable"
            }
        }
    }

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
            // Weather display
            Text(
                text = weatherText,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

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
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 46.dp),
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