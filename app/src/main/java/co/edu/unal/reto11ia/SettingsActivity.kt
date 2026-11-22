package co.edu.unal.reto11ia

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import co.edu.unal.reto11ia.databinding.ActivitySettingsBinding

/** Pantalla de Ajustes: llave de API, modelos de Gemini y personalidad del chatbot. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<EditTextPreference>(AppSettings.KEY_API_KEY)?.apply {
                // Que la llave se escriba oculta, como una contraseña
                setOnBindEditTextListener { editText ->
                    editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                // Resumen: nunca mostramos la llave completa
                summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                    val key = pref.text.orEmpty().trim()
                    when {
                        key.isNotEmpty() -> getString(R.string.pref_api_key_set, key.takeLast(4))
                        BuildConfig.GEMINI_API_KEY.isNotBlank() -> getString(R.string.pref_api_key_from_build)
                        else -> getString(R.string.pref_api_key_missing)
                    }
                }
            }

            findPreference<EditTextPreference>(AppSettings.KEY_TEXT_MODEL)
                ?.setSummaryProvider { pref ->
                    (pref as EditTextPreference).text.orEmpty().ifBlank { AppSettings.DEFAULT_TEXT_MODEL }
                }
            findPreference<EditTextPreference>(AppSettings.KEY_IMAGE_MODEL)
                ?.setSummaryProvider { pref ->
                    (pref as EditTextPreference).text.orEmpty().ifBlank { AppSettings.DEFAULT_IMAGE_MODEL }
                }

            // Abre la página de Google AI Studio para crear una llave
            findPreference<Preference>("get_key")?.setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
                true
            }
        }
    }
}
