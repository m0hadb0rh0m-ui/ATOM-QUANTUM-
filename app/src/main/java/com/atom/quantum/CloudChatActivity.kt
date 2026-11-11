package com.atom.quantum

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class CloudChatActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("atom_quantum_cloud", MODE_PRIVATE) }
    private var history = JSONArray()
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var entry: EditText
    private lateinit var send: Button
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        history = try { JSONArray(prefs.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
        draw()
    }

    private fun label(s: String, size: Float = 16f): TextView = TextView(this).apply {
        text = s; textSize = size; setTextColor(Color.WHITE); setPadding(14, 12, 14, 12)
    }

    private fun draw() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 10)
            setBackgroundColor(Color.rgb(6, 10, 25))
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(label("☁️ ATOM CLOUD", 21f), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(Button(this).apply { text = "⚙️"; setOnClickListener { settings() } })
        head.addView(Button(this).apply {
            text = "جديد"
            setOnClickListener {
                AlertDialog.Builder(this@CloudChatActivity).setMessage("مسح سجل المحادثة السحابية المحلي؟")
                    .setPositiveButton("مسح") { _, _ ->
                        history = JSONArray(); persist(); draw()
                    }.setNegativeButton("إلغاء", null).show()
            }
        })
        root.addView(head)
        scroll = ScrollView(this)
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        status = label("اضبط عنوان API واسم النموذج ومفتاح الاتصال من ⚙️", 12f)
        root.addView(status)
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        entry = EditText(this).apply {
            hint = "اكتب رسالتك..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            maxLines = 4
        }
        bar.addView(entry, LinearLayout.LayoutParams(0, -2, 1f))
        send = Button(this).apply { text = "إرسال"; setOnClickListener { ask() } }
        bar.addView(send)
        root.addView(bar)
        setContentView(root)
        for (i in 0 until history.length()) {
            val m = history.optJSONObject(i) ?: continue
            bubble(m.optString("role"), m.optString("content"))
        }
    }

    private fun bubble(role: String, content: String) {
        val b = label((if (role == "user") "🧑‍💻 أنت\n" else "☁️ ATOM\n") + content)
        b.setBackgroundColor(if (role == "user") Color.rgb(22, 47, 65) else Color.rgb(33, 28, 57))
        val lp = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 5, 0, 5) }
        messages.addView(b, lp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun persist() { prefs.edit().putString("history", history.toString()).apply() }

    private fun ask() {
        val message = entry.text.toString().trim()
        if (message.isEmpty()) return
        val endpoint = prefs.getString("endpoint", "")?.trim().orEmpty()
        val model = prefs.getString("model", "")?.trim().orEmpty()
        val apiKey = prefs.getString("api_key", "")?.trim().orEmpty()
        if (!endpoint.startsWith("https://") || model.isBlank()) {
            status.text = "أدخل رابط HTTPS واسم النموذج في الإعدادات"
            settings()
            return
        }
        entry.setText("")
        history.put(JSONObject().put("role", "user").put("content", message))
        persist()
        bubble("user", message)
        send.isEnabled = false
        status.text = "جارٍ إرسال الطلب السحابي..."
        val payloadHistory = JSONArray().put(JSONObject()
            .put("role", "system")
            .put("content", "أنت ATOM QUANTUM، مساعد عربي دقيق وواضح."))
        for (i in 0 until history.length()) {
            val m = history.optJSONObject(i) ?: continue
            payloadHistory.put(JSONObject().put("role", m.optString("role")).put("content", m.optString("content")))
        }
        thread {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(endpoint).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 20000
                conn.readTimeout = 120000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")
                val payload = JSONObject()
                    .put("model", model)
                    .put("messages", payloadHistory)
                    .put("stream", false)
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val httpCode = conn.responseCode
                if (httpCode !in 200..299) {
                    val detail = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $httpCode"
                    throw IllegalStateException("HTTP $httpCode: ${detail.take(350)}")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val answer = JSONObject(body).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content")
                runOnUiThread {
                    history.put(JSONObject().put("role", "assistant").put("content", answer))
                    persist()
                    bubble("assistant", answer)
                    status.text = "اكتملت الاستجابة"
                    send.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "فشل الاتصال: ${e.message}"
                    send.isEnabled = true
                }
            } finally { conn?.disconnect() }
        }
    }

    private fun settings() {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 8)
        }
        fun field(hintText: String, value: String): EditText = EditText(this).apply {
            hint = hintText
            setText(value)
            setSingleLine(true)
            wrapper.addView(this)
        }
        val endpoint = field("HTTPS API endpoint (/v1/chat/completions)", prefs.getString("endpoint", "") ?: "")
        val model = field("Model ID", prefs.getString("model", "") ?: "")
        val key = field("API key (optional for authenticated proxy)", prefs.getString("api_key", "") ?: "")
        key.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        AlertDialog.Builder(this)
            .setTitle("إعدادات الاتصال السحابي")
            .setMessage("استخدم مزود API متوافقًا مع OpenAI. لا تضع مفتاحًا حساسًا في هاتف مشترك؛ يفضّل خادم وسيط آمن.")
            .setView(wrapper)
            .setPositiveButton("حفظ") { _, _ ->
                prefs.edit().putString("endpoint", endpoint.text.toString().trim())
                    .putString("model", model.text.toString().trim())
                    .putString("api_key", key.text.toString().trim()).apply()
                status.text = "تم حفظ إعدادات الخدمة"
            }.setNegativeButton("إلغاء", null).show()
    }
}
