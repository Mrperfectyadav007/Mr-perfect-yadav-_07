package com.mrperfectyadav.app

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun WebPlayerScreen(modifier: Modifier = Modifier) {
    var address by remember { mutableStateOf("") }
    var loadedAddress by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("Host the Telegram Web Player first, then paste its HTTPS Web App address ending in /room.") }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("TELEGRAM WEB PLAYER", color = Color(0xFF20D67B), fontSize = 19.sp)
        Text(
            "This page opens your own hosted Web Player inside MR. PERFECT YADAV. The GitHub source alone is not a live music server.",
            color = Color(0xFFB8C5D3), fontSize = 12.sp
        )
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Hosted HTTPS address") },
            placeholder = { Text("https://your-domain.com/room") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val candidate = address.trim()
                val valid = runCatching {
                    val uri = android.net.Uri.parse(candidate)
                    uri.scheme == "https" && !uri.host.isNullOrBlank()
                }.getOrDefault(false)
                if (!valid) {
                    message = "Enter a valid HTTPS address for your deployed Web Player."
                } else {
                    loadedAddress = candidate
                    message = "Connecting to your Web Player…"
                    webView?.apply { tag = candidate; loadUrl(candidate) }
                }
            }) {
                Icon(Icons.Default.Language, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text(" CONNECT")
            }
            TextButton(onClick = {
                webView?.reload()
                if (loadedAddress == null) message = "Enter your hosted HTTPS address first."
            }) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Text("RELOAD")
            }
        }
        Text(message, color = Color(0xFFB8C5D3), fontSize = 11.sp)
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            message = "Web Player page loaded. Sign in with Telegram if prompted."
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                            error: android.webkit.WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                message = "Could not load the Web Player. Check the server URL and hosting status."
                            }
                        }
                    }
                    webView = this
                    loadedAddress?.let { tag = it; loadUrl(it) }
                }
            },
            update = { view ->
                webView = view
                if (loadedAddress != null && view.tag != loadedAddress) {
                    view.tag = loadedAddress
                    view.loadUrl(loadedAddress!!)
                }
            }
        )
    }
}
