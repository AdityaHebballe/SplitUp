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
import kotlinx.coroutines.channels.Channel

class GroupViewModel(application: Application, val groupId: Long) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val groupDao = db.groupDao()
    private val expenseDao = db.expenseDao()
    private val paymentDao = db.paymentDao()
    private val memberDao = db.memberDao()
    private val rateRepo = ExchangeRateRepository(RetrofitInstance.api, db.exchangeRateDao())
    private val calculator = BalanceCalculator()
    private val syncRepo: SyncRepository = (application as SplitTrackerApp).syncRepository

    val currentUid: String? get() = syncRepo.firestore.currentUid

    val group: StateFlow<SplitGroup?> = groupDao.getGroupById(groupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val members: StateFlow<List<Member>> = memberDao.getMembersByGroup(groupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Active (non-removed) members only. Balances/settlements and member-selection UI
    // (SettleUpSheet) should use this instead of `members` so a former member never
    // appears as a payer/recipient option or an outstanding balance row — `members`
    // itself stays unfiltered so historical payer-name lookups (e.g. in ExpenseListTab)
    // still resolve for expenses a now-removed member paid in the past.
    val activeMembers: StateFlow<List<Member>> = members
        .map { list -> list.filter { !it.isRemoved } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val expenses: StateFlow<List<Expense>> = expenseDao.getExpensesByGroup(groupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val payments: StateFlow<List<Payment>> = paymentDao.getPaymentsByGroup(groupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val splits = expenseDao.getSplitsForGroup(groupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val ratesCache = MutableStateFlow<Map<Pair<String, String>, Double>>(emptyMap())

    // True when at least one required exchange rate could not be fetched, so the
    // displayed rate (1.0 fallback, not cached) may not reflect the real conversion.
    private val _ratesStale = MutableStateFlow(false)
    val ratesStale: StateFlow<Boolean> = _ratesStale.asStateFlow()

    // One-shot events for the UI (e.g. a snackbar) when a write is saved locally
    // but failed to sync to the cloud, so the user isn't left thinking it worked.
    private val _syncWarnings = Channel<String>(Channel.BUFFERED)
    val syncWarnings: Flow<String> = _syncWarnings.receiveAsFlow()

    init {
        // Start Firestore sync once we know the group's firestoreId
        viewModelScope.launch {
            try {
                group.filterNotNull().first().let { g ->
                    if (g.firestoreId != null) {
                        syncRepo.startSync(g.firestoreId!!, g.id)
                        syncRepo.syncLocalUnsyncedData(g.id)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GroupViewModel", "Failed to start sync", e)
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
                    var anyStale = false
                    for (fromCur in foreignCurrencies) {
                        val key = fromCur to targetCurrency
                        // Only skip currencies we've already fetched successfully — a
                        // previously failed fetch is retried here instead of being
                        // cached as a permanent (and possibly wrong) 1.0.
                        if (!newRates.containsKey(key)) {
                            try {
                                val rate = rateRepo.getRate(fromCur, targetCurrency)
                                newRates[key] = rate
                            } catch (e: Exception) {
                                anyStale = true
                            }
                        }
                    }
                    ratesCache.value = newRates
                    _ratesStale.value = anyStale
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
        activeMembers,
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
        activeMembers,
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
                val synced = syncRepo.deleteExpense(currentGroup, expense)
                if (!synced) {
                    _syncWarnings.trySend("Deleted locally — couldn't sync to the cloud, will retry later")
                }
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
                val synced = syncRepo.recordPayment(currentGroup, payment)
                if (!synced) {
                    _syncWarnings.trySend("Saved locally — couldn't sync to the cloud, will retry later")
                }
            } else {
                paymentDao.insertPayment(payment)
            }
            onSuccess()
        }
    }

    fun updatePayment(payment: Payment, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val currentGroup = group.value
            if (currentGroup != null) {
                val synced = syncRepo.updatePayment(currentGroup, payment)
                if (!synced) {
                    _syncWarnings.trySend("Saved locally — couldn't sync to the cloud, will retry later")
                }
            } else {
                paymentDao.updatePayment(payment)
            }
            onSuccess()
        }
    }

    fun deletePayment(payment: Payment) {
        viewModelScope.launch {
            val currentGroup = group.value
            if (currentGroup != null) {
                val synced = syncRepo.deletePayment(currentGroup, payment)
                if (!synced) {
                    _syncWarnings.trySend("Deleted locally — couldn't sync to the cloud, will retry later")
                }
            } else {
                paymentDao.deletePayment(payment)
            }
        }
    }

    fun postSyncWarning(message: String) {
        _syncWarnings.trySend(message)
    }
}

data class CategorySpending(
    val category: String,
    val icon: String,
    val amount: Double,
    val percentage: Float
)
