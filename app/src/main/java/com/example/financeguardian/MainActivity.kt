package com.example.financeguardian

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.financeguardian.ui.theme.FinanceGuardianTheme
import kotlinx.coroutines.delay

// ─── Color Palette ────────────────────────────────────────────────────────────

private val BgDeep        = Color(0xFF05070F)
private val BgCardAlt     = Color(0xFF0F1520)
private val AccentBlue    = Color(0xFF00C2FF)
private val AccentCyan    = Color(0xFF00FFD1)
private val AccentPurple  = Color(0xFF7B5EF8)
private val AccentAmber   = Color(0xFFFFB830)
private val DangerRed     = Color(0xFFFF3D5A)
private val SafeGreen     = Color(0xFF00E5A0)
private val TextPrimary   = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF6B7A99)
private val GlassStroke   = Color(0x1AFFFFFF)
private val GlowBlue      = Color(0x3300C2FF)
private val GlowCyan      = Color(0x2200FFD1)

// ─── Constants ────────────────────────────────────────────────────────────────

private val CATEGORIES = listOf(
    "Food", "Shopping", "Transport", "Entertainment",
    "Health", "Utilities", "Personal", "Others"
)

private val CATEGORY_COLORS = listOf(
    AccentCyan, AccentPurple, AccentBlue, AccentAmber,
    SafeGreen, Color(0xFFF85E9F), Color(0xFF7B5EF8), DangerRed
)

// ─── Activity ─────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestAppPermissions()
        setContent {
            FinanceGuardianTheme {
                FinanceGuardianUI(this)
            }
        }
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1)
        }
    }
}

// ─── Root UI ──────────────────────────────────────────────────────────────────

@Composable
fun FinanceGuardianUI(context: Context) {
    val sharedPreferences = context.getSharedPreferences("FinanceGuardian", Context.MODE_PRIVATE)

    var budgetInput    by remember { mutableStateOf("") }
    var totalBudget    by remember { mutableIntStateOf(sharedPreferences.getInt("TOTAL_BUDGET", 0)) }
    var currentBalance by remember { mutableIntStateOf(sharedPreferences.getInt("CURRENT_BALANCE", 0)) }
    var pendingPayment by remember { mutableIntStateOf(sharedPreferences.getInt("PENDING_PAYMENT", 0)) }
    var transactionHistory by remember { mutableStateOf(sharedPreferences.getString("TRANSACTION_HISTORY", "") ?: "") }
    
    // Map to hold category totals
    val categoryTotals = remember { mutableStateMapOf<String, Int>() }
    
    // Auto-refresh all values from SharedPreferences
    LaunchedEffect(Unit) {
        while (true) {
            totalBudget = sharedPreferences.getInt("TOTAL_BUDGET", 0)
            currentBalance = sharedPreferences.getInt("CURRENT_BALANCE", 0)
            pendingPayment = sharedPreferences.getInt("PENDING_PAYMENT", 0)
            transactionHistory = sharedPreferences.getString("TRANSACTION_HISTORY", "") ?: ""
            
            CATEGORIES.forEach { category ->
                categoryTotals[category] = sharedPreferences.getInt(category, 0)
            }
            
            delay(1000)
        }
    }

    val analytics by remember(totalBudget, currentBalance, categoryTotals, transactionHistory) {
        derivedStateOf {
            computeAnalytics(totalBudget, currentBalance, categoryTotals.toMap(), transactionHistory)
        }
    }

    val riskAnalysis by remember(totalBudget, currentBalance, categoryTotals, transactionHistory) {
        derivedStateOf {
            computeRiskAnalysis(totalBudget, currentBalance, categoryTotals.toMap(), transactionHistory)
        }
    }

    val spentAmount         = totalBudget - currentBalance
    val budgetUsagePercent  = if (totalBudget > 0) ((spentAmount.toFloat() / totalBudget.toFloat()) * 100).toInt() else 0

    // Global fade-in
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 8 }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDeep)
        ) {
            AmbientBackground()

            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(Modifier.height(56.dp)) }

                item { HeaderSection() }
                item { AiStatusBar() }

                item {
                    BalanceCard(
                        totalBudget        = totalBudget,
                        currentBalance     = currentBalance,
                        spentAmount        = spentAmount,
                        budgetUsagePercent = budgetUsagePercent,
                        riskLevel          = riskAnalysis.riskLevel
                    )
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        RiskMeterCard(
                            modifier           = Modifier.weight(1f),
                            budgetUsagePercent = riskAnalysis.score,
                            riskLevel          = riskAnalysis.riskLevel
                        )
                        SpendingUsageCard(
                            modifier           = Modifier.weight(1f),
                            budgetUsagePercent = budgetUsagePercent,
                            spentAmount        = spentAmount
                        )
                    }
                }

                // Monthly Analytics Card
                item {
                    AnalyticsCard(analytics = analytics)
                }

                // AI Risk Engine Card
                item {
                    RiskEngineCard(risk = riskAnalysis)
                }

                // Quick Demo Simulation
                item {
                    DemoTransactionCard(onSimulate = { amount, category ->
                        simulateTransaction(sharedPreferences, amount, category)
                    })
                }

                // Budget Input
                item {
                    BudgetInputCard(
                        budgetInput = budgetInput,
                        onInputChange = { budgetInput = it },
                        onUpdate = {
                            if (budgetInput.isNotEmpty()) {
                                val addedAmount = budgetInput.toIntOrNull() ?: 0
                                if (addedAmount > 0) {
                                    val newTotal = totalBudget + addedAmount
                                    val newBalance = currentBalance + addedAmount
                                    sharedPreferences.edit {
                                        putInt("TOTAL_BUDGET", newTotal)
                                        putInt("CURRENT_BALANCE", newBalance)
                                    }
                                    budgetInput = ""
                                }
                            }
                        }
                    )
                }

                // Categorical Spending (Pie Chart)
                item {
                    CategoryPieChartCard(categoryTotals = categoryTotals)
                }
                
                // Recent Transactions
                item {
                    TransactionHistoryCard(historyString = transactionHistory)
                }

                // Pending Payment Alert
                if (pendingPayment > 0) {
                    item {
                        PendingPaymentCard(
                            pendingPayment = pendingPayment,
                            onYes = {
                                val newBalance = (currentBalance - pendingPayment).coerceAtLeast(0)
                                sharedPreferences.edit {
                                    putInt("CURRENT_BALANCE", newBalance)
                                    putInt("PENDING_PAYMENT", 0)
                                    
                                    // Add to history as "Manual Confirmation"
                                    val timestamp = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                                    val newEntry = "Confirmed Payment|₹$pendingPayment|Others|$timestamp\n"
                                    val existing = sharedPreferences.getString("TRANSACTION_HISTORY", "") ?: ""
                                    putString("TRANSACTION_HISTORY", newEntry + existing)
                                }
                            },
                            onNo = {
                                sharedPreferences.edit { putInt("PENDING_PAYMENT", 0) }
                            }
                        )
                    }
                }

                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

// ─── Ambient Background ───────────────────────────────────────────────────────

@Composable
fun AmbientBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulse"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            brush  = Brush.radialGradient(listOf(GlowBlue, Color.Transparent), radius = size.width * 0.7f),
            radius = size.width * 0.55f * pulse,
            center = Offset(size.width * 0.85f, size.height * 0.12f),
            alpha  = 0.5f
        )
        drawCircle(
            brush  = Brush.radialGradient(listOf(GlowCyan, Color.Transparent), radius = size.width * 0.5f),
            radius = size.width * 0.4f * pulse,
            center = Offset(size.width * 0.1f, size.height * 0.55f),
            alpha  = 0.4f
        )
        drawCircle(
            brush  = Brush.radialGradient(listOf(Color(0x1A7B5EF8), Color.Transparent), radius = size.width * 0.4f),
            radius = size.width * 0.35f,
            center = Offset(size.width * 0.5f, size.height * 0.88f),
            alpha  = 0.6f
        )
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
fun HeaderSection() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(38.dp).background(
                        brush = Brush.radialGradient(listOf(AccentBlue.copy(alpha = 0.4f), Color.Transparent)),
                        shape = CircleShape
                    )
                )
                Box(
                    modifier = Modifier.size(26.dp).background(
                        brush = Brush.linearGradient(listOf(AccentBlue, AccentCyan)),
                        shape = CircleShape
                    )
                )
            }
            Text(
                text       = "FinClaw",
                fontSize   = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = TextPrimary,
                letterSpacing = 0.3.sp
            )
            Text(
                text       = "AI",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = AccentCyan,
                modifier   = Modifier
                    .background(AccentCyan.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(
            text      = "OpenClaw-powered financial guardian",
            fontSize  = 13.sp,
            color     = TextSecondary,
            letterSpacing = 0.2.sp
        )
    }
}

// ─── AI Status Bar ────────────────────────────────────────────────────────────

@Composable
fun AiStatusBar() {
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse),
        label = "dot"
    )

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            StatusChip(label = "AI Active",        color = SafeGreen,    dotAlpha = dotAlpha)
            StatusChip(label = "OpenClaw Online",  color = AccentBlue,   dotAlpha = dotAlpha)
            StatusChip(label = "Monitoring",       color = AccentPurple, dotAlpha = dotAlpha)
        }
    }
}

@Composable
fun StatusChip(label: String, color: Color, dotAlpha: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier.size(7.dp).background(color.copy(alpha = dotAlpha), CircleShape)
        )
        Text(text = label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
    }
}

// ─── Balance Card ─────────────────────────────────────────────────────────────

@Composable
fun BalanceCard(
    totalBudget: Int,
    currentBalance: Int,
    spentAmount: Int,
    budgetUsagePercent: Int,
    riskLevel: RiskLevel
) {
    val accentColor = riskLevel.color
    val animatedProgress by animateFloatAsState(
        targetValue   = (budgetUsagePercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(1000, easing = EaseOutCubic),
        label         = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0E1628), Color(0xFF0A0F1E)),
                    start  = Offset.Zero,
                    end    = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .border(1.dp, GlassStroke, RoundedCornerShape(28.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(3.dp).background(
                Brush.horizontalGradient(listOf(AccentBlue, AccentCyan, AccentPurple)),
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
        )

        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Available Funds", fontSize = 13.sp, color = TextSecondary, letterSpacing = 0.5.sp)
                Box(
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(riskLevel.label, fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))

            AnimatedContent(
                targetState   = currentBalance,
                transitionSpec = { slideInVertically { -it } + fadeIn() togetherWith slideOutVertically { it } + fadeOut() },
                label         = "balance"
            ) { balance ->
                Text(
                    text       = "₹${formatAmount(balance)}",
                    fontSize   = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = TextPrimary,
                    letterSpacing = (-1).sp
                )
            }

            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Spent: ₹${formatAmount(spentAmount)}", fontSize = 12.sp, color = TextSecondary)
                    Text("$budgetUsagePercent% used", fontSize = 12.sp, color = accentColor, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF1E2A40))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(animatedProgress).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(
                            Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.7f), accentColor))
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricPill(label = "Budget", value = "₹${formatAmount(totalBudget)}", color = AccentBlue, modifier = Modifier.weight(1f))
                MetricPill(label = "Spent",  value = "₹${formatAmount(spentAmount)}", color = DangerRed,  modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MetricPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ─── Risk Meter Card ──────────────────────────────────────────────────────────

@Composable
fun RiskMeterCard(modifier: Modifier = Modifier, budgetUsagePercent: Int, riskLevel: RiskLevel) {
    val animatedSweep by animateFloatAsState(
        targetValue   = (budgetUsagePercent / 100f).coerceIn(0f, 1f) * 240f,
        animationSpec = tween(1200, easing = EaseOutCubic),
        label         = "sweep"
    )
    val accentColor = riskLevel.color

    GlassCard(modifier = modifier) {
        Column(
            modifier              = Modifier.padding(18.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(10.dp)
        ) {
            Text("Risk Level", fontSize = 12.sp, color = TextSecondary)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(90.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke    = 10f
                    val inset     = stroke / 2
                    val startAngle = 150f
                    drawArc(
                        color      = Color(0xFF1E2A40),
                        startAngle = startAngle,
                        sweepAngle = 240f,
                        useCenter  = false,
                        style      = Stroke(width = stroke, cap = StrokeCap.Round),
                        topLeft    = Offset(inset, inset),
                        size       = this.size.copy(width = this.size.width - stroke, height = this.size.height - stroke)
                    )
                    drawArc(
                        brush      = Brush.sweepGradient(listOf(SafeGreen, AccentAmber, DangerRed)),
                        startAngle = startAngle,
                        sweepAngle = animatedSweep,
                        useCenter  = false,
                        style      = Stroke(width = stroke, cap = StrokeCap.Round),
                        topLeft    = Offset(inset, inset),
                        size       = this.size.copy(width = this.size.width - stroke, height = this.size.height - stroke),
                        alpha      = if (animatedSweep > 0f) 1f else 0f
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = "$budgetUsagePercent%",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = accentColor
                    )
                    Text(text = riskLevel.label, fontSize = 9.sp, color = TextSecondary)
                }
            }
        }
    }
}

// ─── Spending Usage Card ──────────────────────────────────────────────────────

@Composable
fun SpendingUsageCard(modifier: Modifier = Modifier, budgetUsagePercent: Int, spentAmount: Int) {
    GlassCard(modifier = modifier) {
        Column(
            modifier            = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Spent", fontSize = 12.sp, color = TextSecondary)
            Text(
                text       = "₹${formatAmount(spentAmount)}",
                fontSize   = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = TextPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            MiniBarChart(percent = budgetUsagePercent)
            Text(text = "of budget used", fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
fun MiniBarChart(percent: Int) {
    val animatedWidth by animateFloatAsState(
        targetValue   = (percent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(1000, easing = EaseOutCubic),
        label         = "miniBar"
    )
    val barColor = when {
        percent >= 85 -> DangerRed
        percent >= 60 -> AccentAmber
        else          -> SafeGreen
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.5.dp)).background(Color(0xFF1E2A40))
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(animatedWidth).fillMaxHeight().clip(RoundedCornerShape(2.5.dp)).background(barColor)
        )
    }
}

// ─── Budget Input Card ────────────────────────────────────────────────────────

@Composable
fun BudgetInputCard(budgetInput: String, onInputChange: (String) -> Unit, onUpdate: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier            = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(8.dp).background(AccentCyan, CircleShape))
                Text("Add Salary / Adjust Budget", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }

            OutlinedTextField(
                value         = budgetInput,
                onValueChange = onInputChange,
                placeholder   = { Text("Enter amount in ₹", color = TextSecondary, fontSize = 14.sp) },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(16.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AccentBlue,
                    unfocusedBorderColor = GlassStroke,
                    focusedTextColor     = TextPrimary,
                    unfocusedTextColor   = TextPrimary,
                    cursorColor          = AccentBlue,
                    focusedContainerColor   = Color(0xFF0D1420),
                    unfocusedContainerColor = Color(0xFF0D1420)
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            )

            Box(
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(AccentBlue, AccentCyan)))
                    .clickable { onUpdate() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Update Budget", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BgDeep)
            }
        }
    }
}

// ─── Pending Payment Card ─────────────────────────────────────────────────────

@Composable
fun PendingPaymentCard(pendingPayment: Int, onYes: () -> Unit, onNo: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "warn")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOut), RepeatMode.Reverse),
        label = "glow"
    )

    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF150A10))
            .border(1.5.dp, DangerRed.copy(alpha = glowAlpha + 0.2f), RoundedCornerShape(24.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(3.dp).background(
                Brush.horizontalGradient(listOf(DangerRed.copy(alpha = 0.6f), DangerRed, DangerRed.copy(alpha = 0.6f))),
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
        )

        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(10.dp).background(DangerRed.copy(alpha = glowAlpha + 0.4f), CircleShape))
                Text("Pending Payment", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DangerRed)
            }

            Text(text = "₹${formatAmount(pendingPayment)}", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Text(text = "OpenClaw AI detected a payment. Did you complete this transaction?", fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(DangerRed).clickable { onYes() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("YES, PAID", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Box(
                    modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1E2030)).border(1.dp, GlassStroke, RoundedCornerShape(14.dp)).clickable { onNo() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("NO, SKIP", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                }
            }
        }
    }
}

// ─── Demo Simulation ──────────────────────────────────────────────────────────

@Composable
fun DemoTransactionCard(onSimulate: (Int, String) -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Quick Simulation", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoButton("₹450 Food", AccentCyan, modifier = Modifier.weight(1f)) { onSimulate(450, "Food") }
                DemoButton("₹899 Shop", AccentPurple, modifier = Modifier.weight(1f)) { onSimulate(899, "Shopping") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoButton("₹1200 Health", SafeGreen, modifier = Modifier.weight(1f)) { onSimulate(1200, "Health") }
                DemoButton("₹300 Util", AccentAmber, modifier = Modifier.weight(1f)) { onSimulate(300, "Utilities") }
            }
        }
    }
}

@Composable
fun DemoButton(title: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = title, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

fun simulateTransaction(sharedPreferences: android.content.SharedPreferences, amount: Int, category: String) {
    val currentBalance = sharedPreferences.getInt("CURRENT_BALANCE", 0)
    val updatedBalance = (currentBalance - amount).coerceAtLeast(0)

    sharedPreferences.edit {
        putInt("CURRENT_BALANCE", updatedBalance)
        val prev = sharedPreferences.getInt(category, 0)
        putInt(category, prev + amount)
        
        // Add to history
        val timestamp = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val dateStamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val newEntry = "Demo Spend|₹$amount|$category|$timestamp|$dateStamp\n"
        val existing = sharedPreferences.getString("TRANSACTION_HISTORY", "") ?: ""
        putString("TRANSACTION_HISTORY", newEntry + existing)
    }
}

// ─── Analytics (Pie Chart) ────────────────────────────────────────────────────

@Composable
fun CategoryPieChartCard(categoryTotals: Map<String, Int>) {
    val totalSpend = categoryTotals.values.sum()

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Categorical Spend", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(20.dp))

            if (totalSpend == 0) {
                Text("No spending data yet", color = TextSecondary, fontSize = 14.sp)
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(180.dp)) {
                        var startAngle = 0f
                        CATEGORIES.forEachIndexed { index, category ->
                            val amount = categoryTotals[category] ?: 0
                            if (amount > 0) {
                                val sweepAngle = (amount.toFloat() / totalSpend.toFloat()) * 360f
                                drawArc(
                                    color = CATEGORY_COLORS.getOrElse(index) { Color.Gray },
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = true
                                )
                                startAngle += sweepAngle
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Legend
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIES.forEachIndexed { index, category ->
                        val amount = categoryTotals[category] ?: 0
                        if (amount > 0) {
                            CategoryLegend(category, amount, CATEGORY_COLORS.getOrElse(index) { Color.Gray })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryLegend(label: String, amount: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
            Text(text = label, color = TextPrimary, fontSize = 14.sp)
        }
        Text(text = "₹$amount", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

// ─── Transaction History ──────────────────────────────────────────────────────

@Composable
fun TransactionHistoryCard(historyString: String) {
    val transactionList = historyString.split("\n").filter { it.isNotEmpty() }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Recent Transactions", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(16.dp))

            if (transactionList.isEmpty()) {
                Text("No transactions yet", color = TextSecondary, fontSize = 14.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    transactionList.take(10).forEach { entry ->
                        val parts = entry.split("|")
                        if (parts.size >= 4) {
                            TransactionItem(
                                merchant = parts[0],
                                amount   = parts[1],
                                category = parts[2],
                                time     = parts[3]
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionItem(merchant: String, amount: String, category: String, time: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = merchant, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "$category • $time", color = TextSecondary, fontSize = 12.sp)
        }
        Text(text = amount, color = DangerRed, fontWeight = FontWeight.Bold)
    }
}

// ─── Reusable Glass Card ──────────────────────────────────────────────────────

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(22.dp)).background(BgCardAlt).border(1.dp, GlassStroke, RoundedCornerShape(22.dp))
    ) {
        content()
    }
}

enum class RiskLevel(val label: String, val color: Color) {
    SAFE("Safe", SafeGreen),
    CAUTION("Caution", AccentAmber),
    HIGH("High Risk", Color(0xFFFF8C00)),
    CRITICAL("Critical", DangerRed)
}

// ─── Risk & Warning Engine ──────────────────────────────────────────────────

data class RiskAnalysis(
    val riskLevel: RiskLevel,
    val warnings: List<String>,
    val aiInsight: String,
    val score: Int // 0-100
)

fun computeRiskAnalysis(
    totalBudget: Int,
    currentBalance: Int,
    categoryTotals: Map<String, Int>,
    history: String
): RiskAnalysis {
    val warnings = mutableListOf<String>()
    var riskPoints = 0
    
    val spent = (totalBudget - currentBalance).coerceAtLeast(0)
    val usagePct = if (totalBudget > 0) (spent * 100 / totalBudget) else 0
    
    // 1. Balance based risks
    when {
        usagePct >= 90 -> {
            riskPoints += 40
            warnings.add("Remaining budget is critically low")
        }
        usagePct >= 75 -> {
            riskPoints += 25
            warnings.add("Budget usage is becoming risky")
        }
        usagePct >= 60 -> {
            riskPoints += 15
            warnings.add("Spending is outpacing the monthly cycle")
        }
    }
    
    // 2. Category based risks
    categoryTotals.forEach { (cat, amount) ->
        if (spent > 0) {
            val catPct = amount * 100 / spent
            if (catPct > 50) {
                riskPoints += 20
                warnings.add("$cat dominates your monthly spending")
            } else if (catPct > 35) {
                riskPoints += 10
                warnings.add("$cat activity is unusually high")
            }
        }
    }
    
    // 3. Large transaction detection
    val lines = history.split("\n").filter { it.isNotEmpty() }
    if (lines.isNotEmpty()) {
        val lastAmountStr = lines.first().split("|").getOrNull(1)?.replace("₹", "") ?: "0"
        val lastAmount = lastAmountStr.toIntOrNull() ?: 0
        if (totalBudget > 0 && lastAmount > (totalBudget * 0.2)) {
            riskPoints += 15
            warnings.add("Last transaction was unusually large")
        }
    }
    
    // 4. Frequency detection
    if (lines.size > 15) {
        riskPoints += 10
        warnings.add("High frequency of small transactions detected")
    }
    
    val level = when {
        riskPoints >= 60 -> RiskLevel.CRITICAL
        riskPoints >= 40 -> RiskLevel.HIGH
        riskPoints >= 20 -> RiskLevel.CAUTION
        else            -> RiskLevel.SAFE
    }
    
    val aiInsight = when (level) {
        RiskLevel.CRITICAL -> "Immediate intervention required to avoid total budget exhaustion."
        RiskLevel.HIGH -> "Current spending behavior may exhaust budget early."
        RiskLevel.CAUTION -> "Spending is slightly above optimal levels. Monitor closely."
        RiskLevel.SAFE -> "Financial health is optimal. Spending patterns remain predictable."
    }
    
    return RiskAnalysis(level, warnings.take(3), aiInsight, riskPoints.coerceIn(0, 100))
}

@Composable
fun RiskEngineCard(risk: RiskAnalysis) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    val cardAlpha = if (risk.riskLevel == RiskLevel.CRITICAL) pulseAlpha else 1f
    
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = cardAlpha }
            .border(
                width = if (risk.riskLevel == RiskLevel.CRITICAL) 2.dp else 0.dp,
                color = if (risk.riskLevel == RiskLevel.CRITICAL) risk.riskLevel.color.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI RISK ENGINE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = risk.riskLevel.color, letterSpacing = 1.2.sp)
                Box(
                    modifier = Modifier
                        .background(risk.riskLevel.color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(risk.riskLevel.label, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = risk.riskLevel.color)
                }
            }
            
            if (risk.warnings.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    risk.warnings.forEach { warning ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(6.dp).background(risk.riskLevel.color, CircleShape))
                            Text(text = warning, fontSize = 13.sp, color = TextPrimary.copy(alpha = 0.9f))
                        }
                    }
                }
            } else {
                Text("No critical risks detected in current behavior.", fontSize = 13.sp, color = TextSecondary)
            }
            
            HorizontalDivider(color = GlassStroke, thickness = 1.dp)
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("AI Insight", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Text(
                    text = "\"${risk.aiInsight}\"",
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    color = TextPrimary,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

fun formatAmount(amount: Int): String {
    return when {
        amount >= 100_000 -> "%.1fL".format(amount / 100_000f)
        amount >= 1_000   -> "%.1fK".format(amount / 1_000f)
        else              -> amount.toString()
    }
}

// ─── Analytics Engine ─────────────────────────────────────────────────────────

enum class Trend { UP, DOWN, STABLE }

data class SpendingAnalytics(
    val monthlySpend: Int,
    val budgetUsagePercent: Int,
    val highestCategory: String,
    val avgDailySpend: Int,
    val weeklyTrend: Trend,
    val insight: String
)

fun computeAnalytics(
    totalBudget: Int,
    currentBalance: Int,
    categoryTotals: Map<String, Int>,
    history: String
): SpendingAnalytics {
    val monthlySpend = (totalBudget - currentBalance).coerceAtLeast(0)
    val usagePct = if (totalBudget > 0) (monthlySpend * 100 / totalBudget) else 0
    
    val highestEntry = categoryTotals.maxByOrNull { it.value }
    val highestCat = if (highestEntry != null && highestEntry.value > 0) highestEntry.key else "None"
    
    val calendar = java.util.Calendar.getInstance()
    val dayOfMonth = calendar[java.util.Calendar.DAY_OF_MONTH]
    val avgDaily = monthlySpend / dayOfMonth
    
    // Trend: compare count of lines (proxy for activity)
    val lines = history.split("\n").filter { it.isNotEmpty() }
    val weeklyTrend = when {
        lines.size > 12 -> Trend.UP
        lines.size > 6  -> Trend.STABLE
        else            -> Trend.DOWN
    }
    
    // Insights generator
    val insight = when {
        usagePct > 85 -> "Emergency: Budget almost exhausted. Rapid spending detected."
        highestCat != "None" && ((categoryTotals[highestCat] ?: 0) > (monthlySpend * 0.45)) -> 
            "$highestCat spending is disproportionately high this month."
        avgDaily > 2000 -> "High velocity spending. Your daily average is above threshold."
        lines.size > 20 -> "Hyper-active transaction history. Consider consolidating purchases."
        (usagePct < 30) && (dayOfMonth > 15) -> "Excellent budget control. You are well below monthly targets."
        else -> "Financial health is optimal. Spending patterns remain predictable."
    }
    
    return SpendingAnalytics(monthlySpend, usagePct, highestCat, avgDaily, weeklyTrend, insight)
}

@Composable
fun AnalyticsCard(analytics: SpendingAnalytics) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("FINANCIAL INTELLIGENCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentCyan, letterSpacing = 1.2.sp)
            
            Column {
                Text(
                    text = "₹${formatAmount(analytics.monthlySpend)}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text("Total monthly expenditure", fontSize = 12.sp, color = TextSecondary)
            }
            
            HorizontalDivider(color = GlassStroke, thickness = 1.dp)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AnalyticsItem("Dominant Category", analytics.highestCategory, Modifier.weight(1f))
                AnalyticsItem("Daily Burn Rate", "₹${formatAmount(analytics.avgDailySpend)}", Modifier.weight(1f))
            }
            
            // AI Insight Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentBlue.copy(alpha = 0.08f))
                    .border(1.dp, AccentBlue.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🦞", fontSize = 20.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("AI Spending Insight", fontSize = 10.sp, color = AccentBlue, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        Text(analytics.insight, fontSize = 13.sp, color = TextPrimary.copy(alpha = 0.9f), lineHeight = 18.sp)
                    }
                }
            }
            
            // Trend Indicator
            val trendInfo = when(analytics.weeklyTrend) {
                Trend.UP -> Triple(DangerRed, "↑", "Spending velocity increasing")
                Trend.DOWN -> Triple(SafeGreen, "↓", "Spending velocity decreasing")
                Trend.STABLE -> Triple(AccentAmber, "→", "Spending velocity stable")
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(18.dp).background(trendInfo.first.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(trendInfo.second, fontSize = 12.sp, color = trendInfo.first, fontWeight = FontWeight.Bold)
                }
                Text(trendInfo.third, fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun AnalyticsItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}
