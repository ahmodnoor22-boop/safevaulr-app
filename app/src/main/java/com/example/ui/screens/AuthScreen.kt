package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.PendingPhoneAuthRequest
import com.example.ui.PhoneAuthMode
import com.example.ui.SafeVaultViewModel
import com.example.ui.VaultGateState
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.MintLight
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyHeroStart
import com.example.ui.theme.NavySurface
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMutedGray
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

object PhoneNumberFormatter {
    fun normalizeE164(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[\\s\\-()]"), "")
        return when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("00") -> "+${cleaned.drop(2)}"
            else -> "+$cleaned"
        }
    }

    fun isValidPhone(raw: String): Boolean {
        val normalized = normalizeE164(raw)
        return normalized.matches(Regex("^\\+[1-9]\\d{7,14}$"))
    }
}

// ------------------------------------------------------------------------
// 1. Firebase Phone Auth Screen (Login, Register, Forgot Password — NO Gmail/Google redirect)
// ------------------------------------------------------------------------

@Composable
fun PhoneAuthLoginScreen(
    onPreparePendingAuth: (PendingPhoneAuthRequest) -> Unit,
    onPhoneAuthCompleted: () -> Unit,
    initialMode: PhoneAuthMode = PhoneAuthMode.LOGIN,
    initialPhoneNumber: String = "+",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember(initialPhoneNumber) { mutableStateOf(initialPhoneNumber) }
    var password by remember { mutableStateOf("") }
    var referralCode by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // OTP Verification State
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var resendSecondsLeft by remember { mutableIntStateOf(0) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    // 60-second live OTP resend countdown timer
    LaunchedEffect(resendSecondsLeft) {
        if (resendSecondsLeft > 0) {
            delay(1000L)
            resendSecondsLeft -= 1
        }
    }

    fun buildPendingRequest(): PendingPhoneAuthRequest {
        val normalizedPhone = PhoneNumberFormatter.normalizeE164(phoneNumber)
        return PendingPhoneAuthRequest(
            mode = mode,
            fullName = fullName.trim().ifBlank { normalizedPhone },
            phoneNumber = normalizedPhone,
            password = password,
            referralCode = referralCode.trim().uppercase()
        )
    }

    fun sendSmsOtp(isResend: Boolean) {
        errorMessage = null
        infoMessage = null

        if (mode == PhoneAuthMode.REGISTER && fullName.trim().isEmpty()) {
            errorMessage = "Please enter your full name."
            return
        }
        if (!PhoneNumberFormatter.isValidPhone(phoneNumber)) {
            errorMessage = "Enter a valid mobile number with country code (e.g. +8801712345678 or +15551234567)."
            return
        }
        if (password.length < 6) {
            errorMessage = when (mode) {
                PhoneAuthMode.FORGOT_PASSWORD -> "New password must be at least 6 characters."
                else -> "Password must be at least 6 characters."
            }
            return
        }
        if (activity == null) {
            errorMessage = "Activity context unavailable for Firebase Phone Verification."
            return
        }

        val formattedPhone = PhoneNumberFormatter.normalizeE164(phoneNumber)
        isLoading = true

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                coroutineScope.launch {
                    try {
                        onPreparePendingAuth(buildPendingRequest())
                        Firebase.auth.signInWithCredential(credential).await()
                        isLoading = false
                        onPhoneAuthCompleted()
                    } catch (e: Exception) {
                        isLoading = false
                        errorMessage = e.localizedMessage ?: "Automatic phone sign-in failed."
                    }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                isLoading = false
                val rawMsg = e.localizedMessage ?: "SMS verification failed."
                errorMessage = when {
                    e is FirebaseAuthInvalidCredentialsException ->
                        "Invalid mobile number format. Include your country code (e.g. +880...)."
                    rawMsg.contains("not enabled", ignoreCase = true) ||
                        rawMsg.contains("not allowed", ignoreCase = true) ||
                        rawMsg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ->
                        "Enable 'Phone' in Firebase Console -> Authentication -> Sign-in method to deliver real SMS OTPs."
                    rawMsg.contains("quota", ignoreCase = true) ||
                        rawMsg.contains("blocked", ignoreCase = true) ->
                        "Too many SMS verification requests. Please wait a bit before trying again."
                    else -> rawMsg
                }
            }

            override fun onCodeSent(
                verId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                isLoading = false
                verificationId = verId
                resendToken = token
                resendSecondsLeft = 60
                otpCode = ""
                infoMessage = "A 6-digit SMS OTP has been sent to $formattedPhone."
            }
        }

        val optionsBuilder = PhoneAuthOptions.newBuilder(Firebase.auth)
            .setPhoneNumber(formattedPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        if (isResend && resendToken != null) {
            optionsBuilder.setForceResendingToken(resendToken!!)
        }

        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
    }

    fun verifyOtpAndSignIn() {
        val verId = verificationId
        if (verId.isNullOrBlank()) {
            errorMessage = "Please request an SMS OTP first."
            return
        }
        val cleanOtp = otpCode.trim()
        if (cleanOtp.length != 6 || !cleanOtp.all { it.isDigit() }) {
            errorMessage = "Enter the 6-digit OTP received via SMS."
            return
        }

        isLoading = true
        errorMessage = null
        val credential = PhoneAuthProvider.getCredential(verId, cleanOtp)
        coroutineScope.launch {
            try {
                onPreparePendingAuth(buildPendingRequest())
                Firebase.auth.signInWithCredential(credential).await()
                isLoading = false
                onPhoneAuthCompleted()
            } catch (e: Exception) {
                isLoading = false
                errorMessage = when (e) {
                    is FirebaseAuthInvalidCredentialsException ->
                        "Invalid or expired 6-digit OTP code. Please check your SMS or resend."
                    else -> e.localizedMessage ?: "OTP verification failed."
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(NavyDeep, NavyHeroStart, NavySurface)
                )
            )
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SafeVaultLogoBadge(size = 90.dp)

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "SafeVault Cloud",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Verified Mobile & Password Cloud Storage",
                fontSize = 12.sp,
                color = MintLight,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Auth Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF0E1C33))
                    .border(1.dp, Color(0x3300E5C9), RoundedCornerShape(26.dp))
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Mode Switcher (Login | Register) when OTP not yet sent
                    if (verificationId == null && mode != PhoneAuthMode.FORGOT_PASSWORD) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF152847))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ModeTabButton(
                                title = "Mobile Login",
                                selected = mode == PhoneAuthMode.LOGIN,
                                onClick = {
                                    mode = PhoneAuthMode.LOGIN
                                    errorMessage = null
                                    infoMessage = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_tab_login")
                            )
                            ModeTabButton(
                                title = "Register",
                                selected = mode == PhoneAuthMode.REGISTER,
                                onClick = {
                                    mode = PhoneAuthMode.REGISTER
                                    errorMessage = null
                                    infoMessage = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_tab_register")
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                    } else {
                        Text(
                            text = when {
                                verificationId != null -> "Verify 6-Digit SMS OTP"
                                mode == PhoneAuthMode.FORGOT_PASSWORD -> "Reset Vault Password"
                                else -> "SafeVault Login"
                            },
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (verificationId == null) {
                        if (mode == PhoneAuthMode.REGISTER) {
                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { fullName = it },
                                label = { Text("Full Name", color = TextMutedGray) },
                                leadingIcon = { Icon(Icons.Default.Person, null, tint = CyanAccent) },
                                singleLine = true,
                                colors = vaultFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_name_input")
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Mobile Number (with Country Code)", color = TextMutedGray) },
                            placeholder = { Text("+8801712345678", color = TextMutedGray) },
                            leadingIcon = { Icon(Icons.Default.Phone, null, tint = CyanAccent) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            colors = vaultFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mobile_login_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = {
                                Text(
                                    text = if (mode == PhoneAuthMode.FORGOT_PASSWORD) "New Password (min 6 chars)" else "Password",
                                    color = TextMutedGray
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = CyanAccent) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle password visibility",
                                        tint = TextMutedGray
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            colors = vaultFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input")
                        )

                        if (mode == PhoneAuthMode.REGISTER) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = referralCode,
                                onValueChange = { referralCode = it.uppercase() },
                                label = { Text("Referral Code (Optional)", color = TextMutedGray) },
                                placeholder = { Text("e.g. SVABC123", color = TextMutedGray) },
                                singleLine = true,
                                colors = vaultFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_referral_input")
                            )
                        }

                        if (mode == PhoneAuthMode.LOGIN) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    mode = PhoneAuthMode.FORGOT_PASSWORD
                                    errorMessage = null
                                    infoMessage = null
                                }) {
                                    Text("Forgot Password?", color = CyanAccent, fontSize = 12.sp)
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        Button(
                            onClick = { sendSmsOtp(isResend = false) },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("send_mobile_otp_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = NavyDeep,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sms,
                                    contentDescription = null,
                                    tint = NavyDeep,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (mode) {
                                        PhoneAuthMode.LOGIN -> "Send SMS OTP & Login"
                                        PhoneAuthMode.REGISTER -> "Send SMS OTP & Register"
                                        PhoneAuthMode.FORGOT_PASSWORD -> "Send Reset SMS OTP"
                                    },
                                    color = NavyDeep,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        if (mode == PhoneAuthMode.FORGOT_PASSWORD) {
                            TextButton(
                                onClick = {
                                    mode = PhoneAuthMode.LOGIN
                                    errorMessage = null
                                }
                            ) {
                                Text("Back to Login", color = TextMutedGray, fontSize = 12.sp)
                            }
                        }
                    } else {
                        // Step 2: 6-Digit SMS OTP Entry + Live Countdown + Resend
                        Text(
                            text = "Enter the 6-digit verification code sent via SMS to ${PhoneNumberFormatter.normalizeE164(phoneNumber)}",
                            fontSize = 12.sp,
                            color = Color(0xFFBAC7DB),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { input ->
                                val digits = input.filter { it.isDigit() }
                                if (digits.length <= 6) otpCode = digits
                            },
                            label = { Text("6-Digit SMS OTP", color = TextMutedGray) },
                            leadingIcon = { Icon(Icons.Default.Sms, null, tint = CyanAccent) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = vaultFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phone_auth_otp_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { sendSmsOtp(isResend = true) },
                                enabled = resendSecondsLeft == 0 && !isLoading,
                                modifier = Modifier.testTag("resend_otp_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = if (resendSecondsLeft == 0) CyanAccent else TextMutedGray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (resendSecondsLeft > 0) {
                                        "Resend OTP in ${resendSecondsLeft}s"
                                    } else {
                                        "Resend OTP"
                                    },
                                    color = if (resendSecondsLeft == 0) CyanAccent else TextMutedGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            TextButton(
                                onClick = {
                                    verificationId = null
                                    otpCode = ""
                                    resendSecondsLeft = 0
                                    infoMessage = null
                                    errorMessage = null
                                }
                            ) {
                                Text("Edit Number", color = TextMutedGray, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { verifyOtpAndSignIn() },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("phone_auth_verify_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = NavyDeep,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = when (mode) {
                                        PhoneAuthMode.LOGIN -> "Verify OTP & Restore Data"
                                        PhoneAuthMode.REGISTER -> "Verify OTP & Create Account"
                                        PhoneAuthMode.FORGOT_PASSWORD -> "Verify OTP & Reset Password"
                                    },
                                    color = NavyDeep,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    if (infoMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = infoMessage!!,
                            fontSize = 12.sp,
                            color = StatusGreen,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage!!,
                            fontSize = 12.sp,
                            color = StatusRed,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeTabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) CyanAccent else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = if (selected) NavyDeep else Color(0xFFBAC7DB),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}

// ------------------------------------------------------------------------
// 2. Post-Phone-Auth Vault Gate (Password Verification on Existing Session / Cold Start)
// ------------------------------------------------------------------------

@Composable
fun VaultCredentialsGateScreen(
    viewModel: SafeVaultViewModel,
    onSignOutRequested: () -> Unit,
    onResetPasswordViaOtp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val gateState by viewModel.vaultGateState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isVaultBusy.collectAsStateWithLifecycle()
    val cloudUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val firebasePhone = remember { Firebase.auth.currentUser?.phoneNumber.orEmpty() }

    var fullName by remember(cloudUser?.fullName) {
        mutableStateOf(cloudUser?.fullName.orEmpty())
    }
    var phone by remember(cloudUser?.phoneNumber, firebasePhone) {
        mutableStateOf(cloudUser?.phoneNumber?.ifBlank { firebasePhone } ?: firebasePhone)
    }
    var password by remember { mutableStateOf("") }
    var gateReferralCode by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(NavyDeep, NavyHeroStart, NavySurface))
            )
            .statusBarsPadding()
            .imePadding()
    ) {
        if (gateState == VaultGateState.CHECKING) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                SafeVaultLogoBadge(size = 84.dp)
                Spacer(modifier = Modifier.height(18.dp))
                CircularProgressIndicator(color = CyanAccent)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Verifying SafeVault Cloud Profile...",
                    color = MintLight,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                SafeVaultLogoBadge(size = 84.dp)

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = when (gateState) {
                        VaultGateState.REGISTER_MOBILE_PROFILE -> "Complete Mobile Vault Setup"
                        VaultGateState.FORGOT_PASSWORD_OTP -> "Reset Vault Password"
                        else -> "Unlock SafeVault"
                    },
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = when (gateState) {
                        VaultGateState.REGISTER_MOBILE_PROFILE -> "Confirm your name and password for your verified mobile number"
                        VaultGateState.FORGOT_PASSWORD_OTP -> "Set a new password for your verified mobile account"
                        else -> "Enter your verified mobile number and password to unlock & restore"
                    },
                    fontSize = 12.sp,
                    color = MintLight,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF0E1C33))
                        .border(1.dp, Color(0x3300E5C9), RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        if (gateState == VaultGateState.REGISTER_MOBILE_PROFILE) {
                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { fullName = it },
                                label = { Text("Full Name", color = TextMutedGray) },
                                leadingIcon = { Icon(Icons.Default.Person, null, tint = CyanAccent) },
                                singleLine = true,
                                colors = vaultFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { if (firebasePhone.isBlank()) phone = it },
                            enabled = firebasePhone.isBlank(),
                            label = { Text("Verified Mobile Number", color = TextMutedGray) },
                            leadingIcon = { Icon(Icons.Default.Phone, null, tint = CyanAccent) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            colors = vaultFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gate_phone_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = {
                                Text(
                                    if (gateState == VaultGateState.FORGOT_PASSWORD_OTP) "New Password" else "Password",
                                    color = TextMutedGray
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = CyanAccent) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        null,
                                        tint = TextMutedGray
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            colors = vaultFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gate_password_input")
                        )

                        if (gateState == VaultGateState.REGISTER_MOBILE_PROFILE) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = gateReferralCode,
                                onValueChange = { gateReferralCode = it.uppercase() },
                                label = { Text("Referral Code (Optional)", color = TextMutedGray) },
                                singleLine = true,
                                colors = vaultFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (gateState == VaultGateState.UNLOCK_EXISTING_VAULT) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    onResetPasswordViaOtp(phone.ifBlank { "+" })
                                }) {
                                    Text("Reset Password via SMS OTP", color = CyanAccent, fontSize = 12.sp)
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Button(
                            onClick = {
                                val normalized = PhoneNumberFormatter.normalizeE164(phone)
                                when (gateState) {
                                    VaultGateState.REGISTER_MOBILE_PROFILE -> {
                                        if (password.length < 6) {
                                            viewModel.showFeedback("Password must be at least 6 characters.")
                                        } else {
                                            viewModel.registerVerifiedMobileProfile(fullName, normalized, password, gateReferralCode)
                                        }
                                    }
                                    VaultGateState.FORGOT_PASSWORD_OTP -> {
                                        if (password.length < 6) {
                                            viewModel.showFeedback("New password must be at least 6 characters.")
                                        } else {
                                            viewModel.resetPasswordAfterPhoneOtp(normalized, password)
                                        }
                                    }
                                    else -> {
                                        viewModel.unlockWithMobileAndPassword(normalized, password)
                                    }
                                }
                            },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("gate_submit_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(color = NavyDeep, modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    text = when (gateState) {
                                        VaultGateState.REGISTER_MOBILE_PROFILE -> "Activate SafeVault"
                                        VaultGateState.FORGOT_PASSWORD_OTP -> "Save New Password"
                                        else -> "Unlock & Restore All Cloud Data"
                                    },
                                    color = NavyDeep,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TextButton(
                            onClick = onSignOutRequested,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use Another Mobile Number", color = TextMutedGray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SafeVaultLogoBadge(
    size: Dp = 84.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.safevault_logo_model_1794540281473),
        contentDescription = "SafeVault Logo",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.5.dp, Color(0x6600E5C9), CircleShape)
    )
}

@Composable
private fun vaultFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CyanAccent,
    unfocusedBorderColor = Color(0xFF2A3D5D),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = CyanAccent,
    disabledTextColor = MintLight,
    disabledBorderColor = Color(0xFF243757)
)
