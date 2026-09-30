package com.zevbuild.zevsafe

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.zevbuild.zevsafe.ui.screens.HomeScreen
import com.zevbuild.zevsafe.ui.theme.BgDark
import com.zevbuild.zevsafe.ui.theme.ZevSafeTheme
import com.zevbuild.zevsafe.viewmodel.VaultViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingIntent(intent)

        setContent {
            ZevSafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgDark
                ) {
                    HomeScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (uri != null) {
                    val path = uri.toString()
                    if (path.endsWith(".zev", ignoreCase = true)) {
                        viewModel.setDecryptVault(this, uri)
                    } else {
                        viewModel.handleIncomingShareUris(this, listOf(uri))
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                if (!uris.isNullOrEmpty()) {
                    viewModel.handleIncomingShareUris(this, uris)
                }
            }
            Intent.ACTION_VIEW -> {
                val data: Uri? = intent.data
                if (data != null) {
                    viewModel.setDecryptVault(this, data)
                }
            }
            else -> {
                val data: Uri? = intent.data
                if (data != null) {
                    viewModel.setDecryptVault(this, data)
                }
            }
        }
    }
}
