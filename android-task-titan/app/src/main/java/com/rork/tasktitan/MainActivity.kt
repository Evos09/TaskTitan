package com.rork.tasktitan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.tasktitan.ui.AppViewModel
import com.rork.tasktitan.ui.navigation.AppNavigation
import com.rork.tasktitan.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val data by vm.data.collectAsStateWithLifecycle()
            val s = data.settings
            // Local reminders need the notification permission on Android 13+; ask once
            // after consent, only while gentle reminders are switched on.
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(s.consentGiven, s.reminders) {
                if (s.consentGiven && s.reminders && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            AppTheme(highContrast = s.highContrast, reducedMotion = s.reducedMotion, largeText = s.largeText) {
                AppNavigation(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshDay()
    }
}
