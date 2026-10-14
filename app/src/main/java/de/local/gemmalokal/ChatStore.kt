package de.local.gemmalokal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ChatMessage(val role: String, val text: String)
data class Chat(val id: String, var title: String, val messages: MutableList<ChatMessage>)

class ChatStore(context: Context) {
    private val file = File(context.filesDir, "chats.json")
    val chats: MutableList<Chat> = mutableListOf()

    init { load() }

    private fun load() {
        if (!file.exists()) return
        try {
            val data = JSONArray(file.readText())
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val messages = mutableListOf<ChatMessage>()
                val rows = item.getJSONArray("messages")
                for (j in 0 until rows.length()) {
                    val row = rows.getJSONObject(j)
                    messages += ChatMessage(row.getString("role"), row.getString("text"))
                }
                chats += Chat(item.getString("id"), item.getString("title"), messages)
            }
        } catch (_: Exception) {
            // A damaged local file should not prevent the app from opening.
        }
    }

    fun newChat(): Chat {
        val chat = Chat(UUID.randomUUID().toString(), "Neuer Chat", mutableListOf())
        chats.add(0, chat)
        save()
        return chat
    }

    fun delete(chat: Chat) {
        chats.removeAll { it.id == chat.id }
        save()
    }

    fun save() {
        val data = JSONArray()
        for (chat in chats) {
            val messages = JSONArray()
            for (message in chat.messages) {
                messages.put(JSONObject().put("role", message.role).put("text", message.text))
            }
            data.put(JSONObject().put("id", chat.id).put("title", chat.title).put("messages", messages))
        }
        val tmp = File(file.parentFile, "chats.json.tmp")
        tmp.writeText(data.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(data.toString())
            tmp.delete()
        }
    }
}
