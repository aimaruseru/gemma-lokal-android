package de.local.gemmalokal

import android.app.AlertDialog
import android.app.DownloadManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.app.Activity
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var store: ChatStore
    private lateinit var model: ModelDownload
    private lateinit var current: Chat
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var conversationChatId: String? = null
    @Volatile private var modelLoaded = false
    private var busy = false
    private var modelChanging = false
    private var checking = false
    private var settingsOpen = false

    private lateinit var root: LinearLayout
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var entry: EditText
    private lateinit var send: Button
    private lateinit var status: TextView
    private lateinit var runtimeStatus: TextView
    private lateinit var loadButton: Button
    private lateinit var unloadButton: Button

    private val ink = Color.rgb(30, 39, 52)
    private val accent = Color.rgb(41, 98, 255)
    private val light = Color.rgb(244, 247, 252)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ChatStore(this)
        model = ModelDownload(this)
        current = store.chats.firstOrNull() ?: store.newChat()
        showChat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float = 16f, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(ink)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun button(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun base(): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(8))
        }
        setContentView(root)
        return root
    }

    private fun showChat() {
        settingsOpen = false
        base()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(button("Chats") { showChats() })
        top.addView(button("+ Neu") {
            if (busy) return@button
            current = store.newChat()
            showChat()
        })
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(button("⚙") { showSettings() })
        root.addView(top)
        root.addView(label(current.title, 20f, true).apply { setPadding(dp(6), dp(8), 0, dp(8)) })

        scroll = ScrollView(this).apply { isFillViewport = true }
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4)) }
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        status = label(when {
            modelLoaded -> "Modell geladen. Alles bleibt lokal auf diesem Gerät."
            model.ready -> "Modell vorhanden. Unter ⚙ in den Arbeitsspeicher laden."
            else -> "Modell fehlt. Unter ⚙ herunterladen."
        }, 13f)
        status.setPadding(dp(4), dp(4), dp(4), dp(8))
        root.addView(status)

        val input = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        entry = EditText(this).apply {
            hint = "Frage stellen …"
            minLines = 1
            maxLines = 5
            setSingleLine(false)
        }
        input.addView(entry, LinearLayout.LayoutParams(0, -2, 1f))
        send = button("Senden") { submit() }
        input.addView(send)
        root.addView(input)
        renderMessages()
        send.isEnabled = modelLoaded && !busy && !modelChanging
    }

    private fun bubble(text: String, mine: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (mine) Gravity.RIGHT else Gravity.LEFT
            setPadding(0, dp(5), 0, dp(5))
        }
        val view = label(text).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setTextColor(if (mine) Color.WHITE else ink)
            background = GradientDrawable().apply {
                cornerRadius = dp(15).toFloat()
                setColor(if (mine) accent else light)
            }
        }
        row.addView(view, LinearLayout.LayoutParams(-2, -2).apply { width = resources.displayMetrics.widthPixels * 4 / 5 })
        messages.addView(row)
    }

    private fun renderMessages() {
        messages.removeAllViews()
        if (current.messages.isEmpty()) {
            messages.addView(label("Hallo! Lade Gemma 4 E4B-it in den Einstellungen herunter, lade es in den Arbeitsspeicher und stelle dann deine erste Frage.", 16f).apply {
                setPadding(dp(14), dp(30), dp(14), 0)
            })
        } else {
            current.messages.forEach { bubble(it.text, it.role == "user") }
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun submit() {
        if (busy || modelChanging || !modelLoaded) return
        val question = entry.text.toString().trim()
        if (question.isEmpty()) return
        entry.text.clear()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(entry.windowToken, 0)
        val target = current
        busy = true
        send.isEnabled = false
        messages.removeAllViews()
        target.messages.forEach { bubble(it.text, it.role == "user") }
        bubble(question, true)
        bubble("Gemma antwortet …", false)
        status.text = "Antwort wird lokal erzeugt …"
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        worker.execute {
            try {
                val chatConversation = ensureConversation(target)
                val answer = chatConversation.sendMessage(question).toString().trim()
                if (answer.isBlank()) error("Das Modell hat keine Textantwort erzeugt.")
                target.messages += ChatMessage("user", question)
                target.messages += ChatMessage("model", answer)
                if (target.title == "Neuer Chat") target.title = question.take(42)
                store.save()
                runOnUiThread {
                    busy = false
                    if (!settingsOpen && current.id == target.id) {
                        showChat()
                    }
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    busy = false
                    if (!settingsOpen && current.id == target.id) {
                        renderMessages()
                        status.text = "Fehler: ${error.localizedMessage ?: error.javaClass.simpleName}"
                        send.isEnabled = modelLoaded
                    }
                }
            }
        }
    }

    private fun ensureConversation(chat: Chat): Conversation {
        check(modelLoaded && engine != null) { "Modell zuerst in den Einstellungen laden." }
        if (conversationChatId != chat.id || conversation == null) {
            conversation?.close()
            val history = chat.messages.map {
                if (it.role == "user") Message.user(it.text) else Message.model(it.text)
            }
            conversation = engine!!.createConversation(ConversationConfig(initialMessages = history))
            conversationChatId = chat.id
        }
        return conversation!!
    }

    private fun loadModel() {
        if (!model.ready) {
            runtimeStatus.text = "Modell zuerst herunterladen."
            return
        }
        if (busy || modelChanging || modelLoaded) return
        modelChanging = true
        refreshRuntimeControls("Modell wird in den Arbeitsspeicher geladen …")
        worker.execute {
            try {
                val created = Engine(EngineConfig(
                    modelPath = model.file.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = cacheDir.absolutePath
                ))
                try { created.initialize() } catch (error: Throwable) { created.close(); throw error }
                engine = created
                modelLoaded = true
                runOnUiThread {
                    modelChanging = false
                    if (settingsOpen) refreshRuntimeControls()
                    else showChat()
                }
            } catch (error: Throwable) {
                modelLoaded = false
                runOnUiThread {
                    modelChanging = false
                    if (settingsOpen) refreshRuntimeControls("Laden fehlgeschlagen: ${error.localizedMessage ?: error.javaClass.simpleName}")
                    else showChat()
                }
            }
        }
    }

    private fun unloadModel() {
        if (busy || modelChanging || !modelLoaded) return
        modelChanging = true
        refreshRuntimeControls("Modell wird entladen …")
        worker.execute {
            try {
                conversation?.close()
                conversation = null
                conversationChatId = null
                engine?.close()
                engine = null
                modelLoaded = false
                runOnUiThread {
                    modelChanging = false
                    if (settingsOpen) refreshRuntimeControls()
                    else showChat()
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    modelChanging = false
                    if (settingsOpen) refreshRuntimeControls("Entladen fehlgeschlagen: ${error.localizedMessage ?: error.javaClass.simpleName}")
                }
            }
        }
    }

    private fun refreshRuntimeControls(message: String? = null) {
        if (!settingsOpen) return
        runtimeStatus.text = message ?: if (modelLoaded) "Modell ist geladen." else "Modell ist entladen. Die Datei bleibt auf dem Gerät."
        loadButton.isEnabled = model.ready && !modelLoaded && !modelChanging && !busy
        unloadButton.isEnabled = modelLoaded && !modelChanging && !busy
    }

    private fun showChats() {
        val names = store.chats.map { it.title }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Chats auf diesem Gerät")
            .setItems(names) { _, which ->
                if (!busy) {
                    current = store.chats[which]
                    showChat()
                }
            }
            .setPositiveButton("Neuer Chat") { _, _ ->
                if (!busy) { current = store.newChat(); showChat() }
            }
            .setNeutralButton("Aktuellen löschen") { _, _ -> confirmDeleteChat() }
            .setNegativeButton("Schließen", null).show()
    }

    private fun confirmDeleteChat() {
        if (busy) return
        AlertDialog.Builder(this).setTitle("Chat löschen?")
            .setMessage("Dieser Chat wird nur auf diesem Gerät gelöscht.")
            .setPositiveButton("Löschen") { _, _ ->
                store.delete(current)
                current = store.chats.firstOrNull() ?: store.newChat()
                showChat()
            }
            .setNegativeButton("Abbrechen", null).show()
    }

    private fun showSettings() {
        settingsOpen = true
        base()
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(page) }, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(button("← Zurück") { showChat() })
        page.addView(label("Einstellungen", 24f, true).apply { setPadding(dp(4), dp(12), 0, dp(16)) })
        page.addView(label("Modell: Gemma 4 E4B-it", 18f, true))
        page.addView(label("Download: ca. 3,66 GB. Für die Ausführung sind zusätzlich mehrere GB Arbeitsspeicher nötig. Die Geschwindigkeit hängt vom Gerät ab.", 14f).apply { setPadding(0, dp(8), 0, dp(12)) })
        status = label("", 15f)
        page.addView(status)
        page.addView(button("Modell herunterladen") {
            val message = model.start()
            if (message != null) status.text = message else updateDownloadStatus()
        })
        runtimeStatus = label("", 15f).apply { setPadding(0, dp(16), 0, dp(4)) }
        page.addView(runtimeStatus)
        loadButton = button("Modell laden") { loadModel() }
        page.addView(loadButton)
        unloadButton = button("Modell entladen") { unloadModel() }
        page.addView(unloadButton)
        page.addView(button("Modell löschen") {
            if (busy || modelChanging) {
                runtimeStatus.text = "Bitte warten, bis der aktuelle Vorgang beendet ist."
                return@button
            }
            AlertDialog.Builder(this).setTitle("Modell löschen?")
                .setPositiveButton("Löschen") { _, _ ->
                    modelChanging = true
                    refreshRuntimeControls("Modell wird gelöscht …")
                    worker.execute {
                        conversation?.close(); conversation = null; conversationChatId = null
                        engine?.close(); engine = null
                        modelLoaded = false
                        model.delete()
                        runOnUiThread {
                            modelChanging = false
                            updateDownloadStatus()
                            refreshRuntimeControls()
                        }
                    }
                }
                .setNegativeButton("Abbrechen", null).show()
        })
        page.addView(label("Datenschutz", 18f, true).apply { setPadding(0, dp(24), 0, dp(8)) })
        page.addView(label("Fragen, Antworten und Chats bleiben im privaten App-Speicher. Es gibt keine Anmeldung, Analyse oder Telemetrie. Nur beim ausdrücklichen Modell-Download verbindet sich Android mit Hugging Face.", 14f))
        updateDownloadStatus()
        refreshRuntimeControls()
    }

    private fun updateDownloadStatus() {
        if (!settingsOpen) return
        if (model.ready) {
            status.text = "Modell installiert und geprüft. Offline bereit."
            return
        }
        val progress = model.status()
        status.text = when (progress?.status) {
            DownloadManager.STATUS_PENDING -> "Download wartet …"
            DownloadManager.STATUS_RUNNING -> {
                val total = if (progress.total > 0) progress.total else ModelDownload.MODEL_BYTES
                "Download: ${progress.bytes / 1_000_000} / ${total / 1_000_000} MB"
            }
            DownloadManager.STATUS_PAUSED -> "Download pausiert (Code ${progress.reason})."
            DownloadManager.STATUS_FAILED -> "Download fehlgeschlagen (Code ${progress.reason}). Erneut versuchen."
            DownloadManager.STATUS_SUCCESSFUL -> "Datei wird geprüft …"
            else -> "Modell noch nicht heruntergeladen."
        }
        if (progress?.status == DownloadManager.STATUS_SUCCESSFUL && !checking) {
            checking = true
            worker.execute {
                val valid = try { model.verify() } catch (_: Exception) { model.delete(); false }
                runOnUiThread {
                    checking = false
                    if (settingsOpen) {
                        status.text = if (valid) "Modell installiert und geprüft. Offline bereit."
                            else "Datei beschädigt oder unvollständig. Bitte erneut herunterladen."
                        refreshRuntimeControls()
                    }
                }
            }
        } else if (progress?.status == DownloadManager.STATUS_RUNNING || progress?.status == DownloadManager.STATUS_PENDING || progress?.status == DownloadManager.STATUS_PAUSED) {
            handler.postDelayed({ updateDownloadStatus() }, 1000)
        }
    }

    override fun onBackPressed() {
        if (settingsOpen) showChat() else super.onBackPressed()
    }

    override fun onDestroy() {
        worker.execute { conversation?.close(); engine?.close() }
        worker.shutdown()
        super.onDestroy()
    }
}
