package com.babakriazi.smsforwarder

import java.util.UUID

data class BodyCondition(
    var filter: String = "",
    var matchType: Rule.MatchType = Rule.MatchType.CONTAINS,
    /** منطق نسبت به شرط قبلی: برای اولین شرط نادیده گرفته می‌شود */
    var logicWithPrevious: Rule.LogicType = Rule.LogicType.AND
)

data class Rule(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var senderFilter: String = "",
    var senderMatchType: MatchType = MatchType.CONTAINS,
    /** لیست شرط‌های محتوا – می‌توان چند تا با AND/OR داشت */
    var bodyConditions: MutableList<BodyCondition> = mutableListOf(BodyCondition()),
    /** منطق بین شرط فرستنده و کل گروه شرط‌های محتوا */
    var logic: LogicType = LogicType.AND,
    var forwardTo: String = "",
    var simSlot: Int = -1,
    var enabled: Boolean = true
) {
    enum class MatchType {
        EXACT, CONTAINS, STARTS_WITH, ENDS_WITH
    }

    enum class LogicType {
        AND, OR
    }

    private fun matchOne(text: String, filter: String, type: MatchType): Boolean {
        if (filter.isBlank()) return true
        return when (type) {
            MatchType.EXACT -> text.equals(filter, ignoreCase = true)
            MatchType.CONTAINS -> text.contains(filter, ignoreCase = true)
            MatchType.STARTS_WITH -> text.startsWith(filter, ignoreCase = true)
            MatchType.ENDS_WITH -> text.endsWith(filter, ignoreCase = true)
        }
    }

    fun matches(sender: String, body: String): Boolean {
        val senderOk = matchOne(sender, senderFilter, senderMatchType)

        // ارزیابی شرط‌های محتوا به ترتیب با منطق بین آن‌ها
        val activeConditions = bodyConditions.filter { it.filter.isNotBlank() }
        val bodyOk = if (activeConditions.isEmpty()) {
            true
        } else {
            var result = matchOne(body, activeConditions[0].filter, activeConditions[0].matchType)
            for (i in 1 until activeConditions.size) {
                val cond = activeConditions[i]
                val thisOk = matchOne(body, cond.filter, cond.matchType)
                result = when (cond.logicWithPrevious) {
                    LogicType.AND -> result && thisOk
                    LogicType.OR -> result || thisOk
                }
            }
            result
        }

        return when (logic) {
            LogicType.AND -> senderOk && bodyOk
            LogicType.OR -> senderOk || bodyOk
        }
    }

    /** توضیح خوانا برای نمایش در لیست */
    fun bodySummary(): String {
        val active = bodyConditions.filter { it.filter.isNotBlank() }
        if (active.isEmpty()) return "هر محتوا"
        return active.mapIndexed { index, c ->
            val logic = if (index == 0) "" else when (c.logicWithPrevious) {
                LogicType.AND -> " و "
                LogicType.OR -> " یا "
            }
            val typeFa = when (c.matchType) {
                MatchType.EXACT -> "دقیقاً"
                MatchType.CONTAINS -> "شامل"
                MatchType.STARTS_WITH -> "شروع با"
                MatchType.ENDS_WITH -> "پایان با"
            }
            "$logic$typeFa «${c.filter}»"
        }.joinToString("")
    }
}
