package com.example.kanjireader.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import com.example.kanjireader.databinding.DialogCreateListBinding

/**
 * Dialog for creating a new word list.
 */
class CreateListDialog(
    private val context: Context,
    private val onListCreated: (String) -> Unit
) {
    
    fun show() {
        val binding = DialogCreateListBinding.inflate(LayoutInflater.from(context))
        
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setCancelable(true)
            .create()
        
        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        binding.btnCreate.setOnClickListener {
            val listName = binding.etListName.text?.toString()?.trim()
            
            when {
                listName.isNullOrEmpty() -> {
                    Toast.makeText(context, "Please enter a list name", Toast.LENGTH_SHORT).show()
                }
                listName.length > 50 -> {
                    Toast.makeText(context, "List name too long (max 50 characters)", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    onListCreated(listName)
                    dialog.dismiss()
                }
            }
        }
        
        dialog.show()
        
        // Focus on input and show keyboard
        binding.etListName.requestFocus()
    }
}