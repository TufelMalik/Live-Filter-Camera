package com.techquantum.livefiltercamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.techquantum.livefiltercamera.ui.CameraScreen
import com.techquantum.livefiltercamera.ui.theme.LiveFilterCameraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveFilterCameraTheme {
                CameraScreen()
            }
        }
    }
}