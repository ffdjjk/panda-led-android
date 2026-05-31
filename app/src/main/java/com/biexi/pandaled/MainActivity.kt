package com.biexi.pandaled

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.biexi.pandaled.ui.theme.PandaLedTheme
import com.biexi.pandaled.ui.navigation.PandaLedNavGraph
import com.biexi.pandaled.util.BillingManager

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Refresh subscription status every time the app comes to foreground,
        // so that external cancellations (e.g. via Google Play) are detected
        // without requiring an app restart.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                BillingManager.querySubscriptionStatus()
            }
        })

        enableEdgeToEdge()
        setContent {
            PandaLedTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PandaLedNavGraph()
                }
            }
        }
    }
}
