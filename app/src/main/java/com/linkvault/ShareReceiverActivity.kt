package com.linkvault

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.linkvault.ui.screens.share.ShareBottomSheetContent
import com.linkvault.ui.theme.LinkVaultTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val url = extractUrl(intent) ?: run {
            finish()
            return
        }

        setContent {
            LinkVaultTheme {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val scope = rememberCoroutineScope()

                fun dismiss() {
                    scope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        finish()
                    }
                }

                Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0f)) {
                    ModalBottomSheet(
                        onDismissRequest = { finish() },
                        sheetState = sheetState,
                        dragHandle = { BottomSheetDefaults.DragHandle() }
                    ) {
                        ShareBottomSheetContent(
                            url = url,
                            onSaved = ::dismiss,
                            modifier = Modifier.navigationBarsPadding()
                        )
                    }
                }
            }
        }
    }

    private fun extractUrl(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND) return null
        if (intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val urlRegex = Regex("https?://[^\\s]+")
        return urlRegex.find(text)?.value
            ?: text.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
}
