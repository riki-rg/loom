package dev.loom

import android.content.Intent
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
    private lateinit var vm: LoomViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm = androidx.lifecycle.ViewModelProvider(this)[LoomViewModel::class.java]
        Playback.attach(this, vm)
        setContent {
            val theme by vm.theme.collectAsState()
            LoomTheme(theme) {
                Surface(Modifier.fillMaxSize()) { Root(vm) }
            }
        }
        // Cold start from the notification: the track is already restored, just open its sheet.
        openPlayerIfAsked(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openPlayerIfAsked(intent)
    }

    private fun openPlayerIfAsked(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_PLAYER -> vm.openPlayer()
            // The floating island opens lyrics, not the full player.
            ACTION_OPEN_LYRICS -> vm.openLyrics()
        }
    }

    override fun onDestroy() {
        if (isFinishing) Playback.release()
        super.onDestroy()
    }

    companion object {
        /** Set on the notification's content intent; the buttons keep their own media commands. */
        const val ACTION_OPEN_PLAYER = "dev.loom.OPEN_PLAYER"
        const val ACTION_OPEN_LYRICS = "dev.loom.OPEN_LYRICS"
    }
}
