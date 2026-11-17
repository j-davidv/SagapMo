package com.sagapmo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import com.sagapmo.app.capture.AndroidSignalReader
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scope = rememberCoroutineScope()
            var busy by remember { mutableStateOf(false) }
            var result by remember { mutableStateOf("SagapMo?") }
            MaterialTheme {
                Column {
                    Text(result)
                    Button(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try { result = "Captured: ${AndroidSignalReader(applicationContext).read()}" }
                            finally { busy = false }
                        }
                    }) { Text("Record reading") }
                }
            }
        }
    }
}
