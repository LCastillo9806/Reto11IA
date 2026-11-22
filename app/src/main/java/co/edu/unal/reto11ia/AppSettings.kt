package co.edu.unal.reto11ia

import android.content.Context
import androidx.preference.PreferenceManager

/** Todo lo que se necesita para llamar a Gemini, leído de los Ajustes. */
data class AiConfig(
    val apiKey: String,
    val textModel: String,
    val imageModel: String,
    val persona: String
)

/**
 * Lee la configuración guardada en la pantalla de Ajustes.
 * La llave de API se toma de Ajustes; si está vacía, se usa la de
 * local.properties (BuildConfig.GEMINI_API_KEY).
 */
object AppSettings {
    const val KEY_API_KEY = "api_key"
    const val KEY_TEXT_MODEL = "text_model"
    const val KEY_IMAGE_MODEL = "image_model"
    const val KEY_PERSONA = "chat_persona"

    const val DEFAULT_TEXT_MODEL = "gemini-3.5-flash"
    const val DEFAULT_IMAGE_MODEL = "gemini-3.1-flash-image"

    fun load(context: Context): AiConfig {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val apiKey = prefs.getString(KEY_API_KEY, "").orEmpty().trim()
            .ifEmpty { BuildConfig.GEMINI_API_KEY }

        val personaKey = prefs.getString(KEY_PERSONA, "general")
        val personaText = context.getString(
            when (personaKey) {
                "agro" -> R.string.persona_agro_prompt
                "android" -> R.string.persona_android_prompt
                else -> R.string.persona_general_prompt
            }
        )

        return AiConfig(
            apiKey = apiKey,
            textModel = prefs.getString(KEY_TEXT_MODEL, "").orEmpty().trim().ifEmpty { DEFAULT_TEXT_MODEL },
            imageModel = prefs.getString(KEY_IMAGE_MODEL, "").orEmpty().trim().ifEmpty { DEFAULT_IMAGE_MODEL },
            persona = personaText
        )
    }
}
