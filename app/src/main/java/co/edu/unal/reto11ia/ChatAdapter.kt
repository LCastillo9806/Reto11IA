package co.edu.unal.reto11ia

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.unal.reto11ia.databinding.ItemMessageModelBinding
import co.edu.unal.reto11ia.databinding.ItemMessageUserBinding

/**
 * Adaptador del RecyclerView del chat: convierte cada ChatMessage en una "burbuja".
 *  - Mensajes del usuario  → burbuja a la derecha
 *  - Respuestas de la IA   → burbuja a la izquierda
 *  - Errores               → burbuja a la izquierda en rojo
 */
class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_MODEL = 1

        private val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(old: ChatMessage, new: ChatMessage) = old === new
            override fun areContentsTheSame(old: ChatMessage, new: ChatMessage) = old == new
        }
    }

    class UserHolder(val binding: ItemMessageUserBinding) : RecyclerView.ViewHolder(binding.root)
    class ModelHolder(val binding: ItemMessageModelBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int) =
        if (getItem(position).role == Role.USER) TYPE_USER else TYPE_MODEL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserHolder(ItemMessageUserBinding.inflate(inflater, parent, false))
        } else {
            ModelHolder(ItemMessageModelBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is UserHolder -> holder.binding.txtMessage.text = message.text
            is ModelHolder -> {
                val b = holder.binding
                val context = b.root.context
                val isError = message.role == Role.ERROR
                b.txtAuthor.setText(if (isError) R.string.chat_error_author else R.string.chat_ai_author)
                b.txtMessage.text = if (isError) message.text else simpleMarkdown(message.text)
                b.card.setCardBackgroundColor(
                    ContextCompat.getColor(context, if (isError) R.color.bubble_error else R.color.bubble_model)
                )
            }
        }
    }
}
