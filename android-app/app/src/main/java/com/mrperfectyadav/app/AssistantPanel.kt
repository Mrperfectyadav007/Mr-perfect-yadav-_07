package com.mrperfectyadav.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private data class AiChatMessage(val role: String, val text: String)

@Composable
fun AssistantDialog(onDismiss: () -> Unit) {
    var apiKey by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(AiChatMessage("model", "नमस्ते! मैं MR. PERFECT YADAV AI Assistant हूँ। पढ़ाई, UPSC/BPSC और सामान्य सवाल पूछिए।"))
    }
    val scope = rememberCoroutineScope()
    val red = Color(0xFFFF3030)
    val dark = Color(0xFF181818)
    val white = Color(0xFFF7F7F7)
    val muted = Color(0xFFB8B8B8)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("MR. PERFECT YADAV AI", color = red, fontWeight = FontWeight.Black)
                Text("Online Assistant • Gemini", color = muted, style = MaterialTheme.typography.labelSmall)
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("अपनी API Key यहाँ डालें") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Text(
                    "Key इस चैट में न भेजें। यह ऐप में सेव नहीं होती; ऐप बंद होने पर फिर डालनी होगी। Free tier की सीमाएँ लागू हैं।",
                    color = muted,
                    style = MaterialTheme.typography.labelSmall
                )
                HorizontalDivider(color = dark)
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement =
                            if (message.role == "user") Arrangement.End else Arrangement.Start) {
                            Surface(
                                color = if (message.role == "user") Color(0xFF4A1111) else dark,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Text(
                                    message.text,
                                    color = white,
                                    modifier = Modifier.padding(11.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    if (loading) item { CircularProgressIndicator(color = red, modifier = Modifier.size(22.dp)) }
                }
                if (error.isNotBlank()) Text(error, color = red, style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("अपना सवाल लिखें…") },
                    maxLines = 3
                )
                Button(
                    onClick = {
                        val question = prompt.trim()
                        val key = apiKey.trim()
                        if (key.isBlank()) {
                            error = "पहले Gemini API Key डालें।"
                        } else if (question.isNotBlank() && !loading) {
                            messages.add(AiChatMessage("user", question))
                            prompt = ""
                            error = ""
                            loading = true
                            scope.launch {
                                try {
                                    val answer = withContext(Dispatchers.IO) {
                                        requestGeminiAnswer(key, messages.dropLast(0).takeLast(12))
                                    }
                                    messages.add(AiChatMessage("model", answer))
                                } catch (e: Exception) {
                                    error = e.message ?: "जवाब नहीं मिल पाया। API Key और इंटरनेट जाँचें।"
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    },
                    enabled = !loading && prompt.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = red)
                ) {
                    Text(if (loading) "जवाब आ रहा है…" else "Send")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("बंद करें", color = red) } },
        containerColor = Color(0xFF101010),
        titleContentColor = white,
        textContentColor = white
    )
}

private suspend fun requestGeminiAnswer(apiKey: String, history: List<AiChatMessage>): String {
    val contents = JSONArray()
    history.forEach { message ->
        contents.put(
            JSONObject()
                .put("role", message.role)
                .put("parts", JSONArray().put(JSONObject().put("text", message.text)))
        )
    }
    val body = JSONObject()
        .put(
            "systemInstruction",
            JSONObject().put(
                "parts",
                JSONArray().put(
                    JSONObject().put(
                        "text",
                        "You are MR. PERFECT YADAV AI Assistant. Reply naturally in the user's language, especially Hindi. Help with general questions, education, UPSC and BPSC preparation. Be accurate, explain clearly, and admit uncertainty."
                    )
                )
            )
        )
        .put("contents", contents)

    val connection = (URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent").openConnection() as HttpURLConnection)
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 45000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("x-goog-api-key", apiKey)
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream.bufferedReader().use { it.readText() }
        val response = JSONObject(responseText)
        if (status !in 200..299) {
            val detail = response.optJSONObject("error")?.optString("message")
            throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "Gemini API error ($status)")
        }
        return response.getJSONArray("candidates")
            .getJSONObject(0).getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0).getString("text")
    } finally {
        connection.disconnect()
    }
}
