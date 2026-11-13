package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminAuditLog
import com.example.data.model.AdminDashboardMetrics
import com.example.data.model.AdminMember
import com.example.data.model.AdminPermission
import com.example.data.model.AdminRole
import com.example.data.model.AppNotification
import com.example.data.model.GlobalAppSettings
import com.example.data.model.ReferralRecord
import com.example.data.model.ReportPeriod
import com.example.data.model.SubscriptionPlan
import com.example.data.model.UserAccount
import com.example.data.model.UserFilterMode
import com.example.data.repository.SafeVaultAdminRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AdminTab(val label: String) {
    DASHBOARD("Dashboard"),
    USERS("Users"),
    PLANS("Plans"),
    REFERRALS("Referrals"),
    SECURITY("Security"),
    SETTINGS("Settings")
}

class AdminViewModel(
    private val adminRepository: SafeVaultAdminRepository,
    val currentUserId: String
) : ViewModel() {

    val currentAdmin: StateFlow<AdminMember?> = adminRepository.observeCurrentAdmin(currentUserId)
        .catch { if (it is CancellationException) throw it; emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val users: StateFlow<List<UserAccount>> = adminRepository.observeAllUsers()
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val plans: StateFlow<List<SubscriptionPlan>> = adminRepository.observeAllPlans()
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val referrals: StateFlow<List<ReferralRecord>> = adminRepository.observeAllReferrals()
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val admins: StateFlow<List<AdminMember>> = adminRepository.observeAllAdmins()
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val notifications: StateFlow<List<AppNotification>> = adminRepository.observeAllNotifications()
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val auditLogs: StateFlow<List<AdminAuditLog>> = adminRepository.observeAuditLogs()
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val appSettings: StateFlow<GlobalAppSettings> = adminRepository.observeGlobalAppSettings()
        .catch { if (it is CancellationException) throw it; emit(GlobalAppSettings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), GlobalAppSettings())

    private val _selectedTab = MutableStateFlow(AdminTab.DASHBOARD)
    val selectedTab: StateFlow<AdminTab> = _selectedTab.asStateFlow()

    private val _reportPeriod = MutableStateFlow(ReportPeriod.DAILY)
    val reportPeriod: StateFlow<ReportPeriod> = _reportPeriod.asStateFlow()

    private val _userSearchQuery = MutableStateFlow("")
    val userSearchQuery: StateFlow<String> = _userSearchQuery.asStateFlow()

    private val _userFilterMode = MutableStateFlow(UserFilterMode.ALL)
    val userFilterMode: StateFlow<UserFilterMode> = _userFilterMode.asStateFlow()

    private val _referralStatusFilter = MutableStateFlow("ALL") // ALL, PENDING, APPROVED, REJECTED
    val referralStatusFilter: StateFlow<String> = _referralStatusFilter.asStateFlow()

    private val _isAdminPinVerified = MutableStateFlow(false)
    val isAdminPinVerified: StateFlow<Boolean> = _isAdminPinVerified.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    val metrics: StateFlow<AdminDashboardMetrics> = combine(
        users,
        referrals,
        reportPeriod
    ) { userList, referralList, period ->
        computeDashboardMetrics(
            users = userList,
            referrals = referralList,
            period = period,
            nowMs = System.currentTimeMillis()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AdminDashboardMetrics())

    val filteredUsers: StateFlow<List<UserAccount>> = combine(
        users,
        userSearchQuery,
        userFilterMode
    ) { allUsers, query, mode ->
        allUsers.filter { user ->
            val matchesQuery = query.isBlank() ||
                user.fullName.contains(query, ignoreCase = true) ||
                user.phoneNumber.contains(query, ignoreCase = true) ||
                user.id.contains(query, ignoreCase = true) ||
                user.planName.contains(query, ignoreCase = true)
            val matchesMode = when (mode) {
                UserFilterMode.ALL -> true
                UserFilterMode.ACTIVE -> !user.isBlocked
                UserFilterMode.BLOCKED -> user.isBlocked
                UserFilterMode.PREMIUM -> user.isPremium
                UserFilterMode.FREE -> !user.isPremium
            }
            matchesQuery && matchesMode
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val filteredReferrals: StateFlow<List<ReferralRecord>> = combine(
        referrals,
        referralStatusFilter
    ) { allReferrals, filter ->
        if (filter == "ALL") allReferrals
        else allReferrals.filter { it.status.equals(filter, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun selectTab(tab: AdminTab) { _selectedTab.value = tab }
    fun setReportPeriod(period: ReportPeriod) { _reportPeriod.value = period }
    fun setUserSearchQuery(query: String) { _userSearchQuery.value = query }
    fun setUserFilterMode(mode: UserFilterMode) { _userFilterMode.value = mode }
    fun setReferralStatusFilter(status: String) { _referralStatusFilter.value = status }
    fun clearFeedback() { _feedbackMessage.value = null }

    fun unlockAdminPin(pinInput: String): Boolean {
        val admin = currentAdmin.value ?: return false
        return if (adminRepository.verifyAdminPin(admin, pinInput)) {
            _isAdminPinVerified.value = true
            true
        } else {
            _feedbackMessage.value = "Incorrect Admin Security PIN."
            false
        }
    }

    fun resetPinGate() {
        _isAdminPinVerified.value = false
    }

    private fun requireActorWithPermission(perm: AdminPermission): AdminMember? {
        val admin = currentAdmin.value
        if (admin == null || !admin.isActive) {
            _feedbackMessage.value = "Unauthorized: Active administrator account required."
            return null
        }
        if (!admin.hasPermission(perm)) {
            _feedbackMessage.value = "Access denied: Requires ${perm.label}."
            return null
        }
        return admin
    }

    // ------------------------------------------------------------------------
    // User Management Actions
    // ------------------------------------------------------------------------

    fun setUserBlockStatus(targetUser: UserAccount, block: Boolean, reason: String) {
        val actor = requireActorWithPermission(AdminPermission.MANAGE_USERS) ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = adminRepository.setUserBlockStatus(actor, targetUser.id, block, reason)
            _isBusy.value = false
            _feedbackMessage.value = if (res.isSuccess) {
                if (block) "Blocked ${targetUser.fullName}." else "Unblocked ${targetUser.fullName}."
            } else {
                res.exceptionOrNull()?.message ?: "Failed to update user status."
            }
        }
    }

    fun deleteUser(targetUser: UserAccount) {
        val actor = requireActorWithPermission(AdminPermission.MANAGE_USERS) ?: return
        if (targetUser.id == actor.uid) {
            _feedbackMessage.value = "Safety guard: Cannot delete your own active admin account."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val res = adminRepository.deleteUserAccount(actor, targetUser)
            _isBusy.value = false
            _feedbackMessage.value = if (res.isSuccess) {
                "Deleted ${targetUser.fullName} and all cloud files."
            } else {
                res.exceptionOrNull()?.message ?: "Could not delete user."
            }
        }
    }

    fun assignPlanToUser(targetUser: UserAccount, plan: SubscriptionPlan, customDurationDays: Int? = null) {
        val actor = requireActorWithPermission(AdminPermission.MANAGE_USERS) ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = adminRepository.assignPlanToUser(actor, targetUser.id, plan, customDurationDays)
            _isBusy.value = false
            _feedbackMessage.value = if (res.isSuccess) {
                "Assigned '${plan.name}' to ${targetUser.fullName}."
            } else {
                res.exceptionOrNull()?.message ?: "Failed to assign plan."
            }
        }
    }

    fun adjustUserPlanExpiry(targetUser: UserAccount, deltaDays: Int) {
        val actor = requireActorWithPermission(AdminPermission.MANAGE_USERS) ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = adminRepository.adjustUserPlanExpiry(actor, targetUser, deltaDays)
            _isBusy.value = false
            _feedbackMessage.value = if (res.isSuccess) {
                if (deltaDays >= 0) "Extended plan by +$deltaDays days." else "Reduced plan by ${-deltaDays} days."
            } else {
                res.exceptionOrNull()?.message ?: "Failed to adjust plan expiry."
            }
        }
    }

    // ------------------------------------------------------------------------
    // Plan Management Actions
    // ------------------------------------------------------------------------

    fun savePlan(plan: SubscriptionPlan, onSuccess: () -> Unit = {}) {
        val actor = requireActorWithPermission(AdminPermission.MANAGE_PLANS) ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = adminRepository.savePlan(actor, plan)
            _isBusy.value = false
            if (res.isSuccess) {
                _feedbackMessage.value = "Plan '${plan.name}' saved."
                onSuccess()
            } else {
                _feedbackMessage.value = res.exceptionOrNull()?.message ?: "Failed to save plan."
            }
        }
    }

    fun deletePlan(plan: SubscriptionPlan) {
        val actor = requireActorWithPermission(AdminPermission.MANAGE_PLANS) ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = adminRepository.deletePlan(actor, plan.id, plan.name)
            _isBusy.value = false
            _feedbackMessage.value = if (res.isSuccess) {
                "Deleted plan '${plan.name}'."
            } else {
                res.exceptionOrNull()?.message ?: "Failed to delete plan."
            }
        }
    }

    // ------------------------------------------------------------------------
    // Referral Control Actions
    // ------------------------------------------------------------------------

    fun reviewReferral(referral: ReferralRecord, approve: Boolean, fraudReason: String = "") {
        val actor = requireActorWithPermission(AdminPermission.MANAGE_REFERRALS) ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = adminRepository.reviewReferral(
                actor = actor,
                referral = referral,
                approve = approve,
                fraudReason = fraudReason,
                settings = appSettings.value
            )
            _isBusy.value = false
            _feedbackMessage.value = if (res.isSuccess) {
                if (approve) "Referral approved & reward credited." else "Referral rejected & flagged."
            } else {
                res.exceptionOrNull()?.message ?: "Referral update failed."
            }
        }
    }

    // ------------------------------------------------------------------------
    // App Settings & Notifications
    // ------------------------------------------------------------------------

    fun updateAppSettings(settings: GlobalAppSettings) {
        val actor = requireActorWithPermission(AdminPermission.MANAGE_SETTINGS) ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = adminRepository.updateGlobalAppSettings(actor, settings)
            _isBusy.value = false
            _feedbackMessage.value = if (res.isSuccess) {
                "App settings updated & synced live."
            } else {
                res.exceptionOrNull()?.message ?: "Failed to update settings."
            }
        }
    }

    fun saveNotification(notification: AppNotification, onSuccess: () -> Unit = {}) {
        val actor = requireActorWithPermission(AdminPermission.MANAGE_SETTINGS) ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = adminRepository.saveNotification(actor, notification)
            _isBusy.value = false
            if (res.isSuccess) {
                _feedbackMessage.value = "Broadcast notification saved."
                onSuccess()
            } else {
                _feedbackMessage.value = res.exceptionOrNull()?.message ?: "Notification save failed."
            }
        }
    }

    fun deleteNotification(notification: AppNotification) {
        val actor = requireActorWithPermission(AdminPermission.MANAGE_SETTINGS) ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = adminRepository.deleteNotification(actor, notification.id, notification.title)
            _isBusy.value = false
            _feedbackMessage.value = if (res.isSuccess) {
                "Notification deleted."
            } else {
                res.exceptionOrNull()?.message ?: "Delete failed."
            }
        }
    }

    // ------------------------------------------------------------------------
    // Admin Security & Multi-Role Actions
    // ------------------------------------------------------------------------

    fun saveAdminMember(
        targetUid: String,
        fullName: String,
        phoneNumber: String,
        email: String,
        role: AdminRole,
        permissions: List<AdminPermission>,
        isActive: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        val actor = requireActorWithPermission(AdminPermission.MANAGE_ADMINS) ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = adminRepository.saveAdminMember(
                actor = actor,
                targetUid = targetUid,
                fullName = fullName,
                phoneNumber = phoneNumber,
                email = email,
                role = role,
                permissions = permissions,
                isActive = isActive
            )
            _isBusy.value = false
            if (res.isSuccess) {
                _feedbackMessage.value = "Admin role updated for $fullName."
                onSuccess()
            } else {
                _feedbackMessage.value = res.exceptionOrNull()?.message ?: "Failed to update admin."
            }
        }
    }

    fun revokeAdminMember(target: AdminMember) {
        val actor = requireActorWithPermission(AdminPermission.MANAGE_ADMINS) ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = adminRepository.revokeAdminMember(actor, target.uid, target.fullName)
            _isBusy.value = false
            _feedbackMessage.value = if (res.isSuccess) {
                "Revoked admin access for ${target.fullName}."
            } else {
                res.exceptionOrNull()?.message ?: "Could not revoke admin."
            }
        }
    }

    fun setOwnAdminPin(newPin: String, onSuccess: () -> Unit = {}) {
        val actor = currentAdmin.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = adminRepository.setOwnAdminPin(actor, newPin)
            _isBusy.value = false
            if (res.isSuccess) {
                _isAdminPinVerified.value = true
                _feedbackMessage.value = "Admin Security PIN configured."
                onSuccess()
            } else {
                _feedbackMessage.value = res.exceptionOrNull()?.message ?: "Failed to set Admin PIN."
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun computeDashboardMetrics(
            users: List<UserAccount>,
            referrals: List<ReferralRecord>,
            period: ReportPeriod,
            nowMs: Long = System.currentTimeMillis()
        ): AdminDashboardMetrics {
            val periodCutoff = nowMs - period.windowMillis
            val activeBaselineCutoff = nowMs - 30L * 24L * 60L * 60L * 1000L

            val activeCount = users.count { !it.isBlocked && (it.lastActiveAt >= activeBaselineCutoff || it.createdAt >= activeBaselineCutoff) }
            val blockedCount = users.count { it.isBlocked }
            val inactiveCount = (users.size - activeCount).coerceAtLeast(0)
            val newUsersInPeriod = users.count { it.createdAt >= periodCutoff }
            val activeInPeriod = users.count { !it.isBlocked && it.lastActiveAt >= periodCutoff }
            val premiumCount = users.count { it.isPremium }
            val freeCount = (users.size - premiumCount).coerceAtLeast(0)

            val approvedRefs = referrals.count { it.status.equals("APPROVED", ignoreCase = true) }
            val pendingRefs = referrals.count { it.status.equals("PENDING", ignoreCase = true) }
            val rejectedRefs = referrals.count { it.status.equals("REJECTED", ignoreCase = true) }
            val refsInPeriod = referrals.count { it.createdAt >= periodCutoff }

            val totalRevenue = users.filter { it.planTier.equals("PREMIUM", ignoreCase = true) }.sumOf { it.planPrice }
            val revenueInPeriod = users.filter {
                it.planTier.equals("PREMIUM", ignoreCase = true) &&
                    (it.createdAt >= periodCutoff || it.lastActiveAt >= periodCutoff)
            }.sumOf { it.planPrice }

            val totalQuota = users.sumOf { it.effectiveQuotaBytes }

            return AdminDashboardMetrics(
                totalUsers = users.size,
                activeUsers = activeCount,
                inactiveUsers = inactiveCount,
                blockedUsers = blockedCount,
                newUsersInPeriod = newUsersInPeriod,
                activeUsersInPeriod = activeInPeriod,
                premiumUsers = premiumCount,
                freeUsers = freeCount,
                totalReferrals = referrals.size,
                approvedReferrals = approvedRefs,
                pendingReferrals = pendingRefs,
                rejectedReferrals = rejectedRefs,
                referralsInPeriod = refsInPeriod,
                totalRevenue = totalRevenue,
                revenueInPeriod = revenueInPeriod,
                totalAllocatedQuotaBytes = totalQuota
            )
        }
    }
}
