package com.sagapmo.app

import android.Manifest
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sagapmo.app.capture.*
import com.sagapmo.app.ui.SagapMoApp
import com.sagapmo.app.ui.SagapMoTheme

class MainActivity : ComponentActivity() {
    private lateinit var model: SagapMoViewModel
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.onPermissionResult()
    }
    private val locationSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        model.onLocationSettingsResult()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getPreferences(MODE_PRIVATE)
        val use3d = prefs.getBoolean("use_3d_map", false)
        val includeSpeed = prefs.getBoolean("include_speed_test", true)
        val alwaysCollapse = prefs.getBoolean("always_collapse_advanced_info", true)
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SagapMoViewModel(
                AndroidSignalReader(applicationContext), AndroidLocationReader(applicationContext),
                (application as SagapMoApplication).database.readings(),
                initialUse3dMap = use3d,
                initialIncludeSpeedTest = includeSpeed,
                initialAlwaysCollapseAdvancedInfo = alwaysCollapse,
                internetAvailability = observeInternetAvailability(applicationContext),
                onSavePreference = { key, value -> prefs.edit().putBoolean(key, value).apply() }
            ) as T
        })[SagapMoViewModel::class.java]
        setContent {
            SagapMoTheme { SagapMoApp(model, ::record, ::requestLocation, ::openAppSettings,
                onOpenLocationSettings = ::openLocationSettings) }
        }
    }
    private fun record() {
        val asked = getPreferences(MODE_PRIVATE).getBoolean("location_requested", false)
        val precise = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        model.record(!asked && !precise)
    }
    private fun requestLocation() {
        if (!model.claimPermissionLaunch()) return
        getPreferences(MODE_PRIVATE).edit().putBoolean("location_requested", true).apply()
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }
    private fun openLocationSettings() {
        if (!model.claimLocationSettingsLaunch()) return
        try {
            locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            model.onLocationSettingsResult()
            android.widget.Toast.makeText(this, "Open Android Settings to turn on Location.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && ::model.isInitialized) model.onAppBackgrounded()
    }
}
