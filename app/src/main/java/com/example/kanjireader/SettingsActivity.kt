package com.example.kanjireader

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SettingsActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "SettingsActivity onCreate() called")
        
        setContentView(R.layout.activity_simple_settings)
        
        // Set up toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
        
        // Set the navigation icon color to white in light mode, grey in dark mode
        val navigationIcon = toolbar.navigationIcon
        navigationIcon?.setTint(ContextCompat.getColor(this, R.color.toolbar_icon_text_color))
        
        // Handle back navigation
        toolbar.setNavigationOnClickListener {
            Log.d(TAG, "Back button clicked")
            finish()
        }
        
        // Setup theme selection
        setupThemeSelection()
        
        Log.d(TAG, "SettingsActivity setup complete")
    }
    
    private fun setupThemeSelection() {
        val currentTheme = ThemeManager.getSavedTheme(this)
        
        findViewById<TextView>(R.id.themeSystem).setOnClickListener {
            Log.d(TAG, "System theme selected")
            applyTheme(ThemeManager.THEME_SYSTEM)
        }
        
        findViewById<TextView>(R.id.themeLight).setOnClickListener {
            Log.d(TAG, "Light theme selected")
            applyTheme(ThemeManager.THEME_LIGHT)
        }
        
        findViewById<TextView>(R.id.themeDark).setOnClickListener {
            Log.d(TAG, "Dark theme selected")
            applyTheme(ThemeManager.THEME_DARK)
        }
        
        // Highlight current theme with theme-appropriate color
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorSecondaryContainer, typedValue, true)
        val highlightColor = typedValue.data
        
        when (currentTheme) {
            ThemeManager.THEME_SYSTEM -> findViewById<TextView>(R.id.themeSystem).setBackgroundColor(highlightColor)
            ThemeManager.THEME_LIGHT -> findViewById<TextView>(R.id.themeLight).setBackgroundColor(highlightColor)
            ThemeManager.THEME_DARK -> findViewById<TextView>(R.id.themeDark).setBackgroundColor(highlightColor)
        }
    }
    
    private fun applyTheme(theme: String) {
        ThemeManager.saveTheme(this, theme)
        ThemeManager.applyTheme(theme)
        recreate() // Recreate activity to apply theme
    }
}