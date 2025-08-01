package com.example.kanjireader

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class DatabaseBuilderActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple UI
        val textView = TextView(this).apply {
            text = "Building database...\nThis will take a few minutes."
            setPadding(32, 32, 32, 32)
        }
        setContentView(textView)

        lifecycleScope.launch {
            val builder = DatabaseBuilder(this@DatabaseBuilderActivity)

            // Build database from your JSON files
            val success = builder.buildDatabase()

            if (success) {
                // Export to external storage for adb pull
                builder.exportDatabase()
                Toast.makeText(
                    this@DatabaseBuilderActivity,
                    "Database built successfully! Check logs for path.",
                    Toast.LENGTH_LONG
                ).show()
                textView.text = "Database built successfully!\nCheck logcat for the path to pull the file."
            } else {
                Toast.makeText(
                    this@DatabaseBuilderActivity,
                    "Database build failed! Check logs.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}