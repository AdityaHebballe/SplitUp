package com.aditya.splitup

import com.aditya.splitup.data.model.Expense
import com.aditya.splitup.data.model.ExpenseSplit
import com.aditya.splitup.data.model.Member
import com.aditya.splitup.data.model.Payment
import com.aditya.splitup.domain.BalanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceCalculatorTest {

    private val calculator = BalanceCalculator()

    @Test
    fun testTwoMemberCustomRatioSplit() {
        // Alice and Bob with 2:1 split
        val alice = Member(id = 1L, groupId = 100L, name = "Alice", defaultRatioPart = 2)
        val bob = Member(id = 2L, groupId = 100L, name = "Bob", defaultRatioPart = 1)
        val members = listOf(alice, bob)

        // Expense of $300 paid by Alice
        val expense = Expense(
            id = 1L,
            groupId = 100L,
            paidByMemberId = alice.id,
            amount = 300.0,
            currency = "USD",
            category = "Food"
        )
        val splits = mapOf(
            1L to listOf(
                ExpenseSplit(id = 1L, expenseId = 1L, memberId = alice.id, ratioPart = 2),
                ExpenseSplit(id = 2L, expenseId = 1L, memberId = bob.id, ratioPart = 1)
            )
        )

        val balances = calculator.calculateMemberBalances(
            members = members,
            expenses = listOf(expense),
            splits = splits,
            payments = emptyList(),
            rates = emptyMap(),
            groupCurrency = "USD"
        )

        // Alice paid 300, owes (2/3)*300 = 200 -> net = +100
        val aliceBal = balances.find { it.memberId == alice.id }!!
        assertEquals(300.0, aliceBal.totalPaid, 0.001)
        assertEquals(200.0, aliceBal.totalOwed, 0.001)
        assertEquals(100.0, aliceBal.netBalance, 0.001)

        // Bob paid 0, owes (1/3)*300 = 100 -> net = -100
        val bobBal = balances.find { it.memberId == bob.id }!!
        assertEquals(0.0, bobBal.totalPaid, 0.001)
        assertEquals(100.0, bobBal.totalOwed, 0.001)
        assertEquals(-100.0, bobBal.netBalance, 0.001)

        val settlements = calculator.calculateSettlements(
            members = members,
            expenses = listOf(expense),
            splits = splits,
            payments = emptyList(),
            rates = emptyMap(),
            groupCurrency = "USD"
        )
        assertEquals(1, settlements.size)
        assertEquals(bob.id, settlements[0].fromMemberId)
        assertEquals(alice.id, settlements[0].toMemberId)
        assertEquals(100.0, settlements[0].amount, 0.001)
    }

    @Test
    fun testPaymentReducesOwedDebt() {
        val alice = Member(id = 1L, groupId = 100L, name = "Alice", defaultRatioPart = 1)
        val bob = Member(id = 2L, groupId = 100L, name = "Bob", defaultRatioPart = 1)
        val members = listOf(alice, bob)

        // Alice pays 100 split 1:1 (Bob owes 50)
        val expense = Expense(
            id = 1L,
            groupId = 100L,
            paidByMemberId = alice.id,
            amount = 100.0,
            currency = "USD"
        )
        val splits = mapOf(
            1L to listOf(
                ExpenseSplit(id = 1L, expenseId = 1L, memberId = alice.id, ratioPart = 1),
                ExpenseSplit(id = 2L, expenseId = 1L, memberId = bob.id, ratioPart = 1)
            )
        )

        // Bob sends 30 to Alice
        val payment = Payment(
            id = 1L,
            groupId = 100L,
            fromMemberId = bob.id,
            toMemberId = alice.id,
            amount = 30.0,
            currency = "USD"
        )

        val settlements = calculator.calculateSettlements(
            members = members,
            expenses = listOf(expense),
            splits = splits,
            payments = listOf(payment),
            rates = emptyMap(),
            groupCurrency = "USD"
        )

        assertEquals(1, settlements.size)
        assertEquals(bob.id, settlements[0].fromMemberId)
        assertEquals(alice.id, settlements[0].toMemberId)
        // Bob originally owed 50, paid 30, so now owes 20
        assertEquals(20.0, settlements[0].amount, 0.001)
    }

    @Test
    fun testMultiCurrencyExpenseConversion() {
        val alice = Member(id = 1L, groupId = 100L, name = "Alice", defaultRatioPart = 1)
        val bob = Member(id = 2L, groupId = 100L, name = "Bob", defaultRatioPart = 1)
        val members = listOf(alice, bob)

        // Expense of 100 EUR paid by Alice, where group default is USD and rate EUR->USD is 1.10
        val expense = Expense(
            id = 1L,
            groupId = 100L,
            paidByMemberId = alice.id,
            amount = 100.0,
            currency = "EUR"
        )
        val splits = mapOf(
            1L to listOf(
                ExpenseSplit(id = 1L, expenseId = 1L, memberId = alice.id, ratioPart = 1),
                ExpenseSplit(id = 2L, expenseId = 1L, memberId = bob.id, ratioPart = 1)
            )
        )
        val rates = mapOf(("EUR" to "USD") to 1.10)

        val balances = calculator.calculateMemberBalances(
            members = members,
            expenses = listOf(expense),
            splits = splits,
            payments = emptyList(),
            rates = rates,
            groupCurrency = "USD"
        )

        // 100 EUR * 1.10 = 110 USD. Split equally: 55 USD each.
        val aliceBal = balances.find { it.memberId == alice.id }!!
        assertEquals(110.0, aliceBal.totalPaid, 0.001)
        assertEquals(55.0, aliceBal.totalOwed, 0.001)
        assertEquals(55.0, aliceBal.netBalance, 0.001)

        val bobBal = balances.find { it.memberId == bob.id }!!
        assertEquals(0.0, bobBal.totalPaid, 0.001)
        assertEquals(55.0, bobBal.totalOwed, 0.001)
        assertEquals(-55.0, bobBal.netBalance, 0.001)
    }
}
