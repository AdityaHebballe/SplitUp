package com.aditya.splitup.domain

import com.aditya.splitup.data.model.Expense
import com.aditya.splitup.data.model.ExpenseSplit
import com.aditya.splitup.data.model.Member
import com.aditya.splitup.data.model.Payment
import kotlin.math.absoluteValue
import kotlin.math.min

data class MemberBalance(
    val memberId: Long,
    val memberName: String,
    val totalPaid: Double,
    val totalOwed: Double,
    val netBalance: Double
)

data class Settlement(
    val fromMemberId: Long,
    val fromName: String,
    val toMemberId: Long,
    val toName: String,
    val amount: Double,
    val currency: String
)

class BalanceCalculator {

    fun calculateMemberBalances(
        members: List<Member>,
        expenses: List<Expense>,
        splits: Map<Long, List<ExpenseSplit>>,
        payments: List<Payment>,
        rates: Map<Pair<String, String>, Double>,
        groupCurrency: String
    ): List<MemberBalance> {
        val paidMap = mutableMapOf<Long, Double>()
        val owedMap = mutableMapOf<Long, Double>()

        // Initialize maps
        members.forEach {
            paidMap[it.id] = 0.0
            owedMap[it.id] = 0.0
        }

        // Process expenses
        for (expense in expenses) {
            val rate = if (expense.currency == groupCurrency) 1.0 else rates[expense.currency to groupCurrency] ?: 1.0
            val amountInGroupCurrency = expense.amount * rate

            // Add to payer's total paid
            paidMap[expense.paidByMemberId] = (paidMap[expense.paidByMemberId] ?: 0.0) + amountInGroupCurrency

            val expenseSplits = splits[expense.id] ?: emptyList()
            val totalRatios = expenseSplits.sumOf { it.ratioPart }.toDouble()

            if (totalRatios > 0) {
                for (split in expenseSplits) {
                    val share = (split.ratioPart / totalRatios) * amountInGroupCurrency
                    owedMap[split.memberId] = (owedMap[split.memberId] ?: 0.0) + share
                }
            }
        }

        // Process payments
        for (payment in payments) {
            val rate = if (payment.currency == groupCurrency) 1.0 else rates[payment.currency to groupCurrency] ?: 1.0
            val amountInGroupCurrency = payment.amount * rate

            paidMap[payment.fromMemberId] = (paidMap[payment.fromMemberId] ?: 0.0) + amountInGroupCurrency
            owedMap[payment.toMemberId] = (owedMap[payment.toMemberId] ?: 0.0) + amountInGroupCurrency
        }

        return members.map { member ->
            val paid = paidMap[member.id] ?: 0.0
            val owed = owedMap[member.id] ?: 0.0
            MemberBalance(
                memberId = member.id,
                memberName = member.name,
                totalPaid = paid,
                totalOwed = owed,
                netBalance = paid - owed
            )
        }
    }

    fun calculateSettlements(
        members: List<Member>,
        expenses: List<Expense>,
        splits: Map<Long, List<ExpenseSplit>>,
        payments: List<Payment>,
        rates: Map<Pair<String, String>, Double>,
        groupCurrency: String
    ): List<Settlement> {
        val balances = calculateMemberBalances(members, expenses, splits, payments, rates, groupCurrency)
        
        // Greedy min-cash-flow
        val debtors = balances.filter { it.netBalance < -0.01 }.sortedBy { it.netBalance }.toMutableList() // Most negative first
        val creditors = balances.filter { it.netBalance > 0.01 }.sortedByDescending { it.netBalance }.toMutableList() // Most positive first

        val settlements = mutableListOf<Settlement>()
        
        var i = 0
        var j = 0
        
        val debtAmounts = debtors.map { it.netBalance.absoluteValue }.toMutableList()
        val creditAmounts = creditors.map { it.netBalance }.toMutableList()

        while (i < debtors.size && j < creditors.size) {
            val debtor = debtors[i]
            val creditor = creditors[j]
            
            val debt = debtAmounts[i]
            val credit = creditAmounts[j]
            
            val settledAmount = min(debt, credit)
            
            settlements.add(
                Settlement(
                    fromMemberId = debtor.memberId,
                    fromName = debtor.memberName,
                    toMemberId = creditor.memberId,
                    toName = creditor.memberName,
                    amount = settledAmount,
                    currency = groupCurrency
                )
            )
            
            debtAmounts[i] -= settledAmount
            creditAmounts[j] -= settledAmount
            
            if (debtAmounts[i] < 0.01) i++
            if (creditAmounts[j] < 0.01) j++
        }
        
        return settlements
    }
}
