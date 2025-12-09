package com.example.buttons_app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.buttons_app.ui.theme.ButtonsappTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Handle permission results if needed
        }

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
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BODY_SENSORS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BODY_SENSORS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        requestPermissionLauncher.launch(requiredPermissions.toTypedArray())
    }
}

@Composable
fun ControlPanelScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val wifiManager = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    var wifiState by remember { mutableStateOf(wifiManager.isWifiEnabled) }
    var bluetoothState by remember { mutableStateOf(bluetoothAdapter?.isEnabled == true) }
    var gpsState by remember { mutableStateOf(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) }
    var accelerometerData by remember { mutableStateOf("x: 0.0, y: 0.0, z: 0.0") }

    // Refresh states on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                wifiState = wifiManager.isWifiEnabled
                bluetoothState = bluetoothAdapter?.isEnabled == true
                gpsState = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

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
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(sensorEventListener)
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            bluetoothState = true
        }
    }

    val toggleWifi: (Boolean) -> Unit = { enable ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
            context.startActivity(panelIntent)
        } else {
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = enable
            wifiState = enable
        }
    }

    val toggleBluetooth: (Boolean) -> Unit = { enable ->
        if (enable) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(intent)
        } else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                @Suppress("DEPRECATION")
                bluetoothAdapter?.disable()
                bluetoothState = false
            }
        }
    }

    val openLocationSettings = {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        context.startActivity(intent)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        InfoCard(title = "Wi-Fi", value = if (wifiState) "On" else "Off") {
            Button(onClick = { toggleWifi(!wifiState) }) {
                Text(if (wifiState) "Turn Off" else "Turn On")
            }
        }
        InfoCard(title = "Bluetooth", value = if (bluetoothState) "On" else "Off") {
            Button(onClick = { toggleBluetooth(!bluetoothState) }) {
                Text(if (bluetoothState) "Turn Off" else "Turn On")
            }
        }
        InfoCard(title = "GPS", value = if (gpsState) "On" else "Off") {
            Button(onClick = openLocationSettings) {
                Text("Settings")
            }
        }
        InfoCard(title = "Accelerometer", value = accelerometerData)
    }
}

@Composable
fun InfoCard(title: String, value: String, content: @Composable () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(100.dp)
                    .background(MaterialTheme.colorScheme.secondary)
            )
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}
