package com.example.kanjireader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.SpannableString
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

object TranslationDialogHelper {
    
    fun showTranslationDialog(context: Context, originalText: String, translatedText: String) {
        // Create a SpannableString for the title with teal color (same as furigana button)
        val titleSpan = SpannableString("Translation")
        titleSpan.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.teal_700)),
            0,
            titleSpan.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        val dialog = AlertDialog.Builder(context)
            .setTitle(titleSpan)
            .setMessage(translatedText)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Translation", translatedText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Translation copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .create()
        
        // Set dark background like OCR text view
        dialog.window?.setBackgroundDrawableResource(R.drawable.bottom_sheet_background)
        
        dialog.show()
        
        // Style the copy button with teal color after showing
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(context, R.color.teal_700)
        )
    }
}