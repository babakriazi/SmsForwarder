package com.babakriazi.smsforwarder

import java.util.UUID

data class Rule(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var senderFilter: String = "",
    var senderMatchType: MatchType = MatchType.CONTAINS,
    var bodyFilter: String = "",
    var bodyMatchType: MatchType = MatchType.CONTAINS,
    var logic: LogicType = LogicType.AND,
    var forwardTo: String = "",
    var simSlot: Int = -1,   // -1 = پیش‌فرض سیستم، 0 = سیم‌کارت ۱، 1 = سیم‌کارت ۲
    var enabled: Boolean = true
) {
    enum class MatchType {
        EXACT, CONTAINS, STARTS_WITH, ENDS_WITH
    }

    enum class LogicType {
        AND, OR
    }

    fun matches(sender: String, body: String): Boolean {
        val senderOk = if (senderFilter.isBlank()) {
            true
        } else {
            when (senderMatchType) {
                MatchType.EXACT -> sender.equals(senderFilter, ignoreCase = true)
                MatchType.CONTAINS -> sender.contains(senderFilter, ignoreCase = true)
                MatchType.STARTS_WITH -> sender.startsWith(senderFilter, ignoreCase = true)
                MatchType.ENDS_WITH -> sender.endsWith(senderFilter, ignoreCase = true)
            }
        }

        val bodyOk = if (bodyFilter.isBlank()) {
            true
        } else {
            when (bodyMatchType) {
                MatchType.EXACT -> body.equals(bodyFilter, ignoreCase = true)
                MatchType.CONTAINS -> body.contains(bodyFilter, ignoreCase = true)
                MatchType.STARTS_WITH -> body.startsWith(bodyFilter, ignoreCase = true)
                MatchType.ENDS_WITH -> body.endsWith(bodyFilter, ignoreCase = true)
            }
        }

        return when (logic) {
            LogicType.AND -> senderOk && bodyOk
            LogicType.OR -> senderOk || bodyOk
        }
    }
}
