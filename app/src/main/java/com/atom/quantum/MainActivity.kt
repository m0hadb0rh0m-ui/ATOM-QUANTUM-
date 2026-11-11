package com.atom.quantum

import android.app.Activity
import android.content.Intent
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val models = linkedMapOf(
        "⚛️ ATOM QUANTUM" to "atom",
        "🌌 DeepSeek R1" to "huihui_ai/deepseek-r1-abliterated:8b",
        "💻 Qwen Coder" to "qwen2.5-coder:7b"
    )
    private val prefs by lazy { getSharedPreferences("atom_quantum", MODE_PRIVATE) }
    private var history = JSONArray()
    private lateinit var chatContainer: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var status: TextView
    private lateinit var modelPicker: Spinner
    private val navy = Color.rgb(5, 9, 23)
    private val cyan = Color.rgb(35, 218, 238)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        history = try { JSONArray(prefs.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
        render()
    }

    private fun text(value: String, size: Float = 16f, color: Int = Color.WHITE): TextView =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            setPadding(12, 10, 12, 10)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
            setPadding(14, 12, 14, 12)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("⚛️ ATOM QUANTUM ∞", 21f, cyan).apply { setTypeface(null, Typeface.BOLD) },
            LinearLayout.LayoutParams(0, -2, 1f))
        val settings = Button(this).apply { text = "⚙️"; setOnClickListener { showSettings() } }
        header.addView(settings)
        root.addView(header)
        val fullStudio = Button(this).apply {
            text = "🌌 فتح الاستوديو الكامل (صور • فيديو • 3D)"
            setOnClickListener { startActivity(Intent(this@MainActivity, StudioActivity::class.java)) }
        }
        root.addView(fullStudio)
        val cloudChat = Button(this).apply {
            text = "☁️ محادثة سحابية عبر الإنترنت"
            setOnClickListener { startActivity(Intent(this@MainActivity, CloudChatActivity::class.java)) }
        }
        root.addView(cloudChat)
        modelPicker = Spinner(this)
        val labels = models.keys.toList()
        modelPicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        modelPicker.setSelection(prefs.getInt("model_index", 0).coerceIn(0, labels.lastIndex))
        modelPicker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt("model_index", position).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        root.addView(modelPicker)
        val newChat = Button(this).apply {
            text = "➕ محادثة جديدة"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setMessage("حذف المحادثة المحلية الحالية؟")
                    .setPositiveButton("نعم") { _, _ ->
                        history = JSONArray()
                        persist()
                        render()
                    }.setNegativeButton("إلغاء", null).show()
            }
        }
        root.addView(newChat)
        scroll = ScrollView(this)
        chatContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(chatContainer)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        status = text("جاهز • يتطلب الاتصال بخادم Ollama", 12f, Color.LTGRAY)
        root.addView(status)
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "اكتب رسالتك هنا..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            minLines = 1
            maxLines = 4
            setSingleLine(false)
        }
        bottom.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        send = Button(this).apply { text = "إرسال"; setOnClickListener { submit() } }
        bottom.addView(send)
        root.addView(bottom)
        setContentView(root)
        for (i in 0 until history.length()) {
            val msg = history.optJSONObject(i) ?: continue
            addBubble(msg.optString("role"), msg.optString("content"))
        }
    }

    private fun addBubble(role: String, message: String) {
        val isUser = role == "user"
        val bubble = text((if (isUser) "🧑‍💻 أنت\n" else "⚛️ ATOM\n") + message)
        bubble.setBackgroundColor(if (isUser) Color.rgb(23, 48, 68) else Color.rgb(26, 24, 51))
        val params = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 6, 0, 6) }
        chatContainer.addView(bubble, params)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun persist() { prefs.edit().putString("history", history.toString()).apply() }

    private fun submit() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return
        input.setText("")
        history.put(JSONObject().put("role", "user").put("content", prompt))
        addBubble("user", prompt)
        persist()
        send.isEnabled = false
        status.text = "جارٍ الاتصال بالنموذج..."
        val selectedModel = models.values.elementAt(modelPicker.selectedItemPosition.coerceAtLeast(0))
        val server = prefs.getString("server", "http://localhost:11434")!!.trimEnd('/')
        val payloadMessages = JSONArray().put(JSONObject()
            .put("role", "system")
            .put("content", "أنت ATOM QUANTUM، مساعد ذكاء اصطناعي يجيب بالعربية بدقة ووضوح."))
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            payloadMessages.put(JSONObject().put("role", item.optString("role")).put("content", item.optString("content")))
        }
        thread {
            var connection: HttpURLConnection? = null
            try {
                connection = URL("$server/api/chat").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 15000
                connection.readTimeout = 300000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val payload = JSONObject().put("model", selectedModel).put("stream", false)
                    .put("messages", payloadMessages)
                    .put("options", JSONObject().put("temperature", 0.2).put("top_p", 0.95).put("num_ctx", 16384))
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                    throw IllegalStateException("HTTP $responseCode: $error")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val answer = JSONObject(body).getJSONObject("message").getString("content")
                    .replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
                runOnUiThread {
                    history.put(JSONObject().put("role", "assistant").put("content", answer))
                    persist()
                    addBubble("assistant", answer)
                    status.text = "اكتملت الاستجابة"
                    send.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "تعذر الاتصال: ${e.message}"
                    send.isEnabled = true
                }
            } finally { connection?.disconnect() }
        }
    }

    private fun showSettings() {
        val editor = EditText(this).apply {
            setText(prefs.getString("server", "http://localhost:11434"))
            setSingleLine(true)
            setPadding(28, 25, 28, 25)
        }
        AlertDialog.Builder(this)
            .setTitle("رابط خادم Ollama")
            .setMessage("على الهاتف استخدم عنوان IP لخادم Ollama، وليس localhost. مثال: http://192.168.1.10:11434")
            .setView(editor)
            .setPositiveButton("حفظ") { _, _ ->
                val url = editor.text.toString().trim().trimEnd('/')
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    prefs.edit().putString("server", url).apply()
                    status.text = "تم حفظ رابط الخادم"
                } else Toast.makeText(this, "أدخل رابط http أو https صحيحًا", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
