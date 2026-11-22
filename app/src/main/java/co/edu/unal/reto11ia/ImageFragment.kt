package co.edu.unal.reto11ia

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.edu.unal.reto11ia.databinding.FragmentImageBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pestaña 3: GENERACIÓN DE IMÁGENES.
 * El usuario describe la imagen y elige un estilo; Gemini la dibuja.
 */
class ImageFragment : Fragment(R.layout.fragment_image), ClearableScreen {

    private var _binding: FragmentImageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ImageViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentImageBinding.bind(view)

        binding.btnGenerate.setOnClickListener { generate() }
        binding.btnSave.setOnClickListener { saveToGallery() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun generate() {
        val description = binding.inputDescription.text?.toString()?.trim().orEmpty()
        if (description.isEmpty()) {
            binding.layoutDescription.error = getString(R.string.image_description_required)
            return
        }
        binding.layoutDescription.error = null

        val style = binding.chipsStyle.findViewById<Chip>(binding.chipsStyle.checkedChipId)
            ?.text?.toString().orEmpty()
        val prompt = getString(R.string.image_prompt_template, description, style)
        viewModel.generate(prompt, description, style, AppSettings.load(requireContext()))
    }

    private fun render(state: ImageUiState) {
        binding.progress.isVisible = state.loading
        binding.txtWaiting.isVisible = state.loading
        binding.btnGenerate.isEnabled = !state.loading
        binding.btnGenerate.setText(if (state.loading) R.string.generating else R.string.image_generate)

        binding.cardImage.isVisible = state.bitmap != null
        binding.imgResult.setImageBitmap(state.bitmap)
        binding.txtCaption.isVisible = !state.caption.isNullOrBlank()
        binding.txtCaption.text = state.caption

        binding.txtError.isVisible = state.error != null
        binding.txtError.text = state.error
    }

    /** Guarda la imagen en la galería (carpeta Imágenes/Reto11IA). */
    private fun saveToGallery() {
        val state = viewModel.state.value
        val bytes = state.bytes ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(requireContext(), R.string.save_not_supported, Toast.LENGTH_LONG).show()
            return
        }
        val extension = if (state.mimeType.contains("jpeg") || state.mimeType.contains("jpg")) "jpg" else "png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "reto11_${System.currentTimeMillis()}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, state.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Reto11IA")
        }
        val resolver = requireContext().contentResolver
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
                    resolver.openOutputStream(uri)!!.use { it.write(bytes) }
                }.isSuccess
            }
            Toast.makeText(
                requireContext(), if (ok) R.string.saved else R.string.save_error, Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun clearScreen() {
        viewModel.clear()
        binding.inputDescription.setText("")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
