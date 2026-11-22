package co.edu.unal.reto11ia

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import co.edu.unal.reto11ia.databinding.FragmentChatBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

/** Pestaña 1: CHATBOT. */
class ChatFragment : Fragment(R.layout.fragment_chat), ClearableScreen {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatViewModel by viewModels()
    private val adapter = ChatAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentChatBinding.bind(view)

        binding.recycler.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true  // los mensajes nuevos aparecen abajo, como en WhatsApp
        }
        binding.recycler.adapter = adapter

        binding.btnSend.setOnClickListener { sendCurrentText() }
        binding.input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentText(); true
            } else false
        }

        // Sugerencias para empezar a conversar con un toque
        for (suggestion in resources.getStringArray(R.array.chat_suggestions)) {
            val chip = Chip(requireContext()).apply {
                text = suggestion
                setOnClickListener { send(suggestion) }
            }
            binding.chipsSuggestions.addView(chip)
        }

        // Observamos el estado del ViewModel y redibujamos cuando cambia
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: ChatUiState) {
        adapter.submitList(state.messages) {
            if (state.messages.isNotEmpty()) {
                // Mostramos el COMIENZO del último mensaje, para leer la respuesta desde arriba
                (binding.recycler.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(state.messages.lastIndex, 0)
            }
        }
        binding.emptyView.isVisible = state.messages.isEmpty()
        binding.txtTyping.isVisible = state.loading
        binding.progress.isVisible = state.loading
        binding.btnSend.isEnabled = !state.loading
    }

    private fun sendCurrentText() {
        val text = binding.input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        binding.input.setText("")
        send(text)
    }

    private fun send(text: String) {
        viewModel.send(text, AppSettings.load(requireContext()))
    }

    override fun clearScreen() = viewModel.clear()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
