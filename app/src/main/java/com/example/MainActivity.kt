package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.SafeVaultAdminRepository
import com.example.data.repository.SafeVaultCloudRepository
import com.example.ui.AdminViewModel
import com.example.ui.BottomTab
import com.example.ui.PendingPhoneAuthRequest
import com.example.ui.SafeVaultViewModel
import com.example.ui.VaultGateState
import com.example.ui.components.CreateFolderDialog
import com.example.ui.components.CreateNoteDialog
import com.example.ui.components.CreateVaultItemDialog
import com.example.ui.components.FileDetailDialog
import com.example.ui.components.SafeVaultBottomNavBar
import com.example.ui.components.UploadModalSheet
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.PhoneAuthLoginScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.RecentActivityScreen
import com.example.ui.screens.VaultCredentialsGateScreen
import com.example.ui.screens.admin.AdminPanelScreen
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.MintLight
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyHeroStart
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SafeVaultTheme
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMutedGray
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafeVaultTheme {
                AppNavigation()
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val authStateFlow = remember { Firebase.auth.authStateFlow() }
    val currentFirebaseUser by authStateFlow.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
    var pendingAuthRequest by remember { mutableStateOf<PendingPhoneAuthRequest?>(null) }
    var initialAuthMode by remember { mutableStateOf(com.example.ui.PhoneAuthMode.LOGIN) }
    var initialPhonePrefill by remember { mutableStateOf("+") }

    val activeUser = currentFirebaseUser
    if (activeUser == null) {
        PhoneAuthLoginScreen(
            onPreparePendingAuth = { pending -> pendingAuthRequest = pending },
            onPhoneAuthCompleted = {
                initialAuthMode = com.example.ui.PhoneAuthMode.LOGIN
                initialPhonePrefill = "+"
            },
            initialMode = initialAuthMode,
            initialPhoneNumber = initialPhonePrefill
        )
    } else {
        // Keyed ViewModel strictly scoped to authenticated Firebase Phone Auth UID
        val vaultViewModel: SafeVaultViewModel = viewModel(
            key = activeUser.uid,
            factory = viewModelFactory {
                initializer {
                    SafeVaultViewModel(
                        repository = SafeVaultCloudRepository(appContext),
                        currentUserId = activeUser.uid,
                        initialPendingAuth = pendingAuthRequest,
                        onConsumePendingAuth = { pendingAuthRequest = null },
                        adminRepository = SafeVaultAdminRepository(appContext)
                    )
                }
            }
        )

        val userFeedbackMessage by vaultViewModel.userFeedbackMessage.collectAsStateWithLifecycle()
        val vaultGateState by vaultViewModel.vaultGateState.collectAsStateWithLifecycle()
        val currentUser by vaultViewModel.currentUser.collectAsStateWithLifecycle()
        val currentAdmin by vaultViewModel.currentAdmin.collectAsStateWithLifecycle()
        val appSettings by vaultViewModel.appSettings.collectAsStateWithLifecycle()

        var showAdminPanel by remember(activeUser.uid) { mutableStateOf(false) }

        LaunchedEffect(userFeedbackMessage) {
            userFeedbackMessage?.let {
                snackbarHostState.showSnackbar(it)
                vaultViewModel.clearFeedback()
            }
        }

        // Security guard: force-close Admin Panel immediately if privileges are revoked
        LaunchedEffect(currentAdmin?.isActive) {
            if (currentAdmin?.isActive != true) {
                showAdminPanel = false
            }
        }

        val handleSignOut: () -> Unit = {
            showAdminPanel = false
            pendingAuthRequest = null
            initialAuthMode = com.example.ui.PhoneAuthMode.LOGIN
            initialPhonePrefill = "+"
            vaultViewModel.prepareSignOut()
            Firebase.auth.signOut()
        }

        val handleResetPasswordViaOtp: (String) -> Unit = { prefillPhone ->
            showAdminPanel = false
            pendingAuthRequest = null
            initialAuthMode = com.example.ui.PhoneAuthMode.FORGOT_PASSWORD
            initialPhonePrefill = prefillPhone.ifBlank { "+" }
            vaultViewModel.prepareSignOut()
            Firebase.auth.signOut()
        }

        val handleDeleteAccount: () -> Unit = {
            coroutineScope.launch {
                val deleted = vaultViewModel.deleteAccountPermanently()
                if (deleted) {
                    showAdminPanel = false
                    pendingAuthRequest = null
                    initialAuthMode = com.example.ui.PhoneAuthMode.LOGIN
                    initialPhonePrefill = "+"
                    Firebase.auth.signOut()
                }
            }
        }

        val isBlockedUser = currentUser?.isBlocked == true && currentAdmin?.isActive != true
        val isMaintenanceBlocked = appSettings.maintenanceMode && currentAdmin?.isActive != true

        when {
            isBlockedUser -> {
                AccountBlockedScreen(
                    reason = currentUser?.blockReason.orEmpty(),
                    supportEmail = appSettings.supportEmail,
                    supportPhone = appSettings.supportPhone,
                    onSignOut = handleSignOut
                )
            }

            isMaintenanceBlocked -> {
                MaintenanceModeScreen(
                    appName = appSettings.appName,
                    message = appSettings.maintenanceMessage,
                    onSignOut = handleSignOut
                )
            }

            showAdminPanel && currentAdmin?.isActive == true -> {
                val adminViewModel: AdminViewModel = viewModel(
                    key = "admin_${activeUser.uid}",
                    factory = viewModelFactory {
                        initializer {
                            AdminViewModel(
                                adminRepository = SafeVaultAdminRepository(appContext),
                                currentUserId = activeUser.uid
                            )
                        }
                    }
                )
                AdminPanelScreen(
                    adminViewModel = adminViewModel,
                    onExitAdminPanel = { showAdminPanel = false }
                )
            }

            vaultGateState != VaultGateState.UNLOCKED -> {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = NavyDeep,
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    VaultCredentialsGateScreen(
                        viewModel = vaultViewModel,
                        onSignOutRequested = handleSignOut,
                        onResetPasswordViaOtp = handleResetPasswordViaOtp,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }

            else -> {
                MainScaffold(
                    viewModel = vaultViewModel,
                    snackbarHostState = snackbarHostState,
                    onSignOutClick = handleSignOut,
                    onDeleteAccountClick = handleDeleteAccount,
                    onOpenAdminPanel = {
                        if (currentAdmin?.isActive == true) {
                            showAdminPanel = true
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AccountBlockedScreen(
    reason: String,
    supportEmail: String,
    supportPhone: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NavyDeep, NavyHeroStart, NavySurface)))
            .statusBarsPadding()
            .padding(24.dp)
            .testTag("account_blocked_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = "Account Suspended",
                tint = StatusRed,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Account Suspended",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = reason.ifBlank { "Your account has been temporarily blocked by an administrator." },
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Contact Support: $supportEmail • $supportPhone",
                color = MintLight,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Sign Out", color = NavyDeep, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MaintenanceModeScreen(
    appName: String,
    message: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NavyDeep, NavyHeroStart, NavySurface)))
            .statusBarsPadding()
            .padding(24.dp)
            .testTag("maintenance_mode_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = "Maintenance Mode",
                tint = StatusAmber,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "$appName Maintenance",
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message.ifBlank { "Scheduled security maintenance is in progress. Please check back shortly." },
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Switch Account / Sign Out", color = NavyDeep, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    viewModel: SafeVaultViewModel,
    snackbarHostState: SnackbarHostState,
    onSignOutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    onOpenAdminPanel: () -> Unit
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val showUploadSheet by viewModel.showUploadSheet.collectAsStateWithLifecycle()
    val showNewFolderDialog by viewModel.showNewFolderDialog.collectAsStateWithLifecycle()
    val showNewNoteDialog by viewModel.showNewNoteDialog.collectAsStateWithLifecycle()
    val showNewVaultDialog by viewModel.showNewVaultDialog.collectAsStateWithLifecycle()
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()

    val uploadSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BackHandler(enabled = currentTab != BottomTab.HOME) {
        viewModel.setBottomTab(BottomTab.HOME)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = NavyDeep,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            SafeVaultBottomNavBar(
                selectedTab = currentTab,
                onTabSelected = { viewModel.setBottomTab(it) },
                onUploadClick = { viewModel.setShowUploadSheet(true) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Crossfade(targetState = currentTab, label = "tab_transition") { tab ->
                when (tab) {
                    BottomTab.HOME -> DashboardScreen(viewModel = viewModel)
                    BottomTab.MY_FILES -> FilesScreen(viewModel = viewModel)
                    BottomTab.UPLOAD -> DashboardScreen(viewModel = viewModel)
                    BottomTab.RECENT -> RecentActivityScreen(viewModel = viewModel)
                    BottomTab.PROFILE -> ProfileSettingsScreen(
                        viewModel = viewModel,
                        onSignOutClick = onSignOutClick,
                        onDeleteAccountClick = onDeleteAccountClick,
                        onOpenAdminPanel = onOpenAdminPanel
                    )
                }
            }
        }
    }

    if (showUploadSheet) {
        UploadModalSheet(
            viewModel = viewModel,
            sheetState = uploadSheetState,
            onDismiss = { viewModel.setShowUploadSheet(false) }
        )
    }

    if (showNewFolderDialog) {
        CreateFolderDialog(
            onDismiss = { viewModel.setShowNewFolderDialog(false) },
            onConfirm = { name, colorHex ->
                viewModel.setShowNewFolderDialog(false)
                viewModel.createFolder(name, colorHex)
            }
        )
    }

    if (showNewNoteDialog) {
        CreateNoteDialog(
            onDismiss = { viewModel.setShowNewNoteDialog(false) },
            onSave = { title, content, isEncrypted ->
                viewModel.setShowNewNoteDialog(false)
                viewModel.saveNote(title, content, isEncrypted)
            }
        )
    }

    if (showNewVaultDialog) {
        CreateVaultItemDialog(
            onDismiss = { viewModel.setShowNewVaultDialog(false) },
            onSave = { category, title, username, secret, notes ->
                viewModel.setShowNewVaultDialog(false)
                viewModel.addVaultItem(category, title, username, secret, notes)
            }
        )
    }

    selectedFile?.let { file ->
        FileDetailDialog(
            file = file,
            decryptedNoteText = viewModel.decryptNotePreview(file),
            onDismiss = { viewModel.selectFile(null) },
            onDownload = {
                viewModel.selectFile(null)
                viewModel.downloadAndRestoreFile(file)
            },
            onDelete = {
                viewModel.selectFile(null)
                viewModel.deleteFile(file)
            }
        )
    }
}
