package com.example.storagemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.storagemanager.ui.components.AdaptiveScaffold
import com.example.storagemanager.ui.components.PermissionGate
import com.example.storagemanager.ui.navigation.AppNavHost
import com.example.storagemanager.ui.theme.StorageManagerTheme
import dagger.hilt.android.AndroidEntryPoint
// edited by Alpine now i have to exit vim 
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StorageManagerTheme {
                PermissionGate {
                    val navController = rememberNavController()
                    AdaptiveScaffold(navController = navController) { modifier ->
                        AppNavHost(
                            navController = navController,
                            modifier = modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
