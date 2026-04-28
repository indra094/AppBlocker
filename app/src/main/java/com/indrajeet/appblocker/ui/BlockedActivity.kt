package com.indrajeet.appblocker.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.indrajeet.appblocker.ui.theme.AppBlockerTheme

class BlockedActivity : ComponentActivity() {
    private var reasonText by mutableStateOf("Blocked")
    private var targetText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyIntent(intent)

        setContent {
            AppBlockerTheme {
                BlockedScreen(
                    reason = reasonText,
                    target = targetText,
                    onClose = {
                        startActivity(
                            Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    private fun applyIntent(intent: android.content.Intent?) {
        reasonText = intent?.getStringExtra(EXTRA_REASON).orEmpty().ifBlank { "Blocked" }
        targetText = intent?.getStringExtra(EXTRA_TARGET).orEmpty()
    }

    companion object {
        const val EXTRA_REASON = "reason"
        const val EXTRA_TARGET = "target"
    }
}

@Composable
private fun BlockedScreen(
    reason: String,
    target: String,
    onClose: () -> Unit
) {
    BackHandler(enabled = true) {}

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.errorContainer) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Blocked right now",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$reason: $target",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "This item is inside an active blocking window.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onClose, modifier = Modifier.padding(top = 20.dp)) {
                Text("Go Home")
            }
        }
    }
}
