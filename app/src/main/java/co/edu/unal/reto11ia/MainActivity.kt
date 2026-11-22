package co.edu.unal.reto11ia

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import co.edu.unal.reto11ia.databinding.ActivityMainBinding

/** Las pantallas que se pueden "limpiar" desde el menú (Nueva conversación / Limpiar). */
interface ClearableScreen {
    fun clearScreen()
}

/**
 * Pantalla principal: barra superior + 3 pestañas en la barra inferior.
 *   💬 Chat    → ChatFragment
 *   📝 Texto   → TextFragment
 *   🖼️ Imagen  → ImageFragment
 *
 * Los tres fragments se crean una sola vez y solo se muestran/ocultan,
 * así no se pierde lo que había en cada pestaña al cambiar de una a otra.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val tabs = mapOf(
        R.id.nav_chat to "chat",
        R.id.nav_text to "text",
        R.id.nav_image to "image"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Evita que las barras del sistema y el teclado tapen el contenido
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        setSupportActionBar(binding.toolbar)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.container, ChatFragment(), "chat")
                .add(R.id.container, TextFragment(), "text")
                .add(R.id.container, ImageFragment(), "image")
                .commitNow()
            showTab(R.id.nav_chat)
        } else {
            // Al girar la pantalla, Android recuerda qué pestaña estaba visible
            binding.toolbar.subtitle = binding.bottomNav.menu
                .findItem(binding.bottomNav.selectedItemId)?.title
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
    }

    private fun fragmentFor(itemId: Int): Fragment? =
        supportFragmentManager.findFragmentByTag(tabs[itemId])

    private fun showTab(itemId: Int) {
        val transaction = supportFragmentManager.beginTransaction()
        for (id in tabs.keys) {
            val fragment = fragmentFor(id) ?: continue
            if (id == itemId) transaction.show(fragment) else transaction.hide(fragment)
        }
        transaction.commit()
        binding.toolbar.subtitle = binding.bottomNav.menu.findItem(itemId)?.title
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_clear -> {
            (fragmentFor(binding.bottomNav.selectedItemId) as? ClearableScreen)?.clearScreen()
            true
        }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
