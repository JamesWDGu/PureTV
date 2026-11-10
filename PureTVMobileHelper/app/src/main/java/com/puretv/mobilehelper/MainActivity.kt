package com.puretv.mobilehelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.puretv.mobilehelper.ui.AppViewModel
import com.puretv.mobilehelper.ui.PureTvApp
import com.puretv.mobilehelper.ui.theme.PureTvTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        AppViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PureTvTheme {
                AppContent()
            }
        }
    }

    @Composable
    private fun AppContent() {
        PureTvApp(viewModel)
    }
}
