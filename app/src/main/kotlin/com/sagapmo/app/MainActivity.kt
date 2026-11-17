package com.sagapmo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sagapmo.app.capture.*
import com.sagapmo.app.ui.SagapMoApp
import com.sagapmo.app.ui.SagapMoTheme

class MainActivity : ComponentActivity() {
    private lateinit var model: SagapMoViewModel
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.onPermissionResult()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SagapMoViewModel(
                AndroidSignalReader(applicationContext), AndroidLocationReader(applicationContext),
                (application as SagapMoApplication).database.readings()) as T
        })[SagapMoViewModel::class.java]
        setContent {
            SagapMoTheme { SagapMoApp(model, ::record, ::requestLocation, ::openAppSettings) }
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
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && ::model.isInitialized) model.onAppBackgrounded()
    }
}
