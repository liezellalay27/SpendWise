package com.spendwise.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private val Green = Color(0xFF315C4A)
private val MutedGreen = Color(0xFFE5F0E9)
private val Warning = Color(0xFFFFF1D6)

@Composable
fun DashboardScreen(model: SpendWiseViewModel, modifier: Modifier = Modifier) {
    var dialog by remember { mutableStateOf<MoneyDialogType?>(null) }
    ScreenColumn(modifier) {
        Header("Good morning, Alex", "Here is your spending plan for today.")
        AllowanceCard(model)
        PeriodSelector(model)
        Row(Modifier.fillMaxWidth()) {
            Button(onClick = { dialog = MoneyDialogType.EXPENSE }, modifier = Modifier.weight(1f)) {
                Text("Add expense")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { dialog = MoneyDialogType.INCOME }, modifier = Modifier.weight(1f)) {
                Text("Add income")
            }
        }
        if (model.remainingBalance < model.cycle.lowBalanceThreshold) {
            Card(colors = CardDefaults.cardColors(containerColor = Warning), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Your balance is getting low. Consider slowing down discretionary spending.",
                    Modifier.padding(16.dp),
                    color = Color(0xFF805A18)
                )
            }
        }
        SectionTitle("This cycle")
        MetricGrid(
            listOf(
                "Budget" to formatMoney(model.cycle.budget),
                "Spent" to formatMoney(model.spent),
                "Income added" to formatMoney(model.incomeAdded),
                "Emergency fund" to formatMoney(model.emergencyFund)
            )
        )
        SyncButton(model)
    }
    dialog?.let {
        MoneyDialog(
            title = if (it == MoneyDialogType.EXPENSE) "Add expense" else "Add income",
            onConfirm = { name, amount ->
                if (it == MoneyDialogType.EXPENSE) {
                    model.addExpense(name, amount)
                } else {
                    model.addIncome(amount)
                }
                dialog = null
            },
            onDismiss = { dialog = null }
        )
    }
}

@Composable
fun ActivityScreen(model: SpendWiseViewModel, modifier: Modifier = Modifier) {
    ScreenColumn(modifier) {
        Header("Activity", "Your latest money movements.")
        if (model.recentActivity.isEmpty()) {
            EmptyState("No activity yet", "Your expenses and income will appear here.")
        } else {
            model.recentActivity.forEach { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(item.label, fontWeight = FontWeight.SemiBold)
                            Text("This budget cycle", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            formatMoney(item.amount),
                            color = if (item.amount < 0) Color(0xFF9D3C35) else Green,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavingsScreen(model: SpendWiseViewModel, modifier: Modifier = Modifier) {
    var allocationText by remember(model.emergencyAllocationPercent) {
        mutableStateOf(model.emergencyAllocationPercent.toString())
    }
    ScreenColumn(modifier) {
        Header("Emergency fund", "Build a cushion for the unexpected.")
        Card(colors = CardDefaults.cardColors(containerColor = MutedGreen), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Current balance", color = Green)
                Text(formatMoney(model.emergencyFund), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${model.emergencyAllocationPercent}% of every added income is automatically allocated here.",
                    color = Green
                )
            }
        }
        SectionTitle("Automatic allocation")
        OutlinedTextField(
            value = allocationText,
            onValueChange = { allocationText = it.filter(Char::isDigit).take(3) },
            label = { Text("Emergency fund percentage") },
            suffix = { Text("%") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                allocationText.toIntOrNull()?.let(model::updateEmergencyAllocationPercent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save allocation")
        }
        SectionTitle("Savings milestones")
        MetricGrid(
            listOf(
                "First goal" to "PHP 10,000",
                "Current progress" to formatMoney(model.emergencyFund),
                "Next milestone" to "PHP 20,000"
            )
        )
        EmptyState("Automatic saving is on", "Add income from the Home tab to grow your emergency fund.")
    }
}

@Composable
fun ProfileScreen(model: SpendWiseViewModel, modifier: Modifier = Modifier) {
    ScreenColumn(modifier) {
        Header("Profile", "Personalize your SpendWise experience.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Alex", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("alex@example.com", color = Color.Gray)
            }
        }
        SectionTitle("Preferences")
        PreferenceRow("Currency", "PHP")
        PreferenceRow("Notifications", "Enabled")
        PreferenceRow("Storage", "Local + backend sync")
        Text("SpendWise MVP", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AllowanceCard(model: SpendWiseViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = Green), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SAFE TO SPEND TODAY", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelMedium)
            Text(formatMoney(model.safeToSpendToday), color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                "${formatMoney(model.remainingBalance)} left • ${model.daysRemaining} days remaining",
                color = Color.White.copy(alpha = .9f)
            )
        }
    }
}

@Composable
private fun PeriodSelector(model: SpendWiseViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Budget cycle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BudgetPeriod.values().forEach { period ->
                FilterChip(
                    selected = model.selectedPeriod == period,
                    onClick = { model.setPeriod(period) },
                    label = { Text(period.label) }
                )
            }
        }
    }
}

@Composable
private fun SyncButton(model: SpendWiseViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = { model.syncWithBackend() },
            enabled = !model.isSyncing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (model.isSyncing) "Syncing..." else "Sync with backend")
        }
        model.syncMessage?.let { Text(it, color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ScreenColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        content()
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color.Gray)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun MetricGrid(values: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        values.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) ->
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Text(value, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PreferenceRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, color = Color.Gray)
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(message, color = Color.Gray)
        }
    }
}

private data class MoneyDialogType(
    val expenseName: String? = null,
    val isExpense: Boolean
) {
    companion object {
        val EXPENSE = MoneyDialogType(isExpense = true)
        val INCOME = MoneyDialogType(isExpense = false)
    }
}

@Composable
private fun MoneyDialog(
    title: String,
    onConfirm: (String, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (title == "Add expense") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Expense name") },
                        placeholder = { Text("e.g. Lunch") },
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Amount in PHP") },
                    placeholder = { Text("e.g. 250.00") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                text.toCentsOrNull()?.let { amount -> onConfirm(name, amount) }
            }) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatMoney(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale("en", "PH")).format(cents / 100.0)

private fun String.toCentsOrNull(): Long? =
    trim().toBigDecimalOrNull()?.movePointRight(2)?.let {
        try { it.longValueExact() } catch (_: ArithmeticException) { null }
    }
