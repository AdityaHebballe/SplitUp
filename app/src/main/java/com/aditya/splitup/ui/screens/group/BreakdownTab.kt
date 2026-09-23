package com.aditya.splitup.ui.screens.group

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aditya.splitup.data.model.Expense
import com.aditya.splitup.domain.CurrencyUtils
import com.aditya.splitup.ui.components.pressScale
import com.aditya.splitup.ui.components.rememberPressInteractionSource
import com.aditya.splitup.ui.theme.AmountStyle
import com.aditya.splitup.ui.theme.AmountStyleLarge
import com.aditya.splitup.ui.theme.AmountStyleSmall
import com.aditya.splitup.ui.theme.CardShape
import com.aditya.splitup.ui.theme.HeroCardShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BreakdownTab(
    viewModel: GroupViewModel,
    onEditExpense: (Expense) -> Unit,
    isTabActive: Boolean = true
) {
    val group by viewModel.group.collectAsStateWithLifecycle()
    val categorySpendings by viewModel.categorySpendings.collectAsStateWithLifecycle()
    val topExpenses by viewModel.topExpenses.collectAsStateWithLifecycle()
    val totalSpent by viewModel.totalSpent.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val rates by viewModel.rates.collectAsStateWithLifecycle()
    val memberMap = remember(members) { members.associateBy { it.id } }
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val haptic = LocalHapticFeedback.current

    val currentCurrency = group?.defaultCurrency ?: "USD"

    if (categorySpendings.isEmpty() && topExpenses.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = "No analytics yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Add some expenses to see category breakdowns and top spending trends!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        val colorPalette = listOf(
            MaterialTheme.colorScheme.primary,
            Color(0xFFFF8A65),
            MaterialTheme.colorScheme.tertiary,
            Color(0xFF26A69A),
            Color(0xFFFFB74D),
            Color(0xFFAB47BC),
            Color(0xFF42A5F5),
            Color(0xFFEC407A)
        )

        var selectedCategory by remember { mutableStateOf<String?>(null) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Donut Chart Card
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = HeroCardShape,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Spending by Category",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Donut Chart with Center Total & Touch Interaction
                        val strokeWidth = 28.dp
                        val popOffset = 10.dp

                        // Compute visual sweeps with minSweep guarantee so small categories render
                        // as smooth circles with rounded caps (StrokeCap.Round) without overlapping neighbors
                        val count = categorySpendings.size
                        val visualSweeps = remember(categorySpendings) {
                            if (count <= 1) {
                                FloatArray(count) { 360f }
                            } else {
                                val rawSweeps = categorySpendings.map { (it.percentage / 100f) * 360f }
                                val capAngleDeg = (14f / 81f) * (180f / Math.PI.toFloat())
                                val minSweepDeg = capAngleDeg * 2f + 5f // ~25 degrees to guarantee circle shape

                                val smallIndices = rawSweeps.indices.filter { rawSweeps[it] < minSweepDeg }
                                val largeIndices = rawSweeps.indices.filter { rawSweeps[it] >= minSweepDeg }

                                val totalSmallBudget = smallIndices.size * minSweepDeg
                                if (totalSmallBudget < 360f && largeIndices.isNotEmpty()) {
                                    val remainingForLarge = 360f - totalSmallBudget
                                    val largeRawSum = largeIndices.sumOf { rawSweeps[it].toDouble() }.toFloat()
                                    FloatArray(count) { i ->
                                        if (rawSweeps[i] < minSweepDeg) minSweepDeg
                                        else (rawSweeps[i] / largeRawSum) * remainingForLarge
                                    }
                                } else {
                                    FloatArray(count) { (categorySpendings[it].percentage / 100f) * 360f }
                                }
                            }
                        }

                        val startAngles = remember(visualSweeps) {
                            val starts = FloatArray(count)
                            var current = -90f
                            visualSweeps.forEachIndexed { i, sweep ->
                                starts[i] = current
                                current += sweep
                            }
                            starts
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(210.dp)
                                .pointerInput(categorySpendings, selectedCategory, visualSweeps) {
                                    val strokeWidthPx = strokeWidth.toPx()
                                    val popOffsetPx = popOffset.toPx()
                                    val marginPx = 12.dp.toPx()
                                    detectTapGestures { tapOffset ->
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val dx = tapOffset.x - center.x
                                        val dy = tapOffset.y - center.y
                                        val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                        val minDim = minOf(size.width, size.height).toFloat()
                                        val baseRadius = (minDim - strokeWidthPx - popOffsetPx * 2f) / 2f
                                        val minR = baseRadius - strokeWidthPx / 2f - marginPx
                                        val maxR = baseRadius + popOffsetPx + strokeWidthPx / 2f + marginPx

                                        if (dist in minR..maxR) {
                                            var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                            angle = (angle + 90f + 360f) % 360f

                                            var cumulative = 0f
                                            var tappedCategory: String? = null
                                            for (i in categorySpendings.indices) {
                                                val sweep = visualSweeps.getOrElse(i) { (categorySpendings[i].percentage / 100f) * 360f }
                                                if (angle >= cumulative && angle < cumulative + sweep) {
                                                    tappedCategory = categorySpendings[i].category
                                                    break
                                                }
                                                cumulative += sweep
                                            }
                                            if (tappedCategory != null) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedCategory = if (selectedCategory == tappedCategory) null else tappedCategory
                                            }
                                        } else if (dist < minR) {
                                            if (selectedCategory != null) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedCategory = null
                                            }
                                        }
                                    }
                                }
                        ) {
                            val animProgress = remember { Animatable(0f) }
                            LaunchedEffect(isTabActive, categorySpendings) {
                                if (isTabActive) {
                                    animProgress.snapTo(0f)
                                    animProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                            }

                            val slicePops = categorySpendings.map { item ->
                                val isSelected = selectedCategory == item.category
                                animateFloatAsState(
                                    targetValue = if (isSelected) 1f else 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "slicePop_${item.category}"
                                )
                            }
                            val sliceAlphas = categorySpendings.map { item ->
                                val isSelected = selectedCategory == item.category
                                animateFloatAsState(
                                    targetValue = if (selectedCategory == null || isSelected) 1f else 0.45f,
                                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                    label = "sliceAlpha_${item.category}"
                                )
                            }

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidthPx = strokeWidth.toPx()
                                val popOffsetPx = popOffset.toPx()
                                val baseRadius = (size.minDimension - strokeWidthPx - popOffsetPx * 2) / 2
                                val center = Offset(size.width / 2, size.height / 2)

                                // Always draw unselected slices first, then the selected slice on top
                                val unselectedIndices = categorySpendings.indices.filter { categorySpendings[it].category != selectedCategory }
                                val selectedIdx = categorySpendings.indices.firstOrNull { categorySpendings[it].category == selectedCategory }
                                val drawOrder = if (selectedIdx != null) unselectedIndices + selectedIdx else categorySpendings.indices.toList()

                                drawOrder.forEach { index ->
                                    val item = categorySpendings[index]
                                    val fullSweep = visualSweeps.getOrElse(index) { (item.percentage / 100f) * 360f }
                                    val sliceColor = colorPalette[index % colorPalette.size]

                                    val radius = baseRadius + popOffsetPx * slicePops[index].value
                                    val topLeft = Offset(center.x - radius, center.y - radius)
                                    val arcSize = Size(radius * 2, radius * 2)

                                    val capAngle = (strokeWidthPx / 2f / radius) * (180f / Math.PI.toFloat())
                                    val targetGap = 5f

                                    if (categorySpendings.size == 1) {
                                        drawArc(
                                            color = sliceColor.copy(alpha = sliceAlphas[index].value),
                                            startAngle = -90f,
                                            sweepAngle = (360f * animProgress.value).coerceAtLeast(0.1f),
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(
                                                width = strokeWidthPx,
                                                cap = StrokeCap.Butt
                                            )
                                        )
                                    } else {
                                        // All multi-segment slices use StrokeCap.Round!
                                        // Small slices at minSweep render as a perfect circle (no flat edges).
                                        // Larger slices render as elongated pills with rounded ends.
                                        val totalDeduct = capAngle * 2f + targetGap
                                        val arcSweep = (fullSweep - totalDeduct).coerceAtLeast(0.1f)
                                        val sweepAngle = (arcSweep * animProgress.value).coerceAtLeast(0.1f)
                                        val startAngle = startAngles[index] + capAngle + (targetGap / 2f)

                                        drawArc(
                                            color = sliceColor.copy(alpha = sliceAlphas[index].value),
                                            startAngle = startAngle,
                                            sweepAngle = sweepAngle,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(
                                                width = strokeWidthPx,
                                                cap = StrokeCap.Round
                                            )
                                        )
                                    }
                                }
                            }

                            AnimatedContent(
                                targetState = selectedCategory,
                                transitionSpec = {
                                    (fadeIn() + scaleIn(initialScale = 0.88f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.88f))
                                },
                                label = "donutCenterTransition"
                            ) { targetCat ->
                                if (targetCat == null) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    ) {
                                        Text(
                                            text = "Total Spent",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = CurrencyUtils.formatAmountStyled(
                                                totalSpent,
                                                currentCurrency,
                                                symbolColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            style = AmountStyleLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${categorySpendings.size} categories",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                } else {
                                    val item = categorySpendings.find { it.category == targetCat }
                                    if (item != null) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(horizontal = 24.dp)
                                        ) {
                                            Text(
                                                text = "${item.icon} ${item.category}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = CurrencyUtils.formatAmountStyled(
                                                    item.amount,
                                                    currentCurrency,
                                                    symbolColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                style = AmountStyleLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                                            ) {
                                                Text(
                                                    text = "${String.format("%.1f", item.percentage)}% of total",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Category Chips / Legend
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            categorySpendings.forEachIndexed { index, item ->
                                val sliceColor = colorPalette[index % colorPalette.size]
                                val isSelected = selectedCategory == item.category
                                val interactionSource = rememberPressInteractionSource()

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pressScale(interactionSource)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedCategory = if (isSelected) null else item.category
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
                                    border = if (isSelected) BorderStroke(1.5.dp, sliceColor) else null,
                                    shadowElevation = if (isSelected) 3.dp else 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = sliceColor,
                                                modifier = Modifier.size(14.dp)
                                            ) {}
                                            Text(
                                                text = "${item.icon} ${item.category}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                                            ) {
                                                Text(
                                                    text = "${String.format("%.1f", item.percentage)}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = CurrencyUtils.formatAmountStyled(
                                                    item.amount,
                                                    currentCurrency,
                                                    symbolColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                style = AmountStyleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Top Expenses Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Top Expenses",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Top Expenses Cards (sorted descending)
            itemsIndexed(topExpenses.take(10), key = { _, expense -> expense.id }) { index, expense ->
                val payer = memberMap[expense.paidByMemberId]
                val categoryIcon = com.aditya.splitup.ui.components.expenseCategories
                    .find { it.name.equals(expense.category, ignoreCase = true) }?.icon ?: "💳"
                val interactionSource = rememberPressInteractionSource()

                ElevatedCard(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onEditExpense(expense)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScale(interactionSource),
                    interactionSource = interactionSource,
                    shape = CardShape,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Rank Badge (#1, #2, ...)
                            Surface(
                                shape = CircleShape,
                                color = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "#${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (index == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Category Emoji Icon
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(categoryIcon, style = MaterialTheme.typography.titleMedium)
                                }
                            }

                            Column {
                                Text(
                                    text = expense.description.ifBlank { expense.category?.ifBlank { "Expense" } ?: "Expense" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Paid by ${payer?.name ?: "Unknown"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = dateFormatter.format(Date(expense.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = CurrencyUtils.formatAmountStyled(
                                    expense.amount,
                                    expense.currency,
                                    symbolColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                style = AmountStyle,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (expense.currency.isNotBlank() && expense.currency != currentCurrency) {
                                val rate = rates[expense.currency to currentCurrency] ?: 1.0
                                val converted = expense.amount * rate
                                Text(
                                    text = "≈ " + CurrencyUtils.formatAmount(converted, currentCurrency),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
