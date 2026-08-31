package com.example.tvassistant

import android.Manifest
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusTextView)
        val nameInput = findViewById<EditText>(R.id.nameInput)
        val saveButton = findViewById<Button>(R.id.saveButton)

        saveButton.setOnClickListener {
            val name = nameInput.text.toString()
            // Сохраняем имя во внутреннее хранилище
            val prefs = getSharedPreferences("AssistantSettings", MODE_PRIVATE)
            prefs.edit().putString("trigger_name", name).apply()
            
            Toast.makeText(this, "Имя $name сохранено", Toast.LENGTH_SHORT).show()
            statusText.text = "Слушаю имя: $name"
        }

        // Запрашиваем микрофон сразу при запуске
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }
}