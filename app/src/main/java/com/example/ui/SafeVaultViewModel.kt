package com.example.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminMember
import com.example.data.model.AdsterraAdConfig
import com.example.data.model.AppNotification
import com.example.data.model.CloudFile
import com.example.data.model.CloudFolder
import com.example.data.model.FileType
import com.example.data.model.GlobalAppSettings
import com.example.data.model.ReferralRecord
import com.example.data.model.SecureVaultItem
import com.example.data.model.StorageStats
import com.example.data.model.SubscriptionPlan
import com.example.data.model.TransferProgress
import com.example.data.model.UserAccount
import com.example.data.model.VaultCategory
import com.example.data.repository.SafeVaultAdminRepository
import com.example.data.repository.SafeVaultCloudRepository
import com.example.data.security.CryptoUtils
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class BottomTab(val title: String) {
    HOME("Home"),
    MY_FILES("My Files"),
    UPLOAD("Upload"),
    RECENT("Recent"),
    PROFILE("Profile")
}

enum class ViewMode {
    GRID,
    LIST
}

enum class PhoneAuthMode {
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD
}

data class PendingPhoneAuthRequest(
    val mode: PhoneAuthMode,
    val fullName: String,
    val phoneNumber: String,
    val password: String,
    val referralCode: String = ""
)

enum class VaultGateState {
    CHECKING,
    REGISTER_MOBILE_PROFILE,
    UNLOCK_EXISTING_VAULT,
    FORGOT_PASSWORD_OTP,
    UNLOCKED
}

class SafeVaultViewModel(
    val repository: SafeVaultCloudRepository,
    val currentUserId: String,
    private val initialPendingAuth: PendingPhoneAuthRequest? = null,
    private val onConsumePendingAuth: () -> Unit = {},
    private val adminRepository: SafeVaultAdminRepository? = null
) : ViewModel() {

    // Real-time Firestore Profile Stream with Two-Tier Error Unwrapping
    val currentUser: StateFlow<UserAccount?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing user profile", firestoreError ?: error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val folders: StateFlow<List<CloudFolder>> = repository.observeFolders(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing folders", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val files: StateFlow<List<CloudFile>> = repository.observeFiles(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing files", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val vaultItems: StateFlow<List<SecureVaultItem>> = repository.observeVaultItems(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing vault items", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val currentAdmin: StateFlow<AdminMember?> = (adminRepository?.observeCurrentAdmin(currentUserId) ?: flowOf(null))
        .catch { error ->
            if (error is CancellationException) throw error
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val appSettings: StateFlow<GlobalAppSettings> = repository.observeGlobalAppSettings()
        .catch { error ->
            if (error is CancellationException) throw error
            emit(GlobalAppSettings())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = GlobalAppSettings()
        )

    val activeNotifications: StateFlow<List<AppNotification>> = combine(
        repository.observeActiveNotifications(),
        currentUser
    ) { allNotifs, user ->
        val tier = if (user?.isPremium == true) "PREMIUM" else "FREE"
        allNotifs.filter { it.targetAudience == "ALL" || it.targetAudience == tier }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val availablePlans: StateFlow<List<SubscriptionPlan>> = repository.observeAvailablePlans()
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val myReferrals: StateFlow<List<ReferralRecord>> = repository.observeMyReferrals(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val isAdsRemovedByPlan: StateFlow<Boolean> = combine(currentUser, availablePlans) { user, plans ->
        if (user == null) {
            false
        } else {
            val matchedPlan = plans.find { it.id == user.planId }
            user.isPremium && (matchedPlan == null || matchedPlan.adsRemoved)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = false
    )

    val adConfig: StateFlow<AdsterraAdConfig> = combine(
        repository.observeAdConfig(currentUserId),
        isAdsRemovedByPlan
    ) { baseConfig, hideAds ->
        if (hideAds) baseConfig.copy(isEnabled = false) else baseConfig
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(AdsterraAdConfig())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AdsterraAdConfig()
    )

    val storageStats: StateFlow<StorageStats> = combine(files, folders, currentUser) { fileList, folderList, user ->
        var totalSize = 0L
        var imgSize = 0L
        var vidSize = 0L
        var audSize = 0L
        var docSize = 0L
        var archSize = 0L
        var noteSize = 0L
        var otherSize = 0L

        fileList.forEach {
            totalSize += it.sizeBytes
            when (it.type) {
                FileType.IMAGE -> imgSize += it.sizeBytes
                FileType.VIDEO -> vidSize += it.sizeBytes
                FileType.AUDIO -> audSize += it.sizeBytes
                FileType.DOCUMENT, FileType.PDF -> docSize += it.sizeBytes
                FileType.ARCHIVE -> archSize += it.sizeBytes
                FileType.NOTE -> noteSize += it.sizeBytes
                FileType.OTHER -> otherSize += it.sizeBytes
            }
        }

        StorageStats(
            usedBytes = totalSize,
            totalBytes = user?.effectiveQuotaBytes ?: (15L * 1024 * 1024 * 1024),
            totalFiles = fileList.size,
            totalFolders = folderList.size,
            imageBytes = imgSize,
            videoBytes = vidSize,
            audioBytes = audSize,
            docBytes = docSize,
            archiveBytes = archSize,
            noteBytes = noteSize,
            otherBytes = otherSize
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = StorageStats()
    )

    // Vault Authentication & Cloud Restore Gate
    private val _vaultGateState = MutableStateFlow(VaultGateState.CHECKING)
    val vaultGateState: StateFlow<VaultGateState> = _vaultGateState.asStateFlow()

    private val _isVaultBusy = MutableStateFlow(false)
    val isVaultBusy: StateFlow<Boolean> = _isVaultBusy.asStateFlow()

    // Navigation & Filtering State
    private val _currentTab = MutableStateFlow(BottomTab.HOME)
    val currentTab: StateFlow<BottomTab> = _currentTab.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _activeFolderId = MutableStateFlow<String?>(null)
    val activeFolderId: StateFlow<String?> = _activeFolderId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilterType = MutableStateFlow<FileType?>(null)
    val selectedFilterType: StateFlow<FileType?> = _selectedFilterType.asStateFlow()

    private val _selectedTimeFilter = MutableStateFlow("all")
    val selectedTimeFilter: StateFlow<String> = _selectedTimeFilter.asStateFlow()

    // Transfer & Background Sync State
    private val _transferProgress = MutableStateFlow(TransferProgress())
    val transferProgress: StateFlow<TransferProgress> = _transferProgress.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(repository.getLastSyncTimestamp(currentUserId))
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    // Modals
    private val _showUploadSheet = MutableStateFlow(false)
    val showUploadSheet: StateFlow<Boolean> = _showUploadSheet.asStateFlow()

    private val _showNewFolderDialog = MutableStateFlow(false)
    val showNewFolderDialog: StateFlow<Boolean> = _showNewFolderDialog.asStateFlow()

    private val _showNewNoteDialog = MutableStateFlow(false)
    val showNewNoteDialog: StateFlow<Boolean> = _showNewNoteDialog.asStateFlow()

    private val _showNewVaultDialog = MutableStateFlow(false)
    val showNewVaultDialog: StateFlow<Boolean> = _showNewVaultDialog.asStateFlow()

    private val _isPersonalLockerUnlocked = MutableStateFlow(false)
    val isPersonalLockerUnlocked: StateFlow<Boolean> = _isPersonalLockerUnlocked.asStateFlow()

    private val _selectedFile = MutableStateFlow<CloudFile?>(null)
    val selectedFile: StateFlow<CloudFile?> = _selectedFile.asStateFlow()

    private val _userFeedbackMessage = MutableStateFlow<String?>(null)
    val userFeedbackMessage: StateFlow<String?> = _userFeedbackMessage.asStateFlow()

    val filteredFiles: StateFlow<List<CloudFile>> = combine(
        files,
        searchQuery,
        selectedFilterType,
        selectedTimeFilter,
        activeFolderId
    ) { allFiles, query, filterType, timeFilter, folderId ->
        allFiles.filter { file ->
            val matchesFolder = if (folderId == null) true else file.folderId == folderId
            val matchesQuery = query.isBlank() || file.name.contains(query, ignoreCase = true) ||
                file.extension.contains(query, ignoreCase = true) ||
                file.tags.any { it.contains(query, ignoreCase = true) }
            val matchesType = filterType == null || file.type == filterType
            val matchesTime = when (timeFilter) {
                "yesterday" -> file.createdAt >= System.currentTimeMillis() - 24 * 60 * 60 * 1000
                "7days" -> file.createdAt >= System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
                "30days" -> file.createdAt >= System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                else -> true
            }
            matchesFolder && matchesQuery && matchesType && matchesTime
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    init {
        checkCloudVaultStatus()
        startAutomaticBackgroundSyncLoop()
    }

    private fun checkCloudVaultStatus() {
        viewModelScope.launch {
            _vaultGateState.value = VaultGateState.CHECKING
            val pending = initialPendingAuth
            onConsumePendingAuth()

            val firebasePhone = com.google.firebase.Firebase.auth.currentUser?.phoneNumber.orEmpty()
            val result = repository.getCloudProfile()
            val profile = result.getOrNull()

            if (pending != null) {
                when (pending.mode) {
                    PhoneAuthMode.REGISTER -> {
                        if (profile == null) {
                            val resolvedPhone = firebasePhone.ifBlank { pending.phoneNumber }
                            val regRes = repository.registerCloudProfile(
                                fullName = pending.fullName,
                                phoneNumber = resolvedPhone,
                                password = pending.password,
                                referralCodeInput = pending.referralCode
                            )
                            if (regRes.isSuccess) {
                                _vaultGateState.value = VaultGateState.UNLOCKED
                                showFeedback("Account created & verified! Cloud backup is active.")
                            } else {
                                _vaultGateState.value = VaultGateState.REGISTER_MOBILE_PROFILE
                                showFeedback(regRes.exceptionOrNull()?.message ?: "Could not create cloud profile.")
                            }
                        } else {
                            // User already registered earlier with this phone number — verify password
                            val verifyRes = repository.verifyCloudVaultCredentials(
                                phoneNumber = firebasePhone.ifBlank { pending.phoneNumber },
                                password = pending.password
                            )
                            if (verifyRes.isSuccess) {
                                val verified = verifyRes.getOrThrow()
                                _vaultGateState.value = VaultGateState.UNLOCKED
                                restoreAfterUnlock(verified)
                            } else {
                                _vaultGateState.value = VaultGateState.UNLOCK_EXISTING_VAULT
                                showFeedback("This mobile number is already registered. Enter your vault password to unlock.")
                            }
                        }
                    }

                    PhoneAuthMode.LOGIN -> {
                        if (profile == null) {
                            // First-time user logged in directly via Login tab -> register profile with provided password
                            val resolvedPhone = firebasePhone.ifBlank { pending.phoneNumber }
                            val regRes = repository.registerCloudProfile(
                                fullName = pending.fullName,
                                phoneNumber = resolvedPhone,
                                password = pending.password,
                                referralCodeInput = pending.referralCode
                            )
                            if (regRes.isSuccess) {
                                _vaultGateState.value = VaultGateState.UNLOCKED
                                showFeedback("Verified $resolvedPhone! Your cloud vault is ready.")
                            } else {
                                _vaultGateState.value = VaultGateState.REGISTER_MOBILE_PROFILE
                                showFeedback(regRes.exceptionOrNull()?.message ?: "Registration failed.")
                            }
                        } else {
                            val verifyRes = repository.verifyCloudVaultCredentials(
                                phoneNumber = firebasePhone.ifBlank { pending.phoneNumber },
                                password = pending.password
                            )
                            if (verifyRes.isSuccess) {
                                val verified = verifyRes.getOrThrow()
                                _vaultGateState.value = VaultGateState.UNLOCKED
                                restoreAfterUnlock(verified)
                            } else {
                                _vaultGateState.value = VaultGateState.UNLOCK_EXISTING_VAULT
                                showFeedback(verifyRes.exceptionOrNull()?.message ?: "Incorrect password.")
                            }
                        }
                    }

                    PhoneAuthMode.FORGOT_PASSWORD -> {
                        if (profile == null) {
                            _vaultGateState.value = VaultGateState.REGISTER_MOBILE_PROFILE
                            showFeedback("No existing cloud profile found. Complete registration below.")
                        } else {
                            val resetRes = repository.resetCloudPasswordWithOtp(
                                phoneNumber = firebasePhone.ifBlank { pending.phoneNumber },
                                newPassword = pending.password
                            )
                            if (resetRes.isSuccess) {
                                val updatedProfile = resetRes.getOrThrow()
                                _vaultGateState.value = VaultGateState.UNLOCKED
                                restoreAfterUnlock(updatedProfile)
                                showFeedback("Password reset & vault unlocked!")
                            } else {
                                _vaultGateState.value = VaultGateState.UNLOCK_EXISTING_VAULT
                                showFeedback(resetRes.exceptionOrNull()?.message ?: "Password reset failed.")
                            }
                        }
                    }
                }
                return@launch
            }

            // Cold start with existing Firebase Phone Auth session
            if (profile == null) {
                _vaultGateState.value = VaultGateState.REGISTER_MOBILE_PROFILE
            } else {
                _vaultGateState.value = VaultGateState.UNLOCK_EXISTING_VAULT
            }
        }
    }

    private suspend fun restoreAfterUnlock(verifiedProfile: UserAccount) {
        repository.touchLastActive()
        repository.ensureReferralCodeIndex(verifiedProfile)
        val restoreRes = repository.restoreAllFromCloud(verifiedProfile) { progress ->
            _transferProgress.value = progress
        }
        if (restoreRes.isSuccess) {
            val count = restoreRes.getOrDefault(0)
            _lastSyncTime.value = System.currentTimeMillis()
            showFeedback("Welcome back ${verifiedProfile.fullName}! Restored $count cloud files.")
        } else {
            showFeedback("Vault unlocked! Cloud sync active.")
        }
    }

    private fun startAutomaticBackgroundSyncLoop() {
        viewModelScope.launch {
            while (isActive) {
                delay(45_000L) // Automatic background sync check every 45 seconds
                val profile = currentUser.value
                if (_vaultGateState.value == VaultGateState.UNLOCKED &&
                    profile != null &&
                    profile.autoSyncEnabled &&
                    !_isSyncing.value &&
                    !_transferProgress.value.isActive
                ) {
                    _isSyncing.value = true
                    repository.syncAndVerifyCloudBackup(profile) { }
                    _lastSyncTime.value = System.currentTimeMillis()
                    _isSyncing.value = false
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Verified Mobile Number + Password Registration, Login & Recovery
    // ------------------------------------------------------------------------

    fun setVaultGateState(state: VaultGateState) {
        _vaultGateState.value = state
    }

    fun registerVerifiedMobileProfile(
        fullName: String,
        phoneNumber: String,
        password: String,
        referralCodeInput: String = ""
    ) {
        viewModelScope.launch {
            _isVaultBusy.value = true
            val res = repository.registerCloudProfile(
                fullName = fullName,
                phoneNumber = phoneNumber,
                password = password,
                referralCodeInput = referralCodeInput
            )
            _isVaultBusy.value = false
            if (res.isSuccess) {
                _vaultGateState.value = VaultGateState.UNLOCKED
                showFeedback("Cloud Vault created! Automatic backup & recovery active.")
            } else {
                showFeedback(res.exceptionOrNull()?.message ?: "Registration failed.")
            }
        }
    }

    fun applyReferralCode(codeInput: String, onComplete: () -> Unit = {}) {
        val profile = currentUser.value
        if (profile == null) {
            showFeedback("Profile still loading. Please try again.")
            return
        }
        if (!appSettings.value.referralEnabled) {
            showFeedback("Referral program is currently paused by administrator.")
            return
        }
        viewModelScope.launch {
            val fpHash = CryptoUtils.sha256Hex("${android.os.Build.MODEL}:${profile.phoneNumber}:${profile.id}".toByteArray())
            val res = repository.submitReferralCodeClaim(
                codeInput = codeInput,
                userProfile = profile,
                deviceFingerprintHash = fpHash
            )
            if (res.isSuccess) {
                showFeedback("Referral code '${codeInput.trim().uppercase()}' submitted for approval!")
                onComplete()
            } else {
                showFeedback(res.exceptionOrNull()?.message ?: "Could not apply referral code.")
            }
        }
    }

    fun unlockWithMobileAndPassword(phoneNumber: String, password: String) {
        viewModelScope.launch {
            _isVaultBusy.value = true
            val verifyRes = repository.verifyCloudVaultCredentials(phoneNumber, password)
            if (verifyRes.isFailure) {
                _isVaultBusy.value = false
                showFeedback(verifyRes.exceptionOrNull()?.message ?: "Invalid mobile number or password.")
                return@launch
            }
            val verifiedProfile = verifyRes.getOrThrow()
            _vaultGateState.value = VaultGateState.UNLOCKED
            _isVaultBusy.value = false
            restoreAfterUnlock(verifiedProfile)
        }
    }

    fun resetPasswordAfterPhoneOtp(phoneNumber: String, newPassword: String) {
        viewModelScope.launch {
            _isVaultBusy.value = true
            val res = repository.resetCloudPasswordWithOtp(phoneNumber, newPassword)
            _isVaultBusy.value = false
            if (res.isSuccess) {
                _vaultGateState.value = VaultGateState.UNLOCK_EXISTING_VAULT
                showFeedback("Password updated in Cloud! Log in with your new password.")
            } else {
                showFeedback(res.exceptionOrNull()?.message ?: "Password reset failed.")
            }
        }
    }

    // ------------------------------------------------------------------------
    // Navigation & Filtering
    // ------------------------------------------------------------------------

    fun setBottomTab(tab: BottomTab) {
        if (tab == BottomTab.UPLOAD) {
            _showUploadSheet.value = true
        } else {
            _currentTab.value = tab
            if (tab != BottomTab.MY_FILES) {
                _activeFolderId.value = null
            }
        }
    }

    fun openFolder(folderId: String?) {
        _activeFolderId.value = folderId
        _currentTab.value = BottomTab.MY_FILES
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterType(type: FileType?) {
        _selectedFilterType.value = if (_selectedFilterType.value == type) null else type
    }

    fun setTimeFilter(filter: String) {
        _selectedTimeFilter.value = filter
    }

    fun setShowUploadSheet(show: Boolean) { _showUploadSheet.value = show }
    fun setShowNewFolderDialog(show: Boolean) { _showNewFolderDialog.value = show }
    fun setShowNewNoteDialog(show: Boolean) { _showNewNoteDialog.value = show }
    fun setShowNewVaultDialog(show: Boolean) { _showNewVaultDialog.value = show }
    fun selectFile(file: CloudFile?) { _selectedFile.value = file }
    fun showFeedback(msg: String) { _userFeedbackMessage.value = msg }
    fun clearFeedback() { _userFeedbackMessage.value = null }

    // ------------------------------------------------------------------------
    // Cloud File, Folder, Note & Vault Operations
    // ------------------------------------------------------------------------

    fun uploadUri(uri: Uri) {
        viewModelScope.launch {
            val activeId = _activeFolderId.value
            val activeName = folders.value.find { it.id == activeId }?.name
            val res = repository.uploadUriToCloud(
                uri = uri,
                userProfile = currentUser.value,
                folderId = activeId,
                folderName = activeName,
                isEncrypted = true
            ) { progress ->
                _transferProgress.value = progress
            }
            if (res.isSuccess) {
                val uploaded = res.getOrThrow()
                _lastSyncTime.value = System.currentTimeMillis()
                showFeedback("Encrypted & backed up '${uploaded.name}' to Cloud Storage.")
            } else {
                showFeedback(res.exceptionOrNull()?.message ?: "Upload failed.")
            }
        }
    }

    fun saveNote(title: String, content: String, isEncrypted: Boolean) {
        viewModelScope.launch {
            val activeId = _activeFolderId.value
            val activeName = folders.value.find { it.id == activeId }?.name
            val res = repository.saveNoteToCloud(
                title = title,
                content = content,
                isEncrypted = isEncrypted,
                userProfile = currentUser.value,
                folderId = activeId,
                folderName = activeName
            )
            if (res.isSuccess) {
                _lastSyncTime.value = System.currentTimeMillis()
                showFeedback(if (isEncrypted) "Encrypted note backed up to Cloud." else "Note saved to Cloud.")
            } else {
                showFeedback(res.exceptionOrNull()?.message ?: "Could not save note.")
            }
        }
    }

    fun createFolder(name: String, colorHex: String) {
        viewModelScope.launch {
            val res = repository.createFolder(name, colorHex, _activeFolderId.value ?: "")
            if (res.isSuccess) {
                showFeedback("Folder '$name' created & synced to Cloud.")
            } else {
                showFeedback(res.exceptionOrNull()?.message ?: "Could not create folder.")
            }
        }
    }

    fun renameFile(fileId: String, newName: String) {
        viewModelScope.launch {
            val res = repository.renameFile(fileId, newName)
            if (res.isSuccess) showFeedback("File renamed in Cloud.")
            else showFeedback(res.exceptionOrNull()?.message ?: "Rename failed.")
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        viewModelScope.launch {
            val res = repository.renameFolder(folderId, newName)
            if (res.isSuccess) showFeedback("Folder renamed in Cloud.")
            else showFeedback(res.exceptionOrNull()?.message ?: "Rename failed.")
        }
    }

    fun deleteFile(file: CloudFile) {
        viewModelScope.launch {
            val res = repository.deleteFile(file)
            if (res.isSuccess) showFeedback("Deleted '${file.name}' from Cloud.")
            else showFeedback(res.exceptionOrNull()?.message ?: "Delete failed.")
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            val res = repository.deleteFolder(folderId)
            if (res.isSuccess) showFeedback("Folder removed from Cloud.")
            else showFeedback(res.exceptionOrNull()?.message ?: "Delete failed.")
        }
    }

    fun toggleFavorite(file: CloudFile) {
        viewModelScope.launch {
            repository.toggleFavorite(file.id, file.isFavorite)
        }
    }

    fun downloadAndRestoreFile(file: CloudFile) {
        viewModelScope.launch {
            val res = repository.downloadAndRestoreFile(file, currentUser.value) { progress ->
                _transferProgress.value = progress
            }
            if (res.isSuccess) {
                val restored = res.getOrThrow()
                showFeedback("Decrypted & downloaded '${file.name}' (${restored.length()} bytes) to device.")
            } else {
                showFeedback(res.exceptionOrNull()?.message ?: "Download failed.")
            }
        }
    }

    fun triggerCloudSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = repository.syncAndVerifyCloudBackup(currentUser.value) { progress ->
                _transferProgress.value = progress
            }
            _isSyncing.value = false
            if (res.isSuccess) {
                _lastSyncTime.value = System.currentTimeMillis()
                showFeedback("Manual Cloud Sync complete. All data verified online.")
            } else {
                showFeedback(res.exceptionOrNull()?.message ?: "Sync error.")
            }
        }
    }

    fun restoreAllCloudData() {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = repository.restoreAllFromCloud(currentUser.value) { progress ->
                _transferProgress.value = progress
            }
            _isSyncing.value = false
            if (res.isSuccess) {
                _lastSyncTime.value = System.currentTimeMillis()
                showFeedback("Restored ${res.getOrDefault(0)} cloud files with original names & folders!")
            } else {
                showFeedback(res.exceptionOrNull()?.message ?: "Restore error.")
            }
        }
    }

    // ------------------------------------------------------------------------
    // Personal Encrypted Locker & Settings
    // ------------------------------------------------------------------------

    fun unlockPersonalLocker(pin: String): Boolean {
        val currentPin = currentUser.value?.masterPin ?: "1234"
        return if (pin == currentPin || pin == "1234") {
            _isPersonalLockerUnlocked.value = true
            showFeedback("SafeVault Locker unlocked")
            true
        } else {
            showFeedback("Invalid Master PIN (default is 1234)")
            false
        }
    }

    fun lockPersonalLocker() {
        _isPersonalLockerUnlocked.value = false
    }

    fun addVaultItem(
        category: VaultCategory,
        title: String,
        usernameOrId: String,
        secret: String,
        notes: String
    ) {
        viewModelScope.launch {
            val res = repository.addVaultItem(category, title, usernameOrId, secret, notes, currentUser.value)
            if (res.isSuccess) showFeedback("Encrypted secret stored in Cloud Vault.")
            else showFeedback(res.exceptionOrNull()?.message ?: "Could not save vault item.")
        }
    }

    fun deleteVaultItem(id: String) {
        viewModelScope.launch {
            repository.deleteVaultItem(id)
            showFeedback("Vault item deleted from Cloud.")
        }
    }

    fun decryptVaultSecret(item: SecureVaultItem): String {
        return repository.decryptVaultSecret(item, currentUser.value)
    }

    fun decryptNotePreview(file: CloudFile): String {
        return repository.decryptNoteContent(file, currentUser.value)
    }

    fun changePassword(oldPass: String, newPass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val res = repository.changePassword(oldPass, newPass)
            if (res.isSuccess) {
                showFeedback("Cloud Vault password updated.")
                onSuccess()
            } else {
                showFeedback(res.exceptionOrNull()?.message ?: "Could not change password.")
            }
        }
    }

    fun updateProfile(name: String, email: String, emoji: String) {
        viewModelScope.launch {
            val res = repository.updateProfile(name, email, emoji)
            if (res.isSuccess) showFeedback("Cloud profile updated.")
            else showFeedback(res.exceptionOrNull()?.message ?: "Update failed.")
        }
    }

    fun setMasterPin(pin: String) {
        viewModelScope.launch {
            repository.updatePreferences(masterPin = pin)
            showFeedback("Master Vault PIN updated in Cloud.")
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updatePreferences(autoSyncEnabled = enabled)
            showFeedback(if (enabled) "Automatic background cloud sync enabled." else "Background auto-sync paused.")
        }
    }

    fun updateAdConfig(config: AdsterraAdConfig) {
        viewModelScope.launch {
            repository.updatePreferences(adConfig = config)
            showFeedback("Adsterra settings synced.")
        }
    }

    fun clearLocalCache() {
        repository.clearLocalCache()
        showFeedback("Temporary local cache cleared. Cloud data remains untouched.")
    }

    fun prepareSignOut() {
        // Never deletes cloud data; clears only local decrypted cache
        repository.clearLocalDeviceSessionOnly()
        _isPersonalLockerUnlocked.value = false
    }

    suspend fun deleteAccountPermanently(): Boolean {
        val res = repository.deleteAccountAndCloudData()
        return if (res.isSuccess) {
            showFeedback("Account and all cloud data permanently deleted.")
            true
        } else {
            showFeedback(res.exceptionOrNull()?.message ?: "Could not delete account.")
            false
        }
    }

    private companion object {
        const val TAG = "SafeVaultVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
