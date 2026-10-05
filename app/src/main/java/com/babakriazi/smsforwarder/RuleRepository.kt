package com.babakriazi.smsforwarder

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class RuleRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sms_forwarder_rules", Context.MODE_PRIVATE)

    fun getAllRules(): MutableList<Rule> {
        val json = prefs.getString("rules", "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<Rule>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)

            // پشتیبانی از فرمت قدیمی (تک شرط) و جدید (چند شرط)
            val bodyConditions = mutableListOf<BodyCondition>()
            if (obj.has("bodyConditions")) {
                val bcArr = obj.getJSONArray("bodyConditions")
                for (j in 0 until bcArr.length()) {
                    val bc = bcArr.getJSONObject(j)
                    bodyConditions.add(
                        BodyCondition(
                            filter = bc.optString("filter", ""),
                            matchType = Rule.MatchType.valueOf(bc.optString("matchType", "CONTAINS")),
                            logicWithPrevious = Rule.LogicType.valueOf(bc.optString("logicWithPrevious", "AND"))
                        )
                    )
                }
            } else {
                // مهاجرت از نسخه قدیمی
                val oldFilter = obj.optString("bodyFilter", "")
                val oldType = Rule.MatchType.valueOf(obj.optString("bodyMatchType", "CONTAINS"))
                bodyConditions.add(BodyCondition(filter = oldFilter, matchType = oldType))
            }
            if (bodyConditions.isEmpty()) {
                bodyConditions.add(BodyCondition())
            }

            list.add(
                Rule(
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    name = obj.optString("name", ""),
                    senderFilter = obj.optString("senderFilter", ""),
                    senderMatchType = Rule.MatchType.valueOf(
                        obj.optString("senderMatchType", "CONTAINS")
                    ),
                    bodyConditions = bodyConditions,
                    logic = Rule.LogicType.valueOf(obj.optString("logic", "AND")),
                    forwardTo = obj.optString("forwardTo", ""),
                    simSlot = obj.optInt("simSlot", -1),
                    enabled = obj.optBoolean("enabled", true)
                )
            )
        }
        return list
    }

    fun saveRules(rules: List<Rule>) {
        val array = JSONArray()
        rules.forEach { rule ->
            val bcArr = JSONArray()
            rule.bodyConditions.forEach { bc ->
                bcArr.put(JSONObject().apply {
                    put("filter", bc.filter)
                    put("matchType", bc.matchType.name)
                    put("logicWithPrevious", bc.logicWithPrevious.name)
                })
            }
            val obj = JSONObject().apply {
                put("id", rule.id)
                put("name", rule.name)
                put("senderFilter", rule.senderFilter)
                put("senderMatchType", rule.senderMatchType.name)
                put("bodyConditions", bcArr)
                put("logic", rule.logic.name)
                put("forwardTo", rule.forwardTo)
                put("simSlot", rule.simSlot)
                put("enabled", rule.enabled)
            }
            array.put(obj)
        }
        prefs.edit().putString("rules", array.toString()).apply()
    }

    fun addOrUpdate(rule: Rule) {
        val rules = getAllRules()
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            rules[index] = rule
        } else {
            rules.add(rule)
        }
        saveRules(rules)
    }

    fun delete(ruleId: String) {
        val rules = getAllRules().filter { it.id != ruleId }
        saveRules(rules)
    }
}
