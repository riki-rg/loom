package dev.loom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.loom.player.Playback
import dev.loom.theme.LoomTheme
import dev.loom.ui.Root
import dev.loom.ui.LoomViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val vm: LoomViewModel = androidx.lifecycle.ViewModelProvider(this)[LoomViewModel::class.java]
        Playback.attach(this, vm)
        setContent {
            val theme by vm.theme.collectAsState()
            LoomTheme(theme) {
                Surface(Modifier.fillMaxSize()) { Root(vm) }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) Playback.release()
        super.onDestroy()
    }
}
