package co.edu.unal.reto11ia

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Generador de imágenes con un MODELO LIBRE (Flux) a través de Pollinations.ai.
 *
 * ¿Por qué existe? El nivel gratuito de Gemini no incluye la generación de
 * imágenes (responde "429 quota exceeded"). El PDF del reto permite usar
 * "modelos libres que se ejecuten en la nube", así que cuando Gemini no
 * puede dibujar, usamos Flux. Es gratis y no necesita llave.
 *
 * Se usa con una simple petición GET:
 *   https://image.pollinations.ai/prompt/{descripción}?width=1024&height=1024&model=flux
 * y la respuesta es directamente la imagen JPEG.
 */
class FreeImageClient {

    companion object {
        private const val BASE_URL = "https://image.pollinations.ai/prompt/"
        const val MODEL_NAME = "Flux (Pollinations.ai)"
    }

    suspend fun generate(prompt: String): GeneratedImage = withContext(Dispatchers.IO) {
        // En una URL los espacios deben ir como %20 (URLEncoder los pone como +)
        val encoded = URLEncoder.encode(prompt, "UTF-8").replace("+", "%20")
        val seed = Random.nextInt(1, 1_000_000)  // semilla distinta = imagen distinta cada vez
        val url = "$BASE_URL$encoded?width=1024&height=1024&model=flux&nologo=true&seed=$seed"

        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.setRequestProperty("User-Agent", "Reto11IA/1.0 (app educativa Android)")

            val code = conn.responseCode
            val type = conn.contentType.orEmpty()
            if (code !in 200..299 || !type.startsWith("image/")) {
                throw IOException("El generador libre respondió HTTP $code")
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            GeneratedImage(bytes, type.substringBefore(';'), null)
        } finally {
            conn.disconnect()
        }
    }
}
