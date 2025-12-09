package com.example.buttons_app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.buttons_app.ui.theme.ButtonsappTheme

class MainActivity : ComponentActivity() {

    private val PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!checkPermissions()) {
            requestPermissions()
        }

        setContent {
            ButtonsappTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ControlPanelScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BODY_SENSORS),
            PERMISSION_REQUEST_CODE
        )
    }
}

@Composable
fun ControlPanelScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    val wifiState = if (wifiManager.isWifiEnabled) "On" else "Off"
    val bluetoothState = when (bluetoothAdapter?.state) {
        BluetoothAdapter.STATE_ON -> "On"
        BluetoothAdapter.STATE_OFF -> "Off"
        else -> "Unknown"
    }
    val gpsState = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) "On" else "Off"

    var accelerometerData by remember { mutableStateOf("x: 0.0, y: 0.0, z: 0.0") }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        val x = it.values[0]
                        val y = it.values[1]
                        val z = it.values[2]
                        accelerometerData = "x: %.2f, y: %.2f, z: %.2f".format(x, y, z)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)

        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text("Wi-Fi: $wifiState")
        Text("Bluetooth: $bluetoothState")
        Text("GPS: $gpsState")
        Text("Accelerometer: $accelerometerData")
    }
}
