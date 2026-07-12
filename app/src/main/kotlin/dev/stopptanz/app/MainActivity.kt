package dev.stopptanz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.stopptanz.app.settings.SettingsRepository
import kotlinx.coroutines.launch

private const val KEY_LAUNCH_COUNT = "debug_launch_count"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsRepository(applicationContext)
        setContent {
            StopptanzApp(settings)
        }
    }
}

@Composable
fun StopptanzApp(settings: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val count by settings.intFlow(KEY_LAUNCH_COUNT, 0).collectAsState(initial = 0)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column {
                    Text("Stopptanz")
                    Text("Persisted count: $count")
                    Button(onClick = { scope.launch { settings.setInt(KEY_LAUNCH_COUNT, count + 1) } }) {
                        Text("Increment")
                    }
                }
            }
        }
    }
}
