package com.babakriazi.smsforwarder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.provider.Telephony
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var repo: RuleRepository
    private lateinit var adapter: RuleAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "برای کار کردن برنامه، همه مجوزها لازم است", Toast.LENGTH_LONG).show()
        } else {
            KeepAliveService.start(this)
            checkMissedSms()
        }
        checkBatteryOptimization()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = RuleRepository(this)

        recyclerView = findViewById(R.id.recyclerRules)
        emptyText = findViewById(R.id.txtEmpty)
        val fab = findViewById<FloatingActionButton>(R.id.fabAdd)

        adapter = RuleAdapter(
            onEdit = { rule -> showRuleDialog(rule) },
            onDelete = { rule ->
                AlertDialog.Builder(this)
                    .setTitle("حذف قانون")
                    .setMessage("آیا مطمئن هستید؟")
                    .setPositiveButton("بله") { _, _ ->
                        repo.delete(rule.id)
                        loadRules()
                    }
                    .setNegativeButton("خیر", null)
                    .show()
            },
            onToggle = { rule, enabled ->
                rule.enabled = enabled
                repo.addOrUpdate(rule)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fab.setOnClickListener { showRuleDialog(null) }

        requestPermissions()
        loadRules()
    }

    override fun onResume() {
        super.onResume()
        // Ensure service is running
        if (hasSmsPermissions()) {
            KeepAliveService.start(this)
        }
    }

    private fun hasSmsPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needRequest) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            KeepAliveService.start(this)
            checkBatteryOptimization()
            checkMissedSms()
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("بهینه‌سازی باتری")
                    .setMessage("برای جلوگیری از بسته شدن برنامه، لطفاً بهینه‌سازی باتری را غیرفعال کنید. این کار برای پیامک‌های حسابداری خیلی مهم است.")
                    .setPositiveButton("تنظیمات") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
                    .setNegativeButton("بعداً", null)
                    .show()
            }
        }
    }

    /** Check SMS inbox for messages received in the last 24 hours that match rules but may have been missed */
    private fun checkMissedSms() {
        if (!hasSmsPermissions()) return

        val rules = repo.getAllRules().filter { it.enabled && it.forwardTo.isNotBlank() }
        if (rules.isEmpty()) return

        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L // last 24h
        val missed = mutableListOf<Triple<String, String, Long>>() // sender, body, date

        try {
            val cursor: Cursor? = contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} > ?",
                arrayOf(cutoff.toString()),
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val addrIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)

                while (it.moveToNext()) {
                    val sender = it.getString(addrIdx) ?: continue
                    val body = it.getString(bodyIdx) ?: continue
                    val date = it.getLong(dateIdx)

                    for (rule in rules) {
                        if (rule.matches(sender, body)) {
                            missed.add(Triple(sender, body, date))
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        if (missed.isEmpty()) return

        // Show confirmation dialog
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("fa"))
        val message = StringBuilder()
        message.append("${missed.size} پیامک مطابق قوانین پیدا شد که ممکن است هنگام بسته بودن برنامه از دست رفته باشد:\n\n")
        missed.take(8).forEach { (sender, body, date) ->
            message.append("• از $sender\n  ${body.take(60)}${if (body.length > 60) "..." else ""}\n  ${sdf.format(Date(date))}\n\n")
        }
        if (missed.size > 8) message.append("... و ${missed.size - 8} مورد دیگر\n\n")
        message.append("آیا می‌خواهید آن‌ها را الان فوروارد کنید؟")

        AlertDialog.Builder(this)
            .setTitle("پیامک‌های احتمالی از دست رفته")
            .setMessage(message.toString())
            .setPositiveButton("بله، فوروارد کن") { _, _ ->
                forwardMissed(missed, rules)
            }
            .setNegativeButton("خیر", null)
            .show()
    }

    private fun forwardMissed(missed: List<Triple<String, String, Long>>, rules: List<Rule>) {
        var count = 0
        for ((sender, body, _) in missed) {
            for (rule in rules) {
                if (rule.matches(sender, body)) {
                    try {
                        // Reuse logic from SmsReceiver
                        val intent = Intent(this, SmsReceiver::class.java)
                        // Call forward directly via a helper - simplest: create temp instance logic
                        forwardOne(rule.forwardTo, sender, body, rule.simSlot)
                        count++
                    } catch (_: Exception) {}
                    break
                }
            }
        }
        Toast.makeText(this, "$count پیامک فوروارد شد", Toast.LENGTH_LONG).show()
    }

    private fun forwardOne(to: String, originalSender: String, body: String, simSlot: Int) {
        try {
            val smsManager = if (simSlot >= 0) {
                try {
                    val subMgr = getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as android.telephony.SubscriptionManager
                    val list = subMgr.activeSubscriptionInfoList
                    if (list != null && list.size > simSlot) {
                        android.telephony.SmsManager.getSmsManagerForSubscriptionId(list[simSlot].subscriptionId)
                    } else {
                        getDefaultSmsManager()
                    }
                } catch (_: Exception) {
                    getDefaultSmsManager()
                }
            } else {
                getDefaultSmsManager()
            }

            val message = "از: $originalSender\n\n$body"
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(to, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(to, null, parts, null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getDefaultSmsManager(): android.telephony.SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(android.telephony.SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            android.telephony.SmsManager.getDefault()
        }
    }

    private fun loadRules() {
        val rules = repo.getAllRules()
        adapter.submitList(rules)
        emptyText.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (rules.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showRuleDialog(existing: Rule?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_rule, null)

        val edtName = dialogView.findViewById<EditText>(R.id.edtName)
        val edtSender = dialogView.findViewById<EditText>(R.id.edtSender)
        val spSenderType = dialogView.findViewById<Spinner>(R.id.spSenderType)
        val edtBody = dialogView.findViewById<EditText>(R.id.edtBody)
        val spBodyType = dialogView.findViewById<Spinner>(R.id.spBodyType)
        val spLogic = dialogView.findViewById<Spinner>(R.id.spLogic)
        val spSim = dialogView.findViewById<Spinner>(R.id.spSim)
        val edtForwardTo = dialogView.findViewById<EditText>(R.id.edtForwardTo)

        val matchTypes = arrayOf("شامل باشد", "دقیقاً برابر", "شروع شود با", "پایان یابد با")
        val matchAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, matchTypes)
        spSenderType.adapter = matchAdapter
        spBodyType.adapter = matchAdapter

        val logicTypes = arrayOf("AND (هر دو شرط)", "OR (یکی از شرط‌ها)")
        spLogic.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, logicTypes)

        val simTypes = arrayOf("پیش‌فرض سیستم", "سیم‌کارت ۱", "سیم‌کارت ۲")
        spSim.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, simTypes)

        if (existing != null) {
            edtName.setText(existing.name)
            edtSender.setText(existing.senderFilter)
            spSenderType.setSelection(existing.senderMatchType.ordinal)
            edtBody.setText(existing.bodyFilter)
            spBodyType.setSelection(existing.bodyMatchType.ordinal)
            spLogic.setSelection(existing.logic.ordinal)
            spSim.setSelection(when (existing.simSlot) {
                0 -> 1
                1 -> 2
                else -> 0
            })
            edtForwardTo.setText(existing.forwardTo)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "قانون جدید" else "ویرایش قانون")
            .setView(dialogView)
            .setPositiveButton("ذخیره", null)
            .setNegativeButton("لغو", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rule = existing ?: Rule()
                rule.name = edtName.text.toString().trim()
                rule.senderFilter = edtSender.text.toString().trim()
                rule.senderMatchType = Rule.MatchType.values()[spSenderType.selectedItemPosition]
                rule.bodyFilter = edtBody.text.toString().trim()
                rule.bodyMatchType = Rule.MatchType.values()[spBodyType.selectedItemPosition]
                rule.logic = Rule.LogicType.values()[spLogic.selectedItemPosition]
                rule.simSlot = when (spSim.selectedItemPosition) {
                    1 -> 0
                    2 -> 1
                    else -> -1
                }
                rule.forwardTo = edtForwardTo.text.toString().trim()

                if (rule.forwardTo.isBlank()) {
                    Toast.makeText(this, "شماره مقصد الزامی است", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                repo.addOrUpdate(rule)
                loadRules()
                Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    inner class RuleAdapter(
        private val onEdit: (Rule) -> Unit,
        private val onDelete: (Rule) -> Unit,
        private val onToggle: (Rule, Boolean) -> Unit
    ) : RecyclerView.Adapter<RuleAdapter.ViewHolder>() {

        private var items = listOf<Rule>()

        fun submitList(list: List<Rule>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_rule, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val txtTitle: TextView = itemView.findViewById(R.id.txtTitle)
            private val txtDetails: TextView = itemView.findViewById(R.id.txtDetails)
            private val switchEnabled: MaterialSwitch = itemView.findViewById(R.id.switchEnabled)
            private val btnEdit: View = itemView.findViewById(R.id.btnEdit)
            private val btnDelete: View = itemView.findViewById(R.id.btnDelete)

            fun bind(rule: Rule) {
                txtTitle.text = if (rule.name.isNotBlank()) rule.name else "قانون بدون نام"

                val senderPart = if (rule.senderFilter.isBlank()) "هر فرستنده" else
                    "فرستنده ${matchTypeFa(rule.senderMatchType)} «${rule.senderFilter}»"

                val bodyPart = if (rule.bodyFilter.isBlank()) "هر محتوا" else
                    "محتوا ${matchTypeFa(rule.bodyMatchType)} «${rule.bodyFilter}»"

                val logicFa = if (rule.logic == Rule.LogicType.AND) "و" else "یا"
                val simFa = when (rule.simSlot) {
                    0 -> "سیم ۱"
                    1 -> "سیم ۲"
                    else -> "پیش‌فرض"
                }

                txtDetails.text = "$senderPart $logicFa $bodyPart\n→ ${rule.forwardTo}  ($simFa)"

                switchEnabled.setOnCheckedChangeListener(null)
                switchEnabled.isChecked = rule.enabled
                switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                    onToggle(rule, isChecked)
                }

                btnEdit.setOnClickListener { onEdit(rule) }
                btnDelete.setOnClickListener { onDelete(rule) }
            }

            private fun matchTypeFa(type: Rule.MatchType): String = when (type) {
                Rule.MatchType.EXACT -> "دقیقاً"
                Rule.MatchType.CONTAINS -> "شامل"
                Rule.MatchType.STARTS_WITH -> "شروع با"
                Rule.MatchType.ENDS_WITH -> "پایان با"
            }
        }
    }
}
