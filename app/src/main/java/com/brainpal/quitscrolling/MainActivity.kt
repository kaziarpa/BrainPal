package com.brainpal.quitscrolling

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.brainpal.quitscrolling.di.AppContainer
import com.brainpal.quitscrolling.ui.navigation.BrainPalNavGraph
import com.brainpal.quitscrolling.ui.theme.BrainPalTheme

open class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        val appContainer = (applicationContext as? BrainPalApplication)?.container
            ?: AppContainer(applicationContext)

        setContent {
            val container = remember { appContainer }
            BrainPalTheme {
                BrainPalNavGraph(appContainer = container)
            }
        }
    }
}
