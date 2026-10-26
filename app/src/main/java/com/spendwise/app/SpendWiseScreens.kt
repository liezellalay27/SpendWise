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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val Green = Color(0xFF315C4A)
private val MutedGreen = Color(0xFFE5F0E9)
private val Warning = Color(0xFFFFF1D6)

@Composable
fun DashboardScreen(
    model: SpendWiseViewModel,
    displayName: String = "",
    modifier: Modifier = Modifier
) {
    var dialog by remember { mutableStateOf<MoneyDialogType?>(null) }
    var showBudgetSetup by remember { mutableStateOf(!model.hasConfiguredBudget) }
    var showExtraBudgetDialog by remember { mutableStateOf(false) }
    ScreenColumn(modifier) {
        Header("Good morning, ${displayName.ifBlank { "there" }}", "Here is your spending plan for today.")
        AllowanceCard(model)
        BudgetPeriodCard(model, onEdit = { showBudgetSetup = true })
        Row(Modifier.fillMaxWidth()) {
            Button(onClick = { dialog = MoneyDialogType.EXPENSE }, modifier = Modifier.weight(1f)) {
                Text("Add expense")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { dialog = MoneyDialogType.INCOME }, modifier = Modifier.weight(1f)) {
                Text("Add income")
            }
        }
        OutlinedButton(
            onClick = { showExtraBudgetDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add extra budget")
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
                "Extra budgets" to formatMoney(model.extraBudgets.sumOf { it.amount }),
                "Spent" to formatMoney(model.spent),
                "Income added" to formatMoney(model.incomeAdded),
                "Emergency fund" to formatMoney(model.emergencyFund)
            )
        )
        if (model.extraBudgets.isNotEmpty()) {
            SectionTitle("Extra budgets")
            model.extraBudgets.forEach { extraBudget ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(extraBudget.name, fontWeight = FontWeight.SemiBold)
                        Text(formatMoney(extraBudget.amount), color = Green, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        SyncButton(model)
    }
    if (showBudgetSetup) {
        BudgetSetupDialog(
            cycle = model.cycle,
            onSave = { startDate, endDate, budget ->
                model.updateBudget(startDate, endDate, budget)
                showBudgetSetup = false
            },
            onDismiss = { showBudgetSetup = false }
        )
    }
    if (showExtraBudgetDialog) {
        ExtraBudgetDialog(
            onSave = { name, amount ->
                model.addExtraBudget(name, amount)
                showExtraBudgetDialog = false
            },
            onDismiss = { showExtraBudgetDialog = false }
        )
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
    var showSavingsDialog by remember { mutableStateOf(false) }
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
        Button(
            onClick = { showSavingsDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add savings from budget")
        }
        Text(
            "Savings are deducted from your available budget and added to the emergency fund.",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )
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
    if (showSavingsDialog) {
        MoneyDialog(
            title = "Add savings",
            onConfirm = { _, amount ->
                model.addSavings(amount)
                showSavingsDialog = false
            },
            onDismiss = { showSavingsDialog = false }
        )
    }
}

@Composable
fun ProfileScreen(
    model: SpendWiseViewModel,
    displayName: String = "",
    email: String = "",
    modifier: Modifier = Modifier,
    onSignOut: () -> Unit = {}
) {
    var showResetDialog by remember { mutableStateOf(false) }
    ScreenColumn(modifier) {
        Header("Profile", "Personalize your SpendWise experience.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(displayName.ifBlank { "SpendWise user" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(email.ifBlank { "Email unavailable" }, color = Color.Gray)
            }
        }
        SectionTitle("Preferences")
        PreferenceRow("Currency", "PHP")
        PreferenceRow("Notifications", "Enabled")
        PreferenceRow("Storage", "Local + backend sync")
        OutlinedButton(
            onClick = { showResetDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset current cycle data")
        }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text("Sign out")
        }
        Text("SpendWise MVP", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    }
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset current cycle?") },
            text = {
                Text("This clears expenses, added income, emergency-fund allocations, and extra budgets stored on this device. Your budget dates and amount will remain.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.resetCycleData()
                        showResetDialog = false
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
private fun BudgetPeriodCard(model: SpendWiseViewModel, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Current budget", fontWeight = FontWeight.Bold)
                TextButton(onClick = onEdit) { Text("Edit") }
            }
            Text(
                "${formatDate(model.cycle.startDate)} - ${formatDate(model.cycle.endDate)}",
                color = Green
            )
            Text("${model.daysRemaining} days remaining", color = Color.Gray)
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

@Composable
private fun BudgetSetupDialog(
    cycle: BudgetCycle,
    onSave: (LocalDate, LocalDate, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var budgetText by remember { mutableStateOf((cycle.budget / 100.0).toString()) }
    var startDate by remember { mutableStateOf(cycle.startDate) }
    var endDate by remember { mutableStateOf(cycle.endDate) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set your budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    label = { Text("Budget amount in PHP") },
                    singleLine = true
                )
                OutlinedButton(onClick = { pickingStart = true }, Modifier.fillMaxWidth()) {
                    Text("Start date: ${formatDate(startDate)}")
                }
                OutlinedButton(onClick = { pickingEnd = true }, Modifier.fillMaxWidth()) {
                    Text("End date: ${formatDate(endDate)}")
                }
                if (!endDate.isAfter(startDate)) {
                    Text("End date must be after the start date.", color = Color(0xFFB3261E))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                budgetText.toCentsOrNull()?.let { budget ->
                    if (endDate.isAfter(startDate)) onSave(startDate, endDate, budget)
                }
            }) { Text("Save budget") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (pickingStart) {
        CalendarPicker(startDate, { selected -> startDate = selected; pickingStart = false }, { pickingStart = false })
    }
    if (pickingEnd) {
        CalendarPicker(endDate, { selected -> endDate = selected; pickingEnd = false }, { pickingEnd = false })
    }
}

@Composable
private fun ExtraBudgetDialog(
    onSave: (String, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add extra budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Add money for a trip, event, or another planned goal. " +
                        "This increases your available budget for the current cycle.",
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Budget name") },
                    placeholder = { Text("e.g. Baguio trip") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount in PHP") },
                    placeholder = { Text("e.g. 5000.00") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                amountText.toCentsOrNull()?.let { onSave(name, it) }
            }) { Text("Add budget") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CalendarPicker(
    date: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onDateSelected(
                        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    )
                }
            }) { Text("Select") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state)
    }
}

private fun formatDate(date: LocalDate): String =
    date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))

private fun formatMoney(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale("en", "PH")).format(cents / 100.0)

private fun String.toCentsOrNull(): Long? =
    trim().toBigDecimalOrNull()?.movePointRight(2)?.let {
        try { it.longValueExact() } catch (_: ArithmeticException) { null }
    }
