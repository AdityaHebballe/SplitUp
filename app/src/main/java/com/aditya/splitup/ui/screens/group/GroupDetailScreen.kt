package com.aditya.splitup.ui.screens.group

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.splitup.data.model.Expense
import com.aditya.splitup.data.model.Payment
import com.aditya.splitup.ui.screens.expense.AddExpenseSheet
import com.aditya.splitup.ui.screens.expense.AddExpenseViewModel
import com.aditya.splitup.ui.screens.payment.SettleUpSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroupDetailScreen(
    viewModel: GroupViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: (Long) -> Unit
) {
    val group by viewModel.group.collectAsStateWithLifecycle()
    val tabs = listOf("Expenses", "Balances", "Breakdown")
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    var showAddExpenseSheet by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
    var showSettleUpSheet by remember { mutableStateOf(false) }
    var settlementToApply by remember { mutableStateOf<com.aditya.splitup.domain.Settlement?>(null) }
    var paymentToEdit by remember { mutableStateOf<Payment?>(null) }

    val addExpenseViewModel: AddExpenseViewModel = viewModel()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.syncWarnings.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = group?.name ?: "Group",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = { group?.let { onNavigateToSettings(it.id) } }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = pagerState.currentPage == 0,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        expenseToEdit = null
                        showAddExpenseSheet = true
                    },
                    icon = {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    },
                    text = {
                        Text("Add Expense")
                    }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 2
            ) { page ->
                when (page) {
                    0 -> ExpenseListTab(
                        viewModel = viewModel,
                        onEditExpense = { expense ->
                            expenseToEdit = expense
                            showAddExpenseSheet = true
                        },
                        onEditPayment = { payment ->
                            paymentToEdit = payment
                        },
                        isTabActive = (pagerState.currentPage == 0)
                    )
                    1 -> BalancesTab(
                        viewModel = viewModel,
                        onNavigateToSettleUp = { showSettleUpSheet = true },
                        onApplySettlement = { settlement -> settlementToApply = settlement },
                        onEditPayment = { payment ->
                            paymentToEdit = payment
                        },
                        isTabActive = (pagerState.currentPage == 1)
                    )
                    2 -> BreakdownTab(
                        viewModel = viewModel,
                        onEditExpense = { expense ->
                            expenseToEdit = expense
                            showAddExpenseSheet = true
                        },
                        isTabActive = (pagerState.currentPage == 2)
                    )
                }
            }
        }
    }

    if (showAddExpenseSheet && group != null) {
        AddExpenseSheet(
            initialGroupId = group!!.id,
            expenseToEdit = expenseToEdit,
            onDismiss = {
                showAddExpenseSheet = false
                expenseToEdit = null
            },
            onExpenseSaved = { synced ->
                if (!synced) {
                    viewModel.postSyncWarning("Saved locally — couldn't sync to the cloud, will retry later")
                }
            },
            viewModel = addExpenseViewModel
        )
    }

    if ((showSettleUpSheet || settlementToApply != null || paymentToEdit != null) && group != null) {
        SettleUpSheet(
            viewModel = viewModel,
            paymentToEdit = paymentToEdit,
            initialFromMemberId = paymentToEdit?.fromMemberId ?: settlementToApply?.fromMemberId,
            initialToMemberId = paymentToEdit?.toMemberId ?: settlementToApply?.toMemberId,
            initialAmount = paymentToEdit?.amount ?: settlementToApply?.amount,
            initialCurrency = paymentToEdit?.currency ?: settlementToApply?.currency,
            onDismiss = {
                showSettleUpSheet = false
                settlementToApply = null
                paymentToEdit = null
            }
        )
    }
}
