package com.ultimatepro.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultimatepro.ui.common.AppButton
import com.ultimatepro.ui.common.AppColors
import com.ultimatepro.ui.common.ShineHairline

@Composable
fun LoginScreen(
    onSuccess: () -> Unit,
    onRegister: () -> Unit,
    vm: AuthViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val focus = LocalFocusManager.current
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw   by remember { mutableStateOf(false) }

    LaunchedEffect(state.done) { if (state.done) onSuccess() }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppColors.Blue, AppColors.BlueDark)))
    ) {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // Brand
            UltimateProLogoMark(Modifier.size(72.dp, 86.dp))
            Spacer(Modifier.height(14.dp))
            Text("UltimatePro", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Text("Field Service Management", color = Color.White.copy(0.7f), fontSize = 14.sp)

            Spacer(Modifier.height(48.dp))

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(8.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text("Sign In", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Welcome back", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = email, onValueChange = { email = it },
                        label = { Text("Email") }, leadingIcon = { Icon(Icons.Default.Email, null) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text("Password") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = { IconButton(onClick = { showPw = !showPw }) {
                            Icon(if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (email.isNotBlank() && password.isNotBlank()) vm.login(email, password) })
                    )

                    AnimatedVisibility(visible = state.error != null) {
                        state.error?.let { err ->
                            Spacer(Modifier.height(8.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(8.dp)) {
                                Text(err, color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    AppButton(
                        onClick = { vm.login(email, password) },
                        label = "Sign In",
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = email.isNotBlank() && password.isNotBlank() && !state.loading,
                        loading = state.loading
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onRegister) {
                Text("Don't have an account? ", color = Color.White.copy(0.75f))
                Text("Sign Up", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    vm: AuthViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var company     by remember { mutableStateOf("") }
    var first       by remember { mutableStateOf("") }
    var last        by remember { mutableStateOf("") }
    var email       by remember { mutableStateOf("") }
    var phone       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var inviteCode  by remember { mutableStateOf("") }
    var showPw      by remember { mutableStateOf(false) }

    LaunchedEffect(state.done) { if (state.done) onSuccess() }

    Scaffold(topBar = {
        Column {
        TopAppBar(title = { Text("Create Account") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
        ShineHairline()
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.height(8.dp))

            @Composable fun Field(label: String, value: String, onChange: (String) -> Unit,
                                  icon: androidx.compose.ui.graphics.vector.ImageVector,
                                  kbType: KeyboardType = KeyboardType.Text, isPw: Boolean = false) {
                var vis by remember { mutableStateOf(false) }
                OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
                    leadingIcon = { Icon(icon, null) }, singleLine = true,
                    trailingIcon = if (isPw) { { IconButton(onClick = { vis = !vis }) {
                        Icon(if (vis) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } } } else null,
                    visualTransformation = if (isPw && !vis) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = kbType),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }

            Field("Company Name", company, { company = it }, Icons.Default.Business)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(first, { first = it }, label = { Text("First Name") },
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(last, { last = it }, label = { Text("Last Name") },
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
            }
            Field("Email", email, { email = it }, Icons.Default.Email, KeyboardType.Email)
            Field("Phone", phone, { phone = it }, Icons.Default.Phone, KeyboardType.Phone)
            Field("Password (8+ chars)", password, { password = it }, Icons.Default.Lock, isPw = true)
            Field("Invite Code", inviteCode, { inviteCode = it }, Icons.Default.VpnKey)
            Text(
                "Contact the administrator for an invite code",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            state.error?.let { err ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(8.dp)) {
                    Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            AppButton(
                onClick = { vm.register(company, first, last, email, phone, password, inviteCode) },
                label = "Create Account",
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = listOf(company, first, email, password, inviteCode).all { it.isNotBlank() } && !state.loading,
                loading = state.loading
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun UltimateProLogoMark(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val pad = 0.88f
        val sc  = minOf(size.width * pad / 180f, size.height * pad / 208f)
        val ox  = (size.width  - 180f * sc) / 2f
        val oy  = (size.height - 208f * sc) / 2f
        fun fx(v: Float) = (v - 20f) * sc + ox
        fun fy(v: Float) = (v - 10f) * sc + oy
        fun sw(v: Float) = maxOf(0.5f, v * sc)

        val outerPts = listOf(110f to 10f, 200f to 62f, 200f to 166f, 110f to 218f, 20f to 166f, 20f to 62f)
        val innerPts = listOf(110f to 26f, 188f to 72f, 188f to 156f, 110f to 202f, 32f to 156f, 32f to 72f)

        fun hexPath(pts: List<Pair<Float, Float>>) = Path().apply {
            moveTo(fx(pts[0].first), fy(pts[0].second))
            for (i in 1 until pts.size) lineTo(fx(pts[i].first), fy(pts[i].second))
            close()
        }

        // Outer hex fill
        drawPath(hexPath(outerPts), color = Color(0xFF0B1824))

        // Inner hex rule
        drawPath(hexPath(innerPts), color = Color(0xFF162E4A),
            style = Stroke(width = sw(1.5f), join = StrokeJoin.Round))

        // U shape (55% opacity)
        drawPath(Path().apply {
            moveTo(fx(62f), fy(60f))
            lineTo(fx(62f), fy(130f))
            quadraticBezierTo(fx(62f), fy(168f), fx(110f), fy(168f))
            quadraticBezierTo(fx(158f), fy(168f), fx(158f), fy(130f))
            lineTo(fx(158f), fy(60f))
        }, color = Color(0x8C1A3A60), style = Stroke(width = sw(8f), cap = StrokeCap.Round))

        // C shape (38% opacity)
        drawPath(Path().apply {
            moveTo(fx(148f), fy(72f))
            quadraticBezierTo(fx(178f), fy(114f), fx(148f), fy(156f))
        }, color = Color(0x6112304E), style = Stroke(width = sw(6f), cap = StrokeCap.Round))

        // Compass arms
        drawLine(Color(0xFF3A7BD5), Offset(fx(52f), fy(162f)), Offset(fx(110f), fy(76f)), strokeWidth = sw(4.5f), cap = StrokeCap.Round)
        drawLine(Color(0xFF3A7BD5), Offset(fx(168f), fy(162f)), Offset(fx(110f), fy(76f)), strokeWidth = sw(4.5f), cap = StrokeCap.Round)

        // Compass spread bar (70% opacity)
        drawLine(Color(0xB23A7BD5), Offset(fx(70f), fy(136f)), Offset(fx(150f), fy(136f)), strokeWidth = sw(2.5f), cap = StrokeCap.Round)

        // Compass feet
        drawCircle(Color(0xFF1E4A80), radius = sw(4f), center = Offset(fx(52f), fy(162f)))
        drawCircle(Color(0xFF1E4A80), radius = sw(4f), center = Offset(fx(168f), fy(162f)))

        // Compass pivot
        drawCircle(Color(0xFF2A6AAF), radius = sw(5f), center = Offset(fx(110f), fy(76f)))

        // Level bar fill + stroke
        val bx = fx(42f); val by = fy(108f)
        val bw = fx(178f) - fx(42f); val bh = fy(123f) - fy(108f)
        val br = CornerRadius(sw(7.5f))
        drawRoundRect(Color(0xFF0E2038), topLeft = Offset(bx, by), size = Size(bw, bh), cornerRadius = br)
        drawRoundRect(Color(0xFF4A90D9), topLeft = Offset(bx, by), size = Size(bw, bh), cornerRadius = br,
            style = Stroke(width = sw(2.8f)))

        // Level ticks
        drawLine(Color(0xFF1A4070), Offset(fx(76f), fy(108f)), Offset(fx(76f), fy(123f)), strokeWidth = sw(1.5f))
        drawLine(Color(0xFF1A4070), Offset(fx(144f), fy(108f)), Offset(fx(144f), fy(123f)), strokeWidth = sw(1.5f))
        drawLine(Color(0xFF152E50), Offset(fx(93f), fy(108f)), Offset(fx(93f), fy(116f)), strokeWidth = sw(1.2f))
        drawLine(Color(0xFF152E50), Offset(fx(127f), fy(108f)), Offset(fx(127f), fy(116f)), strokeWidth = sw(1.2f))

        // Level bubble ring (red)
        drawCircle(Color(0xFFE63946), radius = sw(5.5f), center = Offset(fx(110f), fy(115f)),
            style = Stroke(width = sw(2.5f)))

        // Level bubble dot (red)
        drawCircle(Color(0xFFE63946), radius = sw(2f), center = Offset(fx(110f), fy(115f)))

        // Tape measure ticks
        drawLine(Color(0xFF1E4A7A), Offset(fx(50f), fy(172f)), Offset(fx(50f), fy(180f)), strokeWidth = sw(2f))
        drawLine(Color(0xFF1E4A7A), Offset(fx(82f), fy(172f)), Offset(fx(82f), fy(180f)), strokeWidth = sw(2f))
        drawLine(Color(0xFF2A5A8A), Offset(fx(110f), fy(172f)), Offset(fx(110f), fy(182f)), strokeWidth = sw(2.5f))
        drawLine(Color(0xFF1E4A7A), Offset(fx(138f), fy(172f)), Offset(fx(138f), fy(180f)), strokeWidth = sw(2f))
        drawLine(Color(0xFF1E4A7A), Offset(fx(170f), fy(172f)), Offset(fx(170f), fy(180f)), strokeWidth = sw(2f))

        // Outer hex stroke (on top)
        drawPath(hexPath(outerPts), color = Color(0xFF3A7BD5),
            style = Stroke(width = sw(5f), join = StrokeJoin.Round))
    }
}
