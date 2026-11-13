package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AdminRole(val displayName: String, val defaultPermissions: List<AdminPermission>) {
    SUPER_ADMIN(
        "Super Admin",
        AdminPermission.entries.toList()
    ),
    MODERATOR(
        "Moderator",
        listOf(
            AdminPermission.MANAGE_USERS,
            AdminPermission.MANAGE_REFERRALS,
            AdminPermission.VIEW_AUDIT_LOGS
        )
    ),
    SUPPORT_ADMIN(
        "Support Admin",
        listOf(
            AdminPermission.MANAGE_USERS,
            AdminPermission.VIEW_AUDIT_LOGS
        )
    )
}

enum class AdminPermission(val label: String) {
    MANAGE_USERS("User Management (Block/Plan/Delete)"),
    MANAGE_PLANS("Plan Creation & Pricing"),
    MANAGE_REFERRALS("Referral & Fraud Control"),
    MANAGE_SETTINGS("App Settings & Notifications"),
    MANAGE_ADMINS("Admin Roles & Permissions"),
    VIEW_AUDIT_LOGS("Security Audit Logs")
}

enum class ReportPeriod(val label: String, val windowMillis: Long) {
    DAILY("Daily", 24L * 60L * 60L * 1000L),
    WEEKLY("Weekly", 7L * 24L * 60L * 60L * 1000L),
    MONTHLY("Monthly", 30L * 24L * 60L * 60L * 1000L)
}

enum class UserFilterMode(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    BLOCKED("Blocked"),
    PREMIUM("Premium"),
    FREE("Free")
}

data class AdminMember(
    val uid: String = "",
    val fullName: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val role: AdminRole = AdminRole.MODERATOR,
    val permissions: List<String> = emptyList(),
    val adminPinHash: String = "",
    val adminPinSalt: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun hasPermission(perm: AdminPermission): Boolean {
        if (!isActive) return false
        return role == AdminRole.SUPER_ADMIN || permissions.contains(perm.name)
    }

    val hasPinConfigured: Boolean
        get() = adminPinHash.isNotBlank() && adminPinSalt.isNotBlank()
}

data class SubscriptionPlan(
    val id: String = "",
    val name: String = "",
    val tier: String = "FREE", // FREE or PREMIUM
    val price: Double = 0.0,
    val currency: String = "USD",
    val durationDays: Int = 30,
    val storageQuotaGb: Long = 15L,
    val maxFileSizeMb: Long = 14L,
    val features: List<String> = emptyList(),
    val adsRemoved: Boolean = false,
    val isActive: Boolean = true,
    val createdByUid: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ReferralRecord(
    val id: String = "",
    val referrerUid: String = "",
    val referrerName: String = "",
    val referrerPhone: String = "",
    val referralCode: String = "",
    val referredUid: String = "",
    val referredName: String = "",
    val referredPhone: String = "",
    val deviceFingerprintHash: String = "",
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val rewardPoints: Long = 0L,
    val rewardStorageBytes: Long = 0L,
    val fraudReason: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class GlobalAppSettings(
    val appName: String = "SafeVault",
    val appTagline: String = "Encrypted Cloud Storage & Auto-Recovery",
    val appLogoStyle: String = "CYAN_SHIELD", // CYAN_SHIELD, ROYAL_BLUE, EMERALD_VAULT, AMETHYST_LOCK
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "SafeVault is undergoing scheduled security maintenance. Your cloud files remain 100% encrypted and safe.",
    val announcementEnabled: Boolean = false,
    val announcementTitle: String = "",
    val announcementMessage: String = "",
    val supportEmail: String = "support@safevault.app",
    val supportPhone: String = "+1 (800) 555-0199",
    val supportWhatsApp: String = "+8801700000000",
    val termsAndConditions: String = DEFAULT_TERMS,
    val privacyPolicy: String = DEFAULT_PRIVACY,
    val referralEnabled: Boolean = true,
    val referralRewardPoints: Long = 100L,
    val referralRewardBonusMb: Long = 512L,
    val updatedByUid: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_TERMS =
            "By using SafeVault Cloud Storage, you agree to:\n\n" +
            "1. Authenticate using your own verified mobile number and OTP.\n" +
            "2. Maintain confidentiality of your vault password and Master PIN.\n" +
            "3. Abide by anti-fraud referral and fair cloud storage usage guidelines.\n" +
            "4. Admins may suspend accounts violating security or abuse policies."

        const val DEFAULT_PRIVACY =
            "SafeVault is committed to zero-knowledge encryption and user privacy.\n\n" +
            "1. Client-Side AES-256 Encryption: Notes, secrets, and backed-up files are encrypted before remote upload.\n" +
            "2. Mobile OTP + Password Recovery: Reinstalls on any device restore your encrypted cloud files.\n" +
            "3. Data Deletion: Users or authorized administrators can purge account data permanently."
    }
}

data class AppNotification(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val priority: String = "INFO", // INFO, WARNING, PROMO
    val targetAudience: String = "ALL", // ALL, FREE, PREMIUM
    val isActive: Boolean = true,
    val createdByUid: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class AdminAuditLog(
    val id: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val actorRole: String = "",
    val action: String = "",
    val targetType: String = "USER",
    val targetId: String = "",
    val details: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class AdminDashboardMetrics(
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val inactiveUsers: Int = 0,
    val blockedUsers: Int = 0,
    val newUsersInPeriod: Int = 0,
    val activeUsersInPeriod: Int = 0,
    val premiumUsers: Int = 0,
    val freeUsers: Int = 0,
    val totalReferrals: Int = 0,
    val approvedReferrals: Int = 0,
    val pendingReferrals: Int = 0,
    val rejectedReferrals: Int = 0,
    val referralsInPeriod: Int = 0,
    val totalRevenue: Double = 0.0,
    val revenueInPeriod: Double = 0.0,
    val totalAllocatedQuotaBytes: Long = 0L
)

// Firestore DTOs

@IgnoreExtraProperties
data class FirestoreAdminMember(
    @DocumentId val id: String = "",
    val uid: String = "",
    val fullName: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val role: String = AdminRole.MODERATOR.name,
    val permissions: List<String> = emptyList(),
    val adminPinHash: String = "",
    val adminPinSalt: String = "",
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreSubscriptionPlan(
    @DocumentId val id: String = "",
    val name: String = "",
    val tier: String = "FREE",
    val price: Double = 0.0,
    val currency: String = "USD",
    val durationDays: Long = 30L,
    val storageQuotaGb: Long = 15L,
    val maxFileSizeMb: Long = 14L,
    val features: List<String> = emptyList(),
    val adsRemoved: Boolean = false,
    val isActive: Boolean = true,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreReferralRecord(
    @DocumentId val id: String = "",
    val referrerUid: String = "",
    val referrerName: String = "",
    val referrerPhone: String = "",
    val referralCode: String = "",
    val referredUid: String = "",
    val referredName: String = "",
    val referredPhone: String = "",
    val deviceFingerprintHash: String = "",
    val status: String = "PENDING",
    val rewardPoints: Long = 0L,
    val rewardStorageBytes: Long = 0L,
    val fraudReason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreAppSettings(
    @DocumentId val id: String = "",
    val appName: String = "SafeVault",
    val appTagline: String = "Encrypted Cloud Storage & Auto-Recovery",
    val appLogoStyle: String = "CYAN_SHIELD",
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = GlobalAppSettings().maintenanceMessage,
    val announcementEnabled: Boolean = false,
    val announcementTitle: String = "",
    val announcementMessage: String = "",
    val supportEmail: String = GlobalAppSettings().supportEmail,
    val supportPhone: String = GlobalAppSettings().supportPhone,
    val supportWhatsApp: String = GlobalAppSettings().supportWhatsApp,
    val termsAndConditions: String = GlobalAppSettings.DEFAULT_TERMS,
    val privacyPolicy: String = GlobalAppSettings.DEFAULT_PRIVACY,
    val referralEnabled: Boolean = true,
    val referralRewardPoints: Long = 100L,
    val referralRewardBonusMb: Long = 512L,
    val updatedByUid: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreAppNotification(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val priority: String = "INFO",
    val targetAudience: String = "ALL",
    val isActive: Boolean = true,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreAdminAuditLog(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val actorRole: String = "",
    val action: String = "",
    val targetType: String = "USER",
    val targetId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
