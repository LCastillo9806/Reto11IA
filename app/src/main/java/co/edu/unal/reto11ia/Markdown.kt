package co.edu.unal.reto11ia

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/**
 * Gemini responde con formato Markdown. Esta función hace una conversión
 * sencilla para que se vea bien en un TextView:
 *  - "**texto**"  → negrita
 *  - "*texto*"    → cursiva
 *  - "`código`"   → letra de máquina de escribir (monospace)
 *  - "# Título"   → título en negrita
 *  - "* item"     → "• item"
 *  - "---"        → se elimina (línea separadora)
 */
private val HEADING = Regex("^\\s*#{1,6}\\s+(.*)$")
private val BULLET = Regex("^(\\s*)[*-]\\s+")
private val RULE = Regex("^\\s*([-*_])\\s*(\\1\\s*){2,}$")

// Grupo 1 = **negrita**, grupo 2 = *cursiva*, grupo 3 = `código`
private val INLINE = Regex(
    "\\*\\*(.+?)\\*\\*" +
            "|(?<![*\\w])\\*(?![\\s*])(.+?)(?<![\\s*])\\*(?![*\\w])" +
            "|`([^`]+)`"
)

fun simpleMarkdown(text: String): CharSequence {
    val prepared = text.lines().joinToString("\n") { line ->
        val heading = HEADING.find(line)
        when {
            RULE.matches(line) -> ""
            heading != null -> "**${heading.groupValues[1].trim().trim('*')}**"
            else -> line.replaceFirst(BULLET, "$1• ")
        }
    }.replace(Regex("\n{3,}"), "\n\n")  // no más de una línea en blanco seguida

    val result = SpannableStringBuilder()
    var lastEnd = 0
    for (match in INLINE.findAll(prepared)) {
        result.append(prepared, lastEnd, match.range.first)
        val (bold, italic, code) = match.destructured
        val start = result.length
        val span: Any = when {
            bold.isNotEmpty() -> {
                result.append(bold); StyleSpan(Typeface.BOLD)
            }
            italic.isNotEmpty() -> {
                result.append(italic); StyleSpan(Typeface.ITALIC)
            }
            else -> {
                result.append(code); TypefaceSpan("monospace")
            }
        }
        result.setSpan(span, start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        lastEnd = match.range.last + 1
    }
    result.append(prepared, lastEnd, prepared.length)
    return result
}
