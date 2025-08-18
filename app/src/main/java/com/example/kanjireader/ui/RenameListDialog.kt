package com.example.kanjireader.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import com.example.kanjireader.databinding.DialogRenameListBinding

/**
 * Dialog for renaming an existing word list.
 */
class RenameListDialog(
    private val context: Context,
    private val currentName: String,
    private val onListRenamed: (String) -> Unit
) {
    
    fun show() {
        val binding = DialogRenameListBinding.inflate(LayoutInflater.from(context))
        
        // Pre-populate with current name
        binding.etListName.setText(currentName)
        binding.etListName.selectAll()
        
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setCancelable(true)
            .create()
        
        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        binding.btnRename.setOnClickListener {
            val listName = binding.etListName.text?.toString()?.trim()
            
            when {
                listName.isNullOrEmpty() -> {
                    Toast.makeText(context, "Please enter a list name", Toast.LENGTH_SHORT).show()
                }
                listName.length > 50 -> {
                    Toast.makeText(context, "List name too long (max 50 characters)", Toast.LENGTH_SHORT).show()
                }
                listName == currentName -> {
                    Toast.makeText(context, "Name is the same as current", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    onListRenamed(listName)
                    dialog.dismiss()
                }
            }
        }
        
        dialog.show()
        
        // Focus on input and show keyboard
        binding.etListName.requestFocus()
    }
}