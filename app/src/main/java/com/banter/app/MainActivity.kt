package com.banter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.banter.app.ui.BanterRoot
import com.banter.app.ui.theme.BanterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as BanterApp).container
        setContent {
            BanterTheme {
                BanterRoot(container)
            }
        }
    }
}
