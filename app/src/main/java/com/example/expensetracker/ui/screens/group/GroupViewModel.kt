package com.example.expensetracker.ui.screens.group

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.data.model.Expense
import com.example.expensetracker.data.model.Member
import com.example.expensetracker.data.model.Payment
import com.example.expensetracker.data.model.SplitGroup
import com.example.expensetracker.data.network.RetrofitInstance
import com.example.expensetracker.data.repository.ExchangeRateRepository
import com.example.expensetracker.domain.BalanceCalculator
import com.example.expensetracker.domain.MemberBalance
import com.example.expensetracker.domain.Settlement
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GroupViewModel(application: Application, val groupId: Long) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val groupDao = db.groupDao()
    private val expenseDao = db.expenseDao()
    private val paymentDao = db.paymentDao()
    private val memberDao = db.memberDao()
    private val rateRepo = ExchangeRateRepository(RetrofitInstance.api, db.exchangeRateDao())
    private val calculator = BalanceCalculator()

    val group: StateFlow<SplitGroup?> = groupDao.getGroupById(groupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val members: StateFlow<List<Member>> = memberDao.getMembersByGroup(groupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val expenses: StateFlow<List<Expense>> = expenseDao.getExpensesByGroup(groupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    val payments: StateFlow<List<Payment>> = paymentDao.getPaymentsByGroup(groupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val splits = expenseDao.getSplitsForGroup(groupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val ratesCache = MutableStateFlow<Map<Pair<String, String>, Double>>(emptyMap())

    init {
        // Observe expenses and payments to fetch exchange rates when currencies differ from group default
        viewModelScope.launch {
            combine(group, expenses, payments) { grp, exps, pays ->
                Triple(grp, exps, pays)
            }.collect { (grp, exps, pays) ->
                if (grp != null) {
                    val targetCurrency = grp.defaultCurrency
                    val foreignCurrencies = (exps.map { it.currency } + pays.map { it.currency })
                        .filter { it.isNotBlank() && it != targetCurrency }
                        .distinct()

                    val newRates = ratesCache.value.toMutableMap()
                    for (fromCur in foreignCurrencies) {
                        val key = fromCur to targetCurrency
                        if (!newRates.containsKey(key)) {
                            try {
                                val rate = rateRepo.getRate(fromCur, targetCurrency)
                                newRates[key] = rate
                            } catch (e: Exception) {
                                // Fallback 1.0 if offline or error
                                newRates[key] = 1.0
                            }
                        }
                    }
                    ratesCache.value = newRates
                }
            }
        }
    }

    // Combine (expenses, splits, payments) first, then combine with (members, group, rates)
    private val transactionsFlow = combine(expenses, splits, payments) { exps, spls, pays ->
        Triple(exps, spls, pays)
    }

    val memberBalances: StateFlow<List<MemberBalance>> = combine(
        members,
        group,
        ratesCache,
        transactionsFlow
    ) { mems, grp, rates, (exps, spls, pays) ->
        if (grp == null || mems.isEmpty()) {
            emptyList()
        } else {
            val splitMap = spls.groupBy { it.expenseId }
            calculator.calculateMemberBalances(
                members = mems,
                expenses = exps,
                splits = splitMap,
                payments = pays,
                rates = rates,
                groupCurrency = grp.defaultCurrency
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalSpent: StateFlow<Double> = memberBalances.map { balances ->
        balances.sumOf { it.totalPaid }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    val settlements: StateFlow<List<Settlement>> = combine(
        members,
        group,
        ratesCache,
        transactionsFlow
    ) { mems, grp, rates, (exps, spls, pays) ->
        if (grp == null || mems.isEmpty()) {
            emptyList()
        } else {
            val splitMap = spls.groupBy { it.expenseId }
            calculator.calculateSettlements(
                members = mems,
                expenses = exps,
                splits = splitMap,
                payments = pays,
                rates = rates,
                groupCurrency = grp.defaultCurrency
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val categorySpendings: StateFlow<List<CategorySpending>> = combine(
        expenses,
        group,
        ratesCache
    ) { exps, grp, rates ->
        if (grp == null || exps.isEmpty()) {
            emptyList()
        } else {
            val groupCurrency = grp.defaultCurrency
            val categoryIconMap = com.example.expensetracker.ui.components.expenseCategories.associate { it.name to it.icon }
            val convertedExpenses = exps.map { exp ->
                val rate = if (exp.currency == groupCurrency) 1.0 else (rates[exp.currency to groupCurrency] ?: 1.0)
                exp to (exp.amount * rate)
            }
            val total = convertedExpenses.sumOf { it.second }
            if (total <= 0.0) {
                emptyList()
            } else {
                convertedExpenses.groupBy { (exp, _) ->
                    exp.category?.ifBlank { "Other" } ?: "Other"
                }.map { (cat, list) ->
                    val catTotal = list.sumOf { it.second }
                    val pct = ((catTotal / total) * 100).toFloat()
                    val icon = categoryIconMap[cat] ?: "📦"
                    CategorySpending(cat, icon, catTotal, pct)
                }.sortedByDescending { it.amount }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val topExpenses: StateFlow<List<Expense>> = expenses.map { list ->
        list.sortedByDescending { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            expenseDao.deleteExpense(expense)
        }
    }

    fun recordPayment(fromMemberId: Long, toMemberId: Long, amount: Double, currency: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            paymentDao.insertPayment(
                Payment(
                    groupId = groupId,
                    fromMemberId = fromMemberId,
                    toMemberId = toMemberId,
                    amount = amount,
                    currency = currency
                )
            )
            onSuccess()
        }
    }
}

data class CategorySpending(
    val category: String,
    val icon: String,
    val amount: Double,
    val percentage: Float
)
