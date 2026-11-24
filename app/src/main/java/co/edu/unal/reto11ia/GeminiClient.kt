package co.edu.unal.reto11ia

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

// =====================================================================
// MODELOS DE DATOS
// =====================================================================

/** Quién escribió un mensaje del chat. ERROR solo se muestra, no se envía a la IA. */
enum class Role { USER, MODEL, ERROR }

data class ChatMessage(val role: Role, val text: String)

/** Imagen generada: los bytes (PNG/JPEG) y, opcionalmente, un comentario de la IA. */
class GeneratedImage(val bytes: ByteArray, val mimeType: String, val caption: String?)

/**
 * Error con un mensaje en español, listo para mostrar al usuario.
 * [httpCode] es el código HTTP (429 = sin cuota, 404 = modelo no existe...), o 0.
 */
class GeminiException(message: String, val httpCode: Int = 0) : Exception(message) {
    /** true si el problema es de cuota/permiso: vale la pena usar el modelo libre. */
    val isQuotaOrAccess: Boolean get() = httpCode == 429 || httpCode == 403 || httpCode == 404
}

// =====================================================================
// CLIENTE DE GEMINI (API REST de Google AI Studio)
//
// Todas las funciones usan el mismo endpoint:
//   POST https://generativelanguage.googleapis.com/v1beta/models/{modelo}:generateContent
//   Cabecera: x-goog-api-key: TU_LLAVE
//
// Lo que cambia es el JSON que enviamos:
//   - Chat:    toda la conversación (contents con role user/model)
//   - Texto:   un solo prompt construido a partir del formulario
//   - Imagen:  un prompt + responseModalities = ["TEXT", "IMAGE"]
// =====================================================================

class GeminiClient(private val apiKey: String) {

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

        /** Modelo de respaldo para chat y texto: se usa si el principal está saturado o tarda demasiado. */
        const val FALLBACK_TEXT_MODEL = "gemini-3.5-flash-lite"
    }

    /** CHATBOT: envía el historial completo para que la IA "recuerde" la conversación. */
    suspend fun chat(model: String, history: List<ChatMessage>, systemInstruction: String?): String {
        val contents = JSONArray()
        for (message in history) {
            val role = if (message.role == Role.USER) "user" else "model"
            contents.put(content(role, message.text))
        }
        val body = JSONObject()
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.7))
        systemInstruction?.let { body.put("systemInstruction", textParts(it)) }

        return extractText(requestWithFallback(model, body, timeoutMs = 120_000))
    }

    /** GENERACIÓN DE TEXTO: un solo prompt, sin historial. */
    suspend fun generateText(model: String, prompt: String, temperature: Double): String {
        val body = JSONObject()
            .put("contents", JSONArray().put(content("user", prompt)))
            .put("generationConfig", JSONObject().put("temperature", temperature))
        return extractText(requestWithFallback(model, body, timeoutMs = 120_000))
    }

    /** GENERACIÓN DE IMÁGENES: le pedimos que responda con texto E imagen. */
    suspend fun generateImage(model: String, prompt: String): GeneratedImage {
        val body = JSONObject()
            .put("contents", JSONArray().put(content("user", prompt)))
            .put(
                "generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
            )
        val json = request(model, body, timeoutMs = 120_000)  // las imágenes tardan más

        val parts = extractParts(json)
        var image: GeneratedImage? = null
        val caption = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
            if (inline != null && image == null) {
                // La imagen llega como texto Base64: la convertimos a bytes
                val bytes = Base64.decode(inline.getString("data"), Base64.DEFAULT)
                val mime = inline.optString("mimeType").ifEmpty { inline.optString("mime_type", "image/png") }
                image = GeneratedImage(bytes, mime, null)
            } else if (part.has("text") && !part.optBoolean("thought")) {
                caption.append(part.getString("text"))
            }
        }
        val result = image ?: throw GeminiException(
            "El modelo no generó una imagen. Prueba con otra descripción." +
                    if (caption.isNotBlank()) "\n\nRespuesta: ${caption.toString().trim()}" else ""
        )
        return GeneratedImage(result.bytes, result.mimeType, caption.toString().trim().ifEmpty { null })
    }

    // -----------------------------------------------------------------
    // Detalles internos
    // -----------------------------------------------------------------

    /** {"role": "...", "parts": [{"text": "..."}]} */
    private fun content(role: String, text: String) = textParts(text).put("role", role)

    /** {"parts": [{"text": "..."}]} */
    private fun textParts(text: String) =
        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))

    /**
     * Chat y texto: intenta con el modelo elegido y, si está saturado (500/503) o tarda
     * demasiado, prueba con el modelo de respaldo. Así la app sigue funcionando cuando
     * los servidores de Gemini tienen mucha demanda.
     */
    private suspend fun requestWithFallback(model: String, body: JSONObject, timeoutMs: Int): JSONObject {
        var lastError: Exception? = null
        for (m in listOf(model.trim(), FALLBACK_TEXT_MODEL).distinct()) {
            try {
                return request(m, body, timeoutMs)
            } catch (e: GeminiException) {
                if (e.httpCode < 500) throw e   // llave inválida, modelo inexistente...: no sirve reintentar
                lastError = e
            } catch (e: SocketTimeoutException) {
                lastError = e                   // tardó demasiado: probamos con el respaldo
            }
        }
        throw lastError!!
    }

    /**
     * Hace la petición HTTP y devuelve el JSON de respuesta.
     * Si el servidor está ocupado (500/503), lo reintenta hasta 3 veces (esperando 2, 4 y 8 s).
     */
    private suspend fun request(model: String, body: JSONObject, timeoutMs: Int): JSONObject {
        if (apiKey.isBlank()) {
            throw GeminiException(
                "Falta la llave de API de Gemini. Pégala en Ajustes o en local.properties " +
                        "(GEMINI_API_KEY) y vuelve a compilar."
            )
        }
        var attempt = 0
        while (true) {
            try {
                return requestOnce(model, body, timeoutMs)
            } catch (e: GeminiException) {
                attempt++
                if (e.httpCode < 500 || attempt > 3) throw e
                delay(2_000L shl (attempt - 1))  // espera 2 s, 4 s, 8 s y reintenta
            }
        }
    }

    private suspend fun requestOnce(model: String, body: JSONObject, timeoutMs: Int): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(BASE_URL + model.trim() + ":generateContent").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000
                conn.readTimeout = timeoutMs
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.setRequestProperty("x-goog-api-key", apiKey.trim())
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw GeminiException(friendlyError(code, text, model), code)
                JSONObject(text)
            } finally {
                conn.disconnect()
            }
        }

    /** Saca la lista de "parts" de la primera respuesta (o explica por qué no hay). */
    private fun extractParts(json: JSONObject): JSONArray {
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val reason = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw GeminiException(
                if (reason.isEmpty()) "La IA no devolvió ninguna respuesta."
                else "La IA bloqueó la solicitud por seguridad ($reason). Intenta con otras palabras."
            )
        }
        val candidate = candidates.getJSONObject(0)
        return candidate.optJSONObject("content")?.optJSONArray("parts")
            ?: throw GeminiException(
                "La IA no pudo responder (motivo: ${candidate.optString("finishReason", "desconocido")})."
            )
    }

    /** Une los fragmentos de texto de la respuesta (ignorando los "pensamientos" internos). */
    private fun extractText(json: JSONObject): String {
        val parts = extractParts(json)
        val text = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.has("text") && !part.optBoolean("thought")) text.append(part.getString("text"))
        }
        return text.toString().trim().ifEmpty { throw GeminiException("La IA devolvió una respuesta vacía.") }
    }

    /** Traduce los errores HTTP de Gemini a mensajes entendibles. */
    private fun friendlyError(code: Int, body: String, model: String): String {
        val detail = runCatching {
            JSONObject(body).getJSONObject("error").optString("message")
        }.getOrNull().orEmpty().take(300)

        val message = when {
            code == 400 && detail.contains("API key", ignoreCase = true) ->
                "La llave de API no es válida. Revísala en Ajustes."
            code == 403 ->
                "Gemini rechazó la llave (sin permiso). Revisa que la llave sea de Google AI Studio."
            code == 404 ->
                "El modelo \"$model\" no existe o no está disponible para tu llave. Cámbialo en Ajustes."
            code == 429 ->
                "Se alcanzó el límite de uso de Gemini (cuota). Espera un momento e inténtalo de nuevo. " +
                        "Algunos modelos (como los de imágenes) pueden no estar en el nivel gratuito."
            code >= 500 ->
                "Los servidores de Gemini están ocupados. Inténtalo de nuevo en unos segundos."
            else -> "Error $code al hablar con Gemini."
        }
        return if (detail.isNotEmpty()) "$message\n\nDetalle: $detail" else message
    }
}

/** Convierte cualquier excepción en un texto para el usuario. */
fun Throwable.toUserMessage(): String = when (this) {
    is GeminiException -> message.orEmpty()
    is SocketTimeoutException ->
        "Gemini tardó demasiado en responder (sus servidores pueden estar saturados). Inténtalo de nuevo en un momento."
    is IOException -> "No hay conexión a internet o el servidor no respondió. Revisa tu red."
    else -> "Error inesperado: ${message ?: javaClass.simpleName}"
}
