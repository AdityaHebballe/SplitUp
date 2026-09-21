package com.example.expensetracker.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.expensetracker.data.model.Member
import com.example.expensetracker.ui.theme.CardShape

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RatioEditor(
    members: List<Member>,
    ratios: Map<Long, Int>,
    onRatioChanged: (Long, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalRatio = ratios.values.sum().coerceAtLeast(1)
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        members.forEach { member ->
            val ratio = ratios[member.id] ?: member.defaultRatioPart
            val percentage = (ratio.toFloat() / totalRatio) * 100
            val animatedProgress by animateFloatAsState(
                targetValue = (ratio.toFloat() / totalRatio).coerceIn(0f, 1f),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "ratioProgress_${member.id}"
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Circular Avatar Badge
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = member.name.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = member.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${String.format("%.1f", percentage)}% of expense",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Expressive Stepper: [-] [value] [+]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (ratio > 1) onRatioChanged(member.id, ratio - 1)
                                },
                                enabled = ratio > 1,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease share")
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .widthIn(min = 48.dp)
                                    .height(36.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "$ratio ${if (ratio == 1) "share" else "shares"}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onRatioChanged(member.id, ratio + 1)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase share")
                            }
                        }
                    }

                    // Live Animated Visual Ratio Bar
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
