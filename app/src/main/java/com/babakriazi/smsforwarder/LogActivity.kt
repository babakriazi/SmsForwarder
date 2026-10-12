package com.babakriazi.smsforwarder

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class LogActivity : AppCompatActivity() {

    private lateinit var txtLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarLog)
        toolbar.setNavigationOnClickListener { finish() }

        txtLog = findViewById(R.id.txtLog)
        findViewById<MaterialButton>(R.id.btnCopy).setOnClickListener { copyLog() }
        findViewById<MaterialButton>(R.id.btnClear).setOnClickListener { clearLog() }
        findViewById<MaterialButton>(R.id.btnRefresh).setOnClickListener { refresh() }
        findViewById<MaterialButton>(R.id.btnDump).setOnClickListener {
            AppLog.dumpSettings(this)
            refresh()
            Toast.makeText(this, "تنظیمات به لاگ اضافه شد", Toast.LENGTH_SHORT).show()
        }

        AppLog.i(this, "LogActivity opened")
        refresh()
    }

    private fun refresh() {
        val text = AppLog.getAll(this)
        txtLog.text = if (text.isBlank()) "(لاگ خالی است)" else text
    }

    private fun copyLog() {
        val text = AppLog.getAll(this)
        if (text.isBlank()) {
            Toast.makeText(this, "لاگ خالی است", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("SmsForwarder Log", text))
        Toast.makeText(this, "لاگ کپی شد — برای من بفرست", Toast.LENGTH_LONG).show()
    }

    private fun clearLog() {
        AppLog.clear(this)
        AppLog.i(this, "Log cleared by user")
        refresh()
        Toast.makeText(this, "پاک شد", Toast.LENGTH_SHORT).show()
    }
}
