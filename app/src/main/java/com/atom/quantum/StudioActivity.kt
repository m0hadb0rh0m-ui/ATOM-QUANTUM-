package com.atom.quantum

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.widget.LinearLayout
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class StudioActivity : Activity() {
    private lateinit var web: WebView
    private val prefs by lazy { getSharedPreferences("atom_quantum", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val address = Button(this).apply {
            text = "🔗 عنوان الاستوديو"
            setOnClickListener { editServer() }
        }
        val refresh = Button(this).apply {
            text = "↻ تحديث"
            setOnClickListener { loadStudio() }
        }
        val back = Button(this).apply {
            text = "رجوع"
            setOnClickListener { finish() }
        }
        controls.addView(address, LinearLayout.LayoutParams(0, -2, 2f))
        controls.addView(refresh, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(back, LinearLayout.LayoutParams(0, -2, 1f))
        layout.addView(controls)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                return uri.scheme != "http" && uri.scheme != "https"
            }
        }
        web.webChromeClient = WebChromeClient()
        layout.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        val url = prefs.getString("studio_url", "") ?: ""
        if (url.isBlank()) editServer() else loadStudio()
    }

    private fun loadStudio() {
        val url = prefs.getString("studio_url", "")?.trim().orEmpty()
        if (url.startsWith("https://") || url.startsWith("http://")) {
            web.loadUrl(url)
        } else {
            Toast.makeText(this, "أدخل رابط خادم Streamlit أولاً", Toast.LENGTH_LONG).show()
            editServer()
        }
    }

    private fun editServer() {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(prefs.getString("studio_url", "http://192.168.1.10:8501"))
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("ربط ATOM QUANTUM الأصلي")
            .setMessage("أدخل رابط Streamlit القابل للوصول من الهاتف. مثال داخل الشبكة المحلية: http://192.168.1.10:8501. يجب أن يكون الخادم قيد التشغيل.")
            .setView(input)
            .setPositiveButton("حفظ واتصال") { _, _ ->
                val url = input.text.toString().trim().trimEnd('/')
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    prefs.edit().putString("studio_url", url).apply()
                    loadStudio()
                } else Toast.makeText(this, "الرابط يجب أن يبدأ بـ http أو https", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
