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

    // Auto-refresh pending payment from SharedPreferences
    LaunchedEffect(Unit) {
        while (true) {
            pendingPayment = sharedPreferences.getInt("PENDING_PAYMENT", 0)
            delay(1000)
        }
    }

    val spentAmount         = totalBudget - currentBalance
    val budgetUsagePercent  = if (totalBudget > 0) ((spentAmount.toFloat() / totalBudget.toFloat()) * 100).toInt() else 0

    val riskLevel = when {
        budgetUsagePercent >= 85 -> RiskLevel.DANGER
        budgetUsagePercent >= 60 -> RiskLevel.CAUTION
        else                     -> RiskLevel.SAFE
    }

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
            // Ambient background blobs
            AmbientBackground()

            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(Modifier.height(56.dp)) }

                // ── Header ──────────────────────────────────────────────────
                item { HeaderSection() }

                // ── AI Status Bar ────────────────────────────────────────────
                item { AiStatusBar() }

                // ── Balance Card ─────────────────────────────────────────────
                item {
                    BalanceCard(
                        totalBudget        = totalBudget,
                        currentBalance     = currentBalance,
                        spentAmount        = spentAmount,
                        budgetUsagePercent = budgetUsagePercent,
                        riskLevel          = riskLevel
                    )
                }

                // ── Analytics Row ────────────────────────────────────────────
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        RiskMeterCard(
                            modifier           = Modifier.weight(1f),
                            budgetUsagePercent = budgetUsagePercent,
                            riskLevel          = riskLevel
                        )
                        SpendingUsageCard(
                            modifier           = Modifier.weight(1f),
                            budgetUsagePercent = budgetUsagePercent,
                            spentAmount        = spentAmount
                        )
                    }
                }

                // ── Budget Input ─────────────────────────────────────────────
                item {
                    BudgetInputCard(
                        budgetInput = budgetInput,
                        onInputChange = { budgetInput = it },
                        onUpdate = {
                            if (budgetInput.isNotEmpty()) {
                                val addedAmount = budgetInput.toIntOrNull() ?: 0
                                if (addedAmount > 0) {
                                    totalBudget    += addedAmount
                                    currentBalance += addedAmount
                                    sharedPreferences.edit {
                                        putInt("TOTAL_BUDGET",    totalBudget)
                                        putInt("CURRENT_BALANCE", currentBalance)
                                    }
                                    budgetInput = ""
                                }
                            }
                        }
                    )
                }

                // ── Pending Payment ──────────────────────────────────────────
                if (pendingPayment > 0) {
                    item {

                        CategoryPieChartCard(
                            context = context
                        )
                    }



                    item {
                        PendingPaymentCard(
                            pendingPayment = pendingPayment,
                            onYes = {
                                currentBalance -= pendingPayment
                                if (currentBalance < 0) currentBalance = 0
                                sharedPreferences.edit {
                                    putInt("CURRENT_BALANCE", currentBalance)
                                    putInt("PENDING_PAYMENT", 0)
                                }
                                pendingPayment = 0
                            },
                            onNo = {
                                sharedPreferences.edit { putInt("PENDING_PAYMENT", 0) }
                                pendingPayment = 0
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
            // Glowing orb logo
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            brush  = Brush.radialGradient(listOf(AccentBlue.copy(alpha = 0.4f), Color.Transparent)),
                            shape  = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
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
            modifier = Modifier
                .size(7.dp)
                .background(color.copy(alpha = dotAlpha), CircleShape)
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
        // Top accent bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
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

            // Animated balance
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

            // Progress bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Spent: ₹${formatAmount(spentAmount)}", fontSize = 12.sp, color = TextSecondary)
                    Text("$budgetUsagePercent% used", fontSize = 12.sp, color = accentColor, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF1E2A40))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(accentColor.copy(alpha = 0.7f), accentColor)
                                )
                            )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Budget / Spent row
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
                    // Track
                    drawArc(
                        color      = Color(0xFF1E2A40),
                        startAngle = startAngle,
                        sweepAngle = 240f,
                        useCenter  = false,
                        style      = Stroke(width = stroke, cap = StrokeCap.Round),
                        topLeft    = Offset(inset, inset),
                        size       = this.size.copy(width = this.size.width - stroke, height = this.size.height - stroke)
                    )
                    // Fill
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
            Text(
                text      = "of budget used",
                fontSize  = 11.sp,
                color     = TextSecondary
            )
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
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(2.5.dp))
            .background(Color(0xFF1E2A40))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.5.dp))
                .background(barColor)
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
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(AccentCyan, CircleShape)
                )
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

            // Neon button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(listOf(AccentBlue, AccentCyan))
                    )
                    .clickable { onUpdate() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = "Update Budget",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = BgDeep,
                    letterSpacing = 0.3.sp
                )
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF150A10))
            .border(1.5.dp, DangerRed.copy(alpha = glowAlpha + 0.2f), RoundedCornerShape(24.dp))
    ) {
        // Red glow top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    Brush.horizontalGradient(listOf(DangerRed.copy(alpha = 0.6f), DangerRed, DangerRed.copy(alpha = 0.6f))),
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
        )

        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(DangerRed.copy(alpha = glowAlpha + 0.4f), CircleShape)
                )
                Text("Pending Payment", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DangerRed, letterSpacing = 0.3.sp)
            }

            Text(
                text       = "₹${formatAmount(pendingPayment)}",
                fontSize   = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = TextPrimary
            )

            Text(
                text     = "OpenClaw AI detected a payment. Did you complete this transaction?",
                fontSize = 13.sp,
                color    = TextSecondary,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // YES
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DangerRed)
                        .clickable { onYes() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("YES, PAID", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                // NO
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E2030))
                        .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
                        .clickable { onNo() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("NO, SKIP", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                }
            }
        }
    }
}

// ─── Reusable Glass Card ──────────────────────────────────────────────────────

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(BgCardAlt)
            .border(1.dp, GlassStroke, RoundedCornerShape(22.dp))
    ) {
        content()
    }
}

// ─── Risk Level Enum ──────────────────────────────────────────────────────────

enum class RiskLevel(val label: String, val color: Color) {
    SAFE("Safe",    SafeGreen),
    CAUTION("Caution", AccentAmber),
    DANGER("Danger", DangerRed)
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

fun formatAmount(amount: Int): String {
    return when {
        amount >= 100_000 -> "%.1fL".format(amount / 100_000f)
        amount >= 1_000   -> "%.1fK".format(amount / 1_000f)
        else              -> amount.toString()
    }
}
@Composable
fun CategoryPieChartCard(
    context: Context
) {

    val sharedPreferences =

        context.getSharedPreferences(
            "FinanceGuardian",
            Context.MODE_PRIVATE
        )

    val food =
        sharedPreferences.getInt(
            "Food",
            0
        )

    val shopping =
        sharedPreferences.getInt(
            "Shopping",
            0
        )

    val utilities =
        sharedPreferences.getInt(
            "Utilities",
            0
        )

    val personal =
        sharedPreferences.getInt(
            "Personal",
            0
        )

    val others =
        sharedPreferences.getInt(
            "Others",
            0
        )

    val total =
        food +
                shopping +
                utilities +
                personal +
                others

    GlassCard(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Column(

            modifier =
                Modifier.padding(20.dp)

        ) {

            Text(

                text =
                    "Categorical Spend",

                fontSize = 18.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    TextPrimary
            )

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            if (total == 0) {

                Text(

                    text =
                        "No spending data yet",

                    color =
                        TextSecondary
                )

            } else {

                Canvas(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp)

                ) {

                    val values = listOf(
                        food,
                        shopping,
                        utilities,
                        personal,
                        others
                    )

                    val colors = listOf(
                        AccentCyan,
                        AccentPurple,
                        AccentAmber,
                        SafeGreen,
                        DangerRed
                    )

                    var startAngle = 0f

                    values.forEachIndexed { index, value ->

                        val sweepAngle =

                            (value.toFloat() /
                                    total.toFloat()) * 360f

                        drawArc(

                            color =
                                colors[index],

                            startAngle =
                                startAngle,

                            sweepAngle =
                                sweepAngle,

                            useCenter =
                                true
                        )

                        startAngle += sweepAngle
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(20.dp)
                )

                CategoryLegend(
                    "Food",
                    food,
                    AccentCyan
                )

                CategoryLegend(
                    "Shopping",
                    shopping,
                    AccentPurple
                )

                CategoryLegend(
                    "Utilities",
                    utilities,
                    AccentAmber
                )

                CategoryLegend(
                    "Personal",
                    personal,
                    SafeGreen
                )

                CategoryLegend(
                    "Others",
                    others,
                    DangerRed
                )
            }
        }
    }
}
@Composable
fun CategoryLegend(
    label: String,
    amount: Int,
    color: Color
) {

    Row(

        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),

        horizontalArrangement =
            Arrangement.SpaceBetween,

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(

                modifier =
                    Modifier
                        .size(12.dp)
                        .background(
                            color,
                            CircleShape
                        )
            )

            Spacer(
                modifier =
                    Modifier.width(8.dp)
            )

            Text(

                text = label,

                color =
                    TextPrimary
            )
        }

        Text(

            text =
                "₹$amount",

            color =
                TextSecondary
        )
    }
}




