package com.example.buttons_app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    @Suppress("DEPRECATION")
    var wifiSpeed by remember { mutableStateOf(if (wifiManager.isWifiEnabled) wifiManager.connectionInfo.linkSpeed else 0) }
    var gpsCoordinates by remember { mutableStateOf("Getting location...") }

    // This effect manages all the listeners and state updates.
    DisposableEffect(lifecycleOwner, context, bluetoothAdapter) {
        // Sensor listener for accelerometer
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

        // BroadcastReceiver for Bluetooth state changes
        val bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    bluetoothState = state == BluetoothAdapter.STATE_ON
                }
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothReceiver, filter)
        
        // Location listener for GPS coordinates
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                gpsCoordinates = "Lat: %.4f, Lon: %.4f".format(location.latitude, location.longitude)
            }
            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 10f, locationListener)
        }

        // Lifecycle observer to refresh states on resume
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                wifiState = wifiManager.isWifiEnabled
                @Suppress("DEPRECATION")
                wifiSpeed = if (wifiState) wifiManager.connectionInfo.linkSpeed else 0
                bluetoothState = bluetoothAdapter?.isEnabled == true
                gpsState = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        // Cleanup listeners on dispose
        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
            context.unregisterReceiver(bluetoothReceiver)
            locationManager.removeUpdates(locationListener)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
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

    val openBluetoothSettings = {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        context.startActivity(intent)
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
        InfoCard(
            title = "Wi-Fi",
            buttonContent = {
                Button(onClick = { toggleWifi(!wifiState) }) {
                    Text(if (wifiState) "Turn Off" else "Turn On")
                }
            }
        ) {
            Text("Status: ${if (wifiState) "On" else "Off"}", fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
            if (wifiState) {
                Text("Speed: $wifiSpeed Mbps", fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        InfoCard(
            title = "Bluetooth",
            buttonContent = {
                Button(onClick = openBluetoothSettings) {
                    Text("Settings")
                }
            }
        ) {
            Text(if (bluetoothState) "On" else "Off", fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
        }
        InfoCard(
            title = "GPS",
            buttonContent = {
                Button(onClick = openLocationSettings) {
                    Text("Settings")
                }
            }
        ) {
            Text("Status: ${if (gpsState) "On" else "Off"}", fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
            if (gpsState) {
                Text(gpsCoordinates, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        InfoCard(title = "Accelerometer") {
            Text(accelerometerData, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
fun InfoCard(title: String, buttonContent: @Composable () -> Unit = {}, valueContent: @Composable ColumnScope.() -> Unit) {
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
                valueContent()
            }
            Box(modifier = Modifier.padding(16.dp)) {
                buttonContent()
            }
        }
    }
}
