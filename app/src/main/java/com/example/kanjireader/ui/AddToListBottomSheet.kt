package com.example.kanjireader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.SpannableString
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kanjireader.EnhancedWordResult
import com.example.kanjireader.databinding.FragmentAddToListBottomSheetBinding
import com.example.kanjireader.viewmodel.WordListViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * Bottom sheet fragment for adding words to lists.
 */
class AddToListBottomSheet : BottomSheetDialogFragment() {
    
    // Callback to notify parent when word is added/removed from lists
    var onWordListChanged: (() -> Unit)? = null
    
    private var _binding: FragmentAddToListBottomSheetBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: WordListViewModel
    private lateinit var adapter: WordListCheckboxAdapter
    
    private var currentWord: EnhancedWordResult? = null
    
    // Track ongoing operations to prevent duplicates
    private val ongoingOperations = mutableSetOf<Long>()
    
    companion object {
        private const val ARG_WORD = "word"
        
        fun newInstance(word: EnhancedWordResult): AddToListBottomSheet {
            val fragment = AddToListBottomSheet()
            val args = Bundle()
            args.putSerializable(ARG_WORD, word)
            fragment.arguments = args
            return fragment
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        )[WordListViewModel::class.java]
        
        currentWord = arguments?.getSerializable(ARG_WORD) as? EnhancedWordResult
        currentWord?.let { word ->
            viewModel.setCurrentWord(word)
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddToListBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }
    
    private fun setupUI() {
        // UI setup is now handled in setupClickListeners
    }
    
    private fun setupRecyclerView() {
        adapter = WordListCheckboxAdapter(
            onListToggled = { listId, isSelected ->
                viewModel.toggleListSelection(listId)
                // Immediately save/remove word when checkbox is toggled
                saveWordToListToggle(listId, isSelected)
            },
            onListLongPressed = { selectableList ->
                showDeleteListConfirmation(selectableList.wordList)
            }
        )
        
        binding.recyclerViewLists.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewLists.adapter = adapter
    }
    
    private fun setupObservers() {
        // Observe word lists and selection state
        viewModel.allWordLists.observe(viewLifecycleOwner) { lists ->
            updateSelectableWordLists()
        }
        
        viewModel.selectedListIds.observe(viewLifecycleOwner) { 
            updateSelectableWordLists()
        }
        
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        // Observe operation results
        viewModel.operationSuccess.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
                // Notify parent that word list changed
                onWordListChanged?.invoke()
                // Don't dismiss automatically - let user keep selecting lists
            }
        }
        
        viewModel.operationError.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
        }
    }
    
    private fun updateSelectableWordLists() {
        val lists = viewModel.allWordLists.value ?: return
        val selectedIds = viewModel.selectedListIds.value ?: emptySet()
        
        val selectableLists = lists.map { list ->
            WordListViewModel.SelectableWordList(
                wordList = list,
                isSelected = list.listId in selectedIds
            )
        }
        
        adapter.submitList(selectableLists)
    }
    
    private fun setupClickListeners() {
        binding.btnClose.setOnClickListener {
            dismiss()
        }
        
        binding.btnAddNewList.setOnClickListener {
            val createListDialog = CreateListDialog(requireContext()) { listName ->
                viewModel.createWordList(listName)
            }
            createListDialog.show()
        }
    }
    
    private fun saveWordToListToggle(listId: Long, isSelected: Boolean) {
        val word = currentWord ?: return
        
        // Prevent duplicate operations
        if (listId in ongoingOperations) {
            return
        }
        
        ongoingOperations.add(listId)
        
        lifecycleScope.launch {
            try {
                if (isSelected) {
                    viewModel.addWordToSingleList(word, listId)
                } else {
                    viewModel.removeWordFromSingleList(listId, word)
                }
                
                // Refresh the selection state after operation completes
                kotlinx.coroutines.delay(200) // Small delay to ensure database operation completes
                viewModel.setCurrentWord(word) // This will refresh the selected lists
                
                // Always notify parent of change
                onWordListChanged?.invoke()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error updating list", Toast.LENGTH_SHORT).show()
                // Refresh state even on error to ensure UI consistency
                viewModel.setCurrentWord(word)
            } finally {
                // Remove from ongoing operations
                ongoingOperations.remove(listId)
            }
        }
    }
    
    private fun showDeleteListConfirmation(wordList: com.example.kanjireader.database.WordListEntity) {
        val message = "Are you sure you want to delete the list '${wordList.name}'?\n\nThis will remove ${wordList.wordCount} words from this list."
        val spannableMessage = SpannableString(message)
        
        // Make the list name bold (just the name, not the quotes)
        val nameStart = message.indexOf(wordList.name)
        val nameEnd = nameStart + wordList.name.length
        if (nameStart >= 0) {
            spannableMessage.setSpan(StyleSpan(Typeface.BOLD), nameStart, nameEnd, 0)
        }
        
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Delete List")
            .setMessage(spannableMessage)
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteWordList(wordList)
            }
            .setNegativeButton("Cancel", null)
            .create()
            
        dialog.show()
        
        // Make delete button red
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
        )
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}