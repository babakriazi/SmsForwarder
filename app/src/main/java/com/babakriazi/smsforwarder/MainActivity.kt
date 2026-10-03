package com.babakriazi.smsforwarder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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
            checkBatteryOptimization()
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("بهینه‌سازی باتری")
                    .setMessage("برای جلوگیری از بسته شدن برنامه در پس‌زمینه، لطفاً بهینه‌سازی باتری را برای این برنامه غیرفعال کنید.")
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

    private fun loadRules() {
        val rules = repo.getAllRules()
        adapter.submitList(rules)
        emptyText.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
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

        // Setup spinners
        val matchTypes = arrayOf("شامل باشد", "دقیقاً برابر", "شروع شود با", "پایان یابد با")
        val matchAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, matchTypes)
        matchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spSenderType.adapter = matchAdapter
        spBodyType.adapter = matchAdapter

        val logicTypes = arrayOf("AND (هر دو شرط)", "OR (یکی از شرط‌ها)")
        val logicAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, logicTypes)
        logicAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spLogic.adapter = logicAdapter

        val simTypes = arrayOf("پیش‌فرض سیستم", "سیم‌کارت ۱", "سیم‌کارت ۲")
        val simAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, simTypes)
        simAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spSim.adapter = simAdapter

        if (existing != null) {
            edtName.setText(existing.name)
            edtSender.setText(existing.senderFilter)
            spSenderType.setSelection(existing.senderMatchType.ordinal)
            edtBody.setText(existing.bodyFilter)
            spBodyType.setSelection(existing.bodyMatchType.ordinal)
            spLogic.setSelection(existing.logic.ordinal)
            // simSlot: -1 → 0, 0 → 1, 1 → 2
            spSim.setSelection(when (existing.simSlot) {
                0 -> 1
                1 -> 2
                else -> 0
            })
            edtForwardTo.setText(existing.forwardTo)
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "قانون جدید" else "ویرایش قانون")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ ->
                val rule = existing ?: Rule()
                rule.name = edtName.text.toString().trim()
                rule.senderFilter = edtSender.text.toString().trim()
                rule.senderMatchType = Rule.MatchType.values()[spSenderType.selectedItemPosition]
                rule.bodyFilter = edtBody.text.toString().trim()
                rule.bodyMatchType = Rule.MatchType.values()[spBodyType.selectedItemPosition]
                rule.logic = Rule.LogicType.values()[spLogic.selectedItemPosition]
                rule.simSlot = when (spSim.selectedItemPosition) {
                    1 -> 0   // سیم‌کارت ۱
                    2 -> 1   // سیم‌کارت ۲
                    else -> -1
                }
                rule.forwardTo = edtForwardTo.text.toString().trim()

                if (rule.forwardTo.isBlank()) {
                    Toast.makeText(this, "شماره مقصد الزامی است", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                repo.addOrUpdate(rule)
                loadRules()
                Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    // Simple Adapter
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
            private val switchEnabled: Switch = itemView.findViewById(R.id.switchEnabled)
            private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
            private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

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
