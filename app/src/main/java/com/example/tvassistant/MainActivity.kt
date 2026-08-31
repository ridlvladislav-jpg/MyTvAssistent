package com.example.tvassistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var aiResponseText: TextView
    private val client = OkHttpClient()
    
    // ТВОЙ КЛЮЧ OPENROUTER
    private val API_KEY = "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"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusTextView)
        aiResponseText = findViewById(R.id.aiResponseText)
        val listenButton = findViewById<Button>(R.id.listenButton)
        val nameInput = findViewById<EditText>(R.id.nameInput)

        // Загружаем имя ассистента
        val prefs = getSharedPreferences("AssistantPrefs", MODE_PRIVATE)
        nameInput.setText(prefs.getString("assistant_name", "Джарвис"))

        listenButton.setOnClickListener {
            val name = nameInput.text.toString()
            prefs.edit().putString("assistant_name", name).apply()
            startVoiceRecognition()
        }

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Слушаю вашу команду...")
        
        try {
            startActivityForResult(intent, 100)
        } catch (e: Exception) {
            Toast.makeText(this, "Голосовой ввод недоступен", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val userText = result?.get(0) ?: ""
            sendToOpenRouter(userText)
        }
    }

    private fun sendToOpenRouter(userText: String) {
        runOnUiThread { statusText.text = "ИИ думает..." }

        val url = "https://openrouter.ai/api/v1/chat/completions"
        
        val systemPrompt = """
            Ты - мозг Android TV. Анализируй фразу и отвечай ТОЛЬКО JSON-объектом.
            Формат: {"action": "chat|open_url|open_app", "value": "...", "response": "..."}
            Примеры:
            - "включи ютуб" -> {"action": "open_app", "value": "com.google.android.youtube", "response": "Включаю YouTube"}
            - "открой гугл" -> {"action": "open_url", "value": "https://google.com", "response": "Открываю браузер"}
            - "привет" -> {"action": "chat", "value": "", "response": "Привет! Чем помочь?"}
        """.trimIndent()

        val json = JSONObject().apply {
            put("model", "google/gemini-2.0-flash-exp:free")
            val messages = JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                put(JSONObject().apply { put("role", "user"); put("content", userText) })
            }
            put("messages", messages)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $API_KEY")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { statusText.text = "Ошибка сети: ${e.message}" }
            }

            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string()
                if (data != null) {
                    try {
                        val aiContent = JSONObject(data).getJSONArray("choices")
                            .getJSONObject(0).getJSONObject("message").getString("content")
                        
                        // Чистим ответ от возможных лишних символов ```json ... ```
                        val cleanJson = aiContent.substringAfter("{").substringBeforeLast("}")
                        val command = JSONObject("{$cleanJson}")
                        
                        executeAction(
                            command.getString("action"),
                            command.getString("value"),
                            command.getString("response")
                        )
                    } catch (e: Exception) {
                        runOnUiThread { statusText.text = "ИИ ответил не по формату" }
                    }
                }
            }
        })
    }

    private fun executeAction(action: String, value: String, response: String) {
        runOnUiThread {
            aiResponseText.text = response
            statusText.text = "Выполнено"

            when (action) {
                "open_app" -> {
                    val intent = packageManager.getLaunchIntentForPackage(value)
                    if (intent != null) startActivity(intent) 
                    else Toast.makeText(this, "Приложение $value не найдено", Toast.LENGTH_SHORT).show()
                }
                "open_url" -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(value))
                    startActivity(intent)
                }
            }
        }
    }
}
