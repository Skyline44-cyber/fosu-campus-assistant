package dev.campususage.fosu

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.campususage.fosu.ui.FosuApp
import dev.campususage.fosu.ui.FosuViewModel
import dev.campususage.fosu.ui.theme.FosuTheme

class MainActivity : ComponentActivity() {
    private val vm: FosuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            FosuTheme {
                FosuApp(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}
