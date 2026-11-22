package co.edu.unal.reto11ia

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.edu.unal.reto11ia.databinding.FragmentTextBinding
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch

/**
 * Pestaña 2: GENERACIÓN DE TEXTO.
 * El usuario elige tipo, tono y extensión y escribe un tema. Con eso
 * armamos un PROMPT PREDEFINIDO (una plantilla) que se envía a Gemini.
 */
class TextFragment : Fragment(R.layout.fragment_text), ClearableScreen {

    private var _binding: FragmentTextBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TextViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentTextBinding.bind(view)

        binding.btnGenerate.setOnClickListener { generate() }
        binding.btnCopy.setOnClickListener { copyResult() }
        binding.btnShare.setOnClickListener { shareResult() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun generate() {
        val topic = binding.inputTopic.text?.toString()?.trim().orEmpty()
        if (topic.isEmpty()) {
            binding.layoutTopic.error = getString(R.string.text_topic_required)
            return
        }
        binding.layoutTopic.error = null

        // ---- PROMPT PREDEFINIDO ----
        val prompt = getString(
            R.string.text_prompt_template,
            selectedChipText(binding.chipsType),
            topic,
            selectedChipText(binding.chipsTone),
            selectedChipText(binding.chipsLength)
        )
        viewModel.generate(prompt, AppSettings.load(requireContext()))
    }

    private fun selectedChipText(group: ChipGroup): String =
        group.findViewById<com.google.android.material.chip.Chip>(group.checkedChipId)?.text?.toString().orEmpty()

    private fun render(state: TextUiState) {
        binding.progress.isVisible = state.loading
        binding.btnGenerate.isEnabled = !state.loading
        binding.btnGenerate.setText(if (state.loading) R.string.generating else R.string.text_generate)

        binding.cardResult.isVisible = state.result != null
        binding.txtResult.text = state.result?.let { simpleMarkdown(it) }

        binding.txtError.isVisible = state.error != null
        binding.txtError.text = state.error
    }

    private fun copyResult() {
        val text = binding.txtResult.text?.toString().orEmpty()
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
        Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareResult() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, binding.txtResult.text?.toString().orEmpty())
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    override fun clearScreen() {
        viewModel.clear()
        binding.inputTopic.setText("")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
