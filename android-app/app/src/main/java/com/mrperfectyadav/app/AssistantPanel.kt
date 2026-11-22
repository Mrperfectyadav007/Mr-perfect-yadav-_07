package com.mrperfectyadav.app

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

private data class AiChatMessage(val role: String, val text: String)

@Composable
fun AssistantDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(loadSavedGeminiKey(context)) }
    var prompt by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var keyStatus by remember { mutableStateOf(if (apiKey.isNotBlank()) "सेव की हुई Key लोड हो गई।" else "") }
    var showKeySettings by remember { mutableStateOf(apiKey.isBlank()) }
    var showKeyText by remember { mutableStateOf(false) }
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showKeySettings = !showKeySettings }, modifier = Modifier.weight(1f)) {
                        Text(if (showKeySettings) "API Settings बंद करें" else "Gemini API Settings")
                    }
                    OutlinedButton(onClick = {
                        messages.clear()
                        messages.add(AiChatMessage("model", "नई बातचीत शुरू हुई। मैं MR. PERFECT YADAV AI हूँ।"))
                        error = ""
                    }) { Text("New Chat") }
                }
                if (showKeySettings) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; keyStatus = "" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("नई Gemini API Key यहाँ डालें") },
                        singleLine = true,
                        visualTransformation = if (showKeyText) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { showKeyText = !showKeyText }) { Text(if (showKeyText) "Hide" else "Show") } }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            try {
                                saveGeminiKey(context, apiKey.trim())
                                keyStatus = "API Key इस डिवाइस में सुरक्षित रूप से सेव हो गई।"
                                error = ""
                                showKeySettings = false
                            } catch (e: Exception) {
                                error = "Key सेव नहीं हुई: ${e.message ?: "कृपया दोबारा कोशिश करें"}"
                            }
                        }, enabled = apiKey.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Key Save करें") }
                        TextButton(onClick = {
                            clearSavedGeminiKey(context)
                            apiKey = ""
                            keyStatus = "सेव की हुई Key हटा दी गई।"
                        }, modifier = Modifier.weight(1f)) { Text("Key हटाएँ", color = red) }
                    }
                    Text("Key इस डिवाइस के Android Keystore से एन्क्रिप्ट होकर सेव होती है। इसे चैट या GitHub पर साझा न करें।", color = muted, style = MaterialTheme.typography.labelSmall)
                }
                if (keyStatus.isNotBlank()) {
                    Text(keyStatus, color = Color(0xFF8DDC9B), style = MaterialTheme.typography.labelSmall)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(listOf("UPSC/BPSC समझाओ", "आज का क्विज", "पढ़ाई का प्लान", "इस टॉपिक के नोट्स")) { suggestion ->
                        AssistSuggestion(suggestion) {
                            prompt = when (suggestion) {
                                "UPSC/BPSC समझाओ" -> "इस विषय को UPSC/BPSC स्तर पर सरल हिंदी में समझाइए।"
                                "आज का क्विज" -> "मेरे लिए 5 UPSC/BPSC स्तर के हिंदी MCQ बनाइए।"
                                "पढ़ाई का प्लान" -> "मेरे लिए एक प्रभावी दैनिक पढ़ाई का प्लान बनाइए।"
                                else -> "इस विषय के परीक्षा-उपयोगी हिंदी नोट्स बनाइए।"
                            }
                        }
                    }
                }
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
                            error = "पहले Gemini API Key डालें और Save करें।"
                        } else if (question.isNotBlank() && !loading) {
                            messages.add(AiChatMessage("user", question))
                            prompt = ""
                            error = ""
                            loading = true
                            scope.launch {
                                try {
                                    val answer = withContext(Dispatchers.IO) {
                                        requestGeminiAnswer(key, messages.takeLast(12))
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

private const val GEMINI_PREFS = "mr_perfect_secure_ai"
private const val GEMINI_KEY_PREF = "encrypted_gemini_api_key"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val GEMINI_KEY_ALIAS = "MrPerfectYadavGeminiKey"

private fun loadSavedGeminiKey(context: Context): String {
    return try {
        val stored = context.getSharedPreferences(GEMINI_PREFS, Context.MODE_PRIVATE)
            .getString(GEMINI_KEY_PREF, null) ?: return ""
        val pieces = stored.split(":")
        if (pieces.size != 2) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateGeminiSecretKey(), GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (_: Exception) {
        ""
    }
}

private fun saveGeminiKey(context: Context, value: String) {
    require(value.isNotBlank()) { "API Key खाली है" }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateGeminiSecretKey())
    val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    val encrypted = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    context.getSharedPreferences(GEMINI_PREFS, Context.MODE_PRIVATE)
        .edit().putString(GEMINI_KEY_PREF, "$iv:$encrypted").apply()
}

private fun clearSavedGeminiKey(context: Context) {
    context.getSharedPreferences(GEMINI_PREFS, Context.MODE_PRIVATE)
        .edit().remove(GEMINI_KEY_PREF).apply()
}

private fun getOrCreateGeminiSecretKey(): java.security.Key {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existing = keyStore.getKey(GEMINI_KEY_ALIAS, null)
    if (existing != null) return existing
    val generator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
    generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
        GEMINI_KEY_ALIAS,
        android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
    ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build())
    return generator.generateKey()
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


@Composable
private fun AssistSuggestion(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label, fontWeight = FontWeight.Bold) },
        colors = AssistChipDefaults.assistChipColors(labelColor = Color(0xFFFF4057)))
}
