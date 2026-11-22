package co.edu.unal.reto11ia

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * ¿Qué es un ViewModel?
 * Es una clase que guarda los datos de una pantalla y sobrevive cuando
 * Android recrea la Activity (por ejemplo, al girar el teléfono). Así la
 * conversación del chat no se borra al rotar.
 *
 * Cada ViewModel expone un "state" (StateFlow): la pantalla lo observa y
 * se redibuja sola cada vez que cambia.
 */

// =====================================================================
// 1. CHATBOT
// =====================================================================

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = false
)

class ChatViewModel : ViewModel() {

    companion object {
        // Cuántos mensajes anteriores se envían como "memoria" de la conversación
        private const val MAX_HISTORY = 20
    }

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    fun send(text: String, config: AiConfig) {
        if (_state.value.loading) return
        _state.update { it.copy(messages = it.messages + ChatMessage(Role.USER, text), loading = true) }

        viewModelScope.launch {
            try {
                val reply = GeminiClient(config.apiKey)
                    .chat(config.textModel, historyForApi(), config.persona)
                addMessage(ChatMessage(Role.MODEL, reply))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addMessage(ChatMessage(Role.ERROR, e.toUserMessage()))
            }
        }
    }

    fun clear() {
        _state.value = ChatUiState()
    }

    private fun addMessage(message: ChatMessage) {
        _state.update { it.copy(messages = it.messages + message, loading = false) }
    }

    /**
     * El historial que se envía a Gemini:
     *  - sin los mensajes de error,
     *  - sin las preguntas que fallaron (las que van seguidas de un error),
     *  - solo los últimos MAX_HISTORY y empezando siempre por el usuario.
     */
    private fun historyForApi(): List<ChatMessage> {
        val all = _state.value.messages
        val clean = all.filterIndexed { i, msg ->
            when (msg.role) {
                Role.ERROR -> false
                Role.USER -> all.getOrNull(i + 1)?.role != Role.ERROR
                Role.MODEL -> true
            }
        }
        return clean.takeLast(MAX_HISTORY).dropWhile { it.role != Role.USER }
    }
}

// =====================================================================
// 2. GENERACIÓN DE TEXTO (prompt predefinido + datos del usuario)
// =====================================================================

data class TextUiState(
    val result: String? = null,
    val error: String? = null,
    val loading: Boolean = false
)

class TextViewModel : ViewModel() {

    private val _state = MutableStateFlow(TextUiState())
    val state: StateFlow<TextUiState> = _state.asStateFlow()

    fun generate(prompt: String, config: AiConfig) {
        if (_state.value.loading) return
        _state.value = TextUiState(loading = true)
        viewModelScope.launch {
            _state.value = try {
                val text = GeminiClient(config.apiKey).generateText(config.textModel, prompt, temperature = 0.9)
                TextUiState(result = text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TextUiState(error = e.toUserMessage())
            }
        }
    }

    fun clear() {
        _state.value = TextUiState()
    }
}

// =====================================================================
// 3. GENERACIÓN DE IMÁGENES
// =====================================================================

data class ImageUiState(
    val bitmap: Bitmap? = null,
    val bytes: ByteArray? = null,
    val mimeType: String = "image/png",
    val caption: String? = null,
    val error: String? = null,
    val loading: Boolean = false
)

class ImageViewModel : ViewModel() {

    private val _state = MutableStateFlow(ImageUiState())
    val state: StateFlow<ImageUiState> = _state.asStateFlow()

    /**
     * Genera una imagen.
     *  1. Intenta con Gemini (modelo de imágenes).
     *  2. Si Gemini no tiene cuota para imágenes (nivel gratuito), usa el modelo
     *     libre Flux. Antes, Gemini (texto, gratis) convierte la descripción en
     *     un prompt detallado en inglés, que es como mejor funciona Flux.
     */
    fun generate(geminiPrompt: String, description: String, style: String, config: AiConfig) {
        if (_state.value.loading) return
        _state.value = ImageUiState(loading = true)
        viewModelScope.launch {
            _state.value = try {
                val gemini = GeminiClient(config.apiKey)
                val image = try {
                    val result = gemini.generateImage(config.imageModel, geminiPrompt)
                    GeneratedImage(
                        result.bytes, result.mimeType,
                        "Creada con Gemini (${config.imageModel})." +
                                (result.caption?.let { "\n$it" } ?: "")
                    )
                } catch (e: GeminiException) {
                    if (!e.isQuotaOrAccess && config.apiKey.isNotBlank()) throw e
                    generateWithFreeModel(gemini, description, style, config)
                }

                // Convertir los bytes en una imagen que el ImageView pueda mostrar
                val bitmap = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                } ?: throw GeminiException("No se pudo leer la imagen recibida.")
                ImageUiState(
                    bitmap = bitmap, bytes = image.bytes,
                    mimeType = image.mimeType, caption = image.caption
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImageUiState(error = e.toUserMessage())
            }
        }
    }

    private suspend fun generateWithFreeModel(
        gemini: GeminiClient, description: String, style: String, config: AiConfig
    ): GeneratedImage {
        // Gemini mejora el prompt; si falla, usamos la descripción tal cual
        val enhanced = runCatching {
            gemini.generateText(
                config.textModel,
                "Convert this image request into a single, vivid English prompt for an image " +
                        "generation model (max 60 words). Request: \"$description\". Style: $style. " +
                        "Reply with the prompt only.",
                temperature = 0.7
            ).lines().joinToString(" ").trim().trim('"')
        }.getOrNull()

        val prompt = enhanced ?: "$description, $style"
        val image = FreeImageClient().generate(prompt)
        val caption = buildString {
            append("Creada con el modelo libre ${FreeImageClient.MODEL_NAME}, ")
            append("porque el nivel gratuito de Gemini no incluye imágenes.")
            if (enhanced != null) append("\nPrompt mejorado por Gemini: \"$enhanced\"")
        }
        return GeneratedImage(image.bytes, image.mimeType, caption)
    }

    fun clear() {
        _state.value = ImageUiState()
    }
}
