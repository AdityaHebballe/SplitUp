package com.aditya.splitup.ui.screens.group

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aditya.splitup.SplitTrackerApp
import com.aditya.splitup.data.AppDatabase
import com.aditya.splitup.data.model.Expense
import com.aditya.splitup.data.model.Member
import com.aditya.splitup.data.model.Payment
import com.aditya.splitup.data.model.SplitGroup
import com.aditya.splitup.data.network.RetrofitInstance
import com.aditya.splitup.data.repository.ExchangeRateRepository
import com.aditya.splitup.data.sync.SyncRepository
import com.aditya.splitup.domain.BalanceCalculator
import com.aditya.splitup.domain.MemberBalance
import com.aditya.splitup.domain.Settlement
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
    private val syncRepo: SyncRepository = (application as SplitTrackerApp).syncRepository

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
        // Start Firestore sync once we know the group's firestoreId
        viewModelScope.launch {
            group.filterNotNull().first().let { g ->
                if (g.firestoreId != null) {
                    syncRepo.startSync(g.firestoreId!!, g.id)
                    syncRepo.syncLocalUnsyncedData(g.id)
                }
            }
        }

        // Observe expenses and payments to fetch exchange rates when currencies differ
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

    override fun onCleared() {
        super.onCleared()
        // Stop listeners when ViewModel is cleared (user leaves group screen)
        val fsId = group.value?.firestoreId
        if (fsId != null) syncRepo.stopSync(fsId)
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
            val categoryIconMap = com.aditya.splitup.ui.components.expenseCategories.associate { it.name to it.icon }
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

    val rates: StateFlow<Map<Pair<String, String>, Double>> = ratesCache.asStateFlow()

    val topExpenses: StateFlow<List<Expense>> = combine(
        expenses,
        group,
        ratesCache
    ) { exps, grp, rates ->
        if (grp == null || exps.isEmpty()) {
            emptyList()
        } else {
            val groupCurrency = grp.defaultCurrency
            exps.sortedByDescending { exp ->
                val rate = if (exp.currency == groupCurrency) 1.0 else (rates[exp.currency to groupCurrency] ?: 1.0)
                exp.amount * rate
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            val currentGroup = group.value
            if (currentGroup != null) {
                syncRepo.deleteExpense(currentGroup, expense)
            } else {
                expenseDao.deleteExpense(expense)
            }
        }
    }

    fun recordPayment(fromMemberId: Long, toMemberId: Long, amount: Double, currency: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val currentGroup = group.value
            val payment = Payment(
                groupId = groupId,
                fromMemberId = fromMemberId,
                toMemberId = toMemberId,
                amount = amount,
                currency = currency
            )
            if (currentGroup != null) {
                syncRepo.recordPayment(currentGroup, payment)
            } else {
                paymentDao.insertPayment(payment)
            }
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
