package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AdminAuditLog
import com.example.data.model.AdminMember
import com.example.data.model.AdminPermission
import com.example.data.model.AdminRole
import com.example.data.model.AppNotification
import com.example.data.model.FirestoreAdminAuditLog
import com.example.data.model.FirestoreAdminMember
import com.example.data.model.FirestoreAppNotification
import com.example.data.model.FirestoreAppSettings
import com.example.data.model.FirestoreReferralRecord
import com.example.data.model.FirestoreSubscriptionPlan
import com.example.data.model.FirestoreUserProfile
import com.example.data.model.GlobalAppSettings
import com.example.data.model.ReferralRecord
import com.example.data.model.SubscriptionPlan
import com.example.data.model.UserAccount
import com.example.data.security.CryptoUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class SafeVaultAdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    companion object {
        const val BOOTSTRAPPED_OWNER_EMAIL = "ahmodnoor22@gmail.com"
    }

    private fun requireActorUid(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Authentication required for SafeVault Admin actions.")
    }

    private fun sanitizeId(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
    }

    private fun isBootstrappedOwnerSignedIn(): Boolean {
        val user = auth.currentUser ?: return false
        return user.email.equals(BOOTSTRAPPED_OWNER_EMAIL, ignoreCase = true) && user.isEmailVerified
    }

    // ------------------------------------------------------------------------
    // Real-Time Streams
    // ------------------------------------------------------------------------

    fun observeCurrentAdmin(uid: String): Flow<AdminMember?> = flow {
        val path = "admins/$uid"
        emitAll(
            db.collection("admins").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) {
                        if (auth.currentUser?.uid == uid && isBootstrappedOwnerSignedIn()) {
                            return@map AdminMember(
                                uid = uid,
                                fullName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Owner Admin",
                                email = BOOTSTRAPPED_OWNER_EMAIL,
                                role = AdminRole.SUPER_ADMIN,
                                permissions = AdminPermission.entries.map { it.name },
                                isActive = true
                            )
                        }
                        return@map null
                    }
                    val dto = snapshot.toObject(
                        FirestoreAdminMember::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@map null
                    mapAdminDto(snapshot.id, dto)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (firestoreError != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError, OperationType.GET, path)
                    }
                    if (auth.currentUser?.uid == uid && isBootstrappedOwnerSignedIn()) {
                        emit(
                            AdminMember(
                                uid = uid,
                                fullName = "Owner Admin",
                                email = BOOTSTRAPPED_OWNER_EMAIL,
                                role = AdminRole.SUPER_ADMIN,
                                permissions = AdminPermission.entries.map { it.name },
                                isActive = true
                            )
                        )
                    } else {
                        emit(null)
                    }
                }
        )
    }

    fun observeAllUsers(): Flow<List<UserAccount>> = flow {
        val path = "users"
        emitAll(
            db.collection("users").snapshots().map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val dto = doc.toObject(
                        FirestoreUserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@mapNotNull null
                    UserAccount(
                        id = doc.id,
                        phoneNumber = dto.phoneNumber,
                        fullName = dto.fullName,
                        email = dto.email,
                        avatarEmoji = dto.avatarEmoji,
                        passwordHash = dto.passwordHash,
                        salt = dto.salt,
                        masterPin = dto.masterPin,
                        isVerified = dto.isVerified,
                        autoSyncEnabled = dto.autoSyncEnabled,
                        darkModeEnabled = dto.darkModeEnabled,
                        totalQuotaBytes = dto.totalQuotaBytes,
                        accountStatus = dto.accountStatus.ifBlank { "ACTIVE" },
                        blockReason = dto.blockReason,
                        planId = dto.planId.ifBlank { "free" },
                        planName = dto.planName.ifBlank { "Free Vault" },
                        planTier = dto.planTier.ifBlank { "FREE" },
                        planPrice = dto.planPrice,
                        planExpiresAt = dto.planExpiresAt,
                        referralCode = dto.referralCode,
                        referredByUid = dto.referredByUid,
                        referredByCode = dto.referredByCode,
                        referralCount = dto.referralCount,
                        referralBonusBytes = dto.referralBonusBytes,
                        referralRewardPoints = dto.referralRewardPoints,
                        lastActiveAt = if (dto.lastActiveAt > 0L) dto.lastActiveAt else (dto.updatedAt?.toDate()?.time ?: 0L),
                        createdAt = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }.sortedByDescending { it.createdAt }
            }.catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (firestoreError != null) {
                    handleFirestoreError(firestoreError, OperationType.LIST, path)
                }
                emit(emptyList())
            }
        )
    }

    fun observeAllPlans(): Flow<List<SubscriptionPlan>> = flow {
        val path = "plans"
        emitAll(
            db.collection("plans").snapshots().map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val dto = doc.toObject(
                        FirestoreSubscriptionPlan::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@mapNotNull null
                    SubscriptionPlan(
                        id = doc.id,
                        name = dto.name,
                        tier = dto.tier,
                        price = dto.price,
                        currency = dto.currency,
                        durationDays = dto.durationDays.toInt(),
                        storageQuotaGb = dto.storageQuotaGb,
                        maxFileSizeMb = dto.maxFileSizeMb,
                        features = dto.features,
                        adsRemoved = dto.adsRemoved,
                        isActive = dto.isActive,
                        createdByUid = dto.createdByUid,
                        createdAt = dto.createdAt?.toDate()?.time ?: 0L,
                        updatedAt = dto.updatedAt?.toDate()?.time ?: 0L
                    )
                }.sortedBy { it.price }
            }.catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (firestoreError != null) handleFirestoreError(firestoreError, OperationType.LIST, path)
                emit(emptyList())
            }
        )
    }

    fun observeAllReferrals(): Flow<List<ReferralRecord>> = flow {
        val path = "referrals"
        emitAll(
            db.collection("referrals").snapshots().map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val dto = doc.toObject(
                        FirestoreReferralRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@mapNotNull null
                    ReferralRecord(
                        id = doc.id,
                        referrerUid = dto.referrerUid,
                        referrerName = dto.referrerName,
                        referrerPhone = dto.referrerPhone,
                        referralCode = dto.referralCode,
                        referredUid = dto.referredUid,
                        referredName = dto.referredName,
                        referredPhone = dto.referredPhone,
                        deviceFingerprintHash = dto.deviceFingerprintHash,
                        status = dto.status,
                        rewardPoints = dto.rewardPoints,
                        rewardStorageBytes = dto.rewardStorageBytes,
                        fraudReason = dto.fraudReason,
                        createdAt = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                        updatedAt = dto.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }.sortedByDescending { it.createdAt }
            }.catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (firestoreError != null) handleFirestoreError(firestoreError, OperationType.LIST, path)
                emit(emptyList())
            }
        )
    }

    fun observeAllAdmins(): Flow<List<AdminMember>> = flow {
        val path = "admins"
        emitAll(
            db.collection("admins").snapshots().map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val dto = doc.toObject(
                        FirestoreAdminMember::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@mapNotNull null
                    mapAdminDto(doc.id, dto)
                }.sortedByDescending { it.createdAt }
            }.catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (firestoreError != null) handleFirestoreError(firestoreError, OperationType.LIST, path)
                emit(emptyList())
            }
        )
    }

    fun observeAllNotifications(): Flow<List<AppNotification>> = flow {
        val path = "notifications"
        emitAll(
            db.collection("notifications").snapshots().map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val dto = doc.toObject(
                        FirestoreAppNotification::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@mapNotNull null
                    AppNotification(
                        id = doc.id,
                        title = dto.title,
                        message = dto.message,
                        priority = dto.priority,
                        targetAudience = dto.targetAudience,
                        isActive = dto.isActive,
                        createdByUid = dto.createdByUid,
                        createdAt = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                        updatedAt = dto.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }.sortedByDescending { it.createdAt }
            }.catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (firestoreError != null) handleFirestoreError(firestoreError, OperationType.LIST, path)
                emit(emptyList())
            }
        )
    }

    fun observeAuditLogs(): Flow<List<AdminAuditLog>> = flow {
        val path = "audit_logs"
        emitAll(
            db.collection("audit_logs").snapshots().map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val dto = doc.toObject(
                        FirestoreAdminAuditLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@mapNotNull null
                    AdminAuditLog(
                        id = doc.id,
                        actorUid = dto.actorUid,
                        actorName = dto.actorName,
                        actorRole = dto.actorRole,
                        action = dto.action,
                        targetType = dto.targetType,
                        targetId = dto.targetId,
                        details = dto.details,
                        createdAt = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }.sortedByDescending { it.createdAt }
            }.catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (firestoreError != null) handleFirestoreError(firestoreError, OperationType.LIST, path)
                emit(emptyList())
            }
        )
    }

    fun observeGlobalAppSettings(): Flow<GlobalAppSettings> = flow {
        val path = "app_settings/global"
        emitAll(
            db.collection("app_settings").document("global").snapshots().map { snapshot ->
                if (!snapshot.exists()) return@map GlobalAppSettings()
                val dto = snapshot.toObject(
                    FirestoreAppSettings::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: return@map GlobalAppSettings()
                GlobalAppSettings(
                    appName = dto.appName.ifBlank { "SafeVault" },
                    appTagline = dto.appTagline,
                    appLogoStyle = dto.appLogoStyle.ifBlank { "CYAN_SHIELD" },
                    maintenanceMode = dto.maintenanceMode,
                    maintenanceMessage = dto.maintenanceMessage,
                    announcementEnabled = dto.announcementEnabled,
                    announcementTitle = dto.announcementTitle,
                    announcementMessage = dto.announcementMessage,
                    supportEmail = dto.supportEmail,
                    supportPhone = dto.supportPhone,
                    supportWhatsApp = dto.supportWhatsApp,
                    termsAndConditions = dto.termsAndConditions.ifBlank { GlobalAppSettings.DEFAULT_TERMS },
                    privacyPolicy = dto.privacyPolicy.ifBlank { GlobalAppSettings.DEFAULT_PRIVACY },
                    referralEnabled = dto.referralEnabled,
                    referralRewardPoints = dto.referralRewardPoints,
                    referralRewardBonusMb = dto.referralRewardBonusMb,
                    updatedByUid = dto.updatedByUid,
                    updatedAt = dto.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }.catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (firestoreError != null) handleFirestoreError(firestoreError, OperationType.GET, path)
                emit(GlobalAppSettings())
            }
        )
    }

    // ------------------------------------------------------------------------
    // User Management (Block/Unblock, Delete, Change Plan, Extend/Reduce Expiry)
    // ------------------------------------------------------------------------

    suspend fun setUserBlockStatus(
        actor: AdminMember,
        targetUid: String,
        block: Boolean,
        reason: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val docRef = db.collection("users").document(targetUid)
        try {
            docRef.update(
                mapOf(
                    "accountStatus" to if (block) "BLOCKED" else "ACTIVE",
                    "blockReason" to if (block) reason.trim().ifBlank { "Blocked by administrator" }.take(300) else "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(
                actor = actor,
                action = if (block) "BLOCK_USER" else "UNBLOCK_USER",
                targetType = "USER",
                targetId = targetUid,
                details = if (block) "Blocked user: ${reason.ifBlank { "Policy enforcement" }}" else "Unblocked user account"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteUserAccount(
        actor: AdminMember,
        targetUser: UserAccount
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val userRef = db.collection("users").document(targetUser.id)
        try {
            val files = userRef.collection("files").get().await()
            for (f in files.documents) {
                val chunks = f.reference.collection("chunks").get().await()
                for (c in chunks.documents) c.reference.delete().await()
                f.reference.delete().await()
            }
            val folders = userRef.collection("folders").get().await()
            for (fld in folders.documents) fld.reference.delete().await()
            val vault = userRef.collection("vault_items").get().await()
            for (v in vault.documents) v.reference.delete().await()
            userRef.delete().await()

            writeAuditLog(
                actor = actor,
                action = "DELETE_USER",
                targetType = "USER",
                targetId = targetUser.id,
                details = "Permanently deleted user ${targetUser.fullName} (${targetUser.phoneNumber})"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun assignPlanToUser(
        actor: AdminMember,
        targetUid: String,
        plan: SubscriptionPlan,
        customDurationDays: Int? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val docRef = db.collection("users").document(targetUid)
        val effectiveDays = (customDurationDays ?: plan.durationDays).coerceAtLeast(0)
        val expiresAt = if (plan.tier.equals("FREE", ignoreCase = true) || effectiveDays == 0) {
            0L
        } else {
            System.currentTimeMillis() + effectiveDays * 86_400_000L
        }
        val quotaBytes = plan.storageQuotaGb.coerceAtLeast(1L) * 1024L * 1024L * 1024L

        try {
            docRef.update(
                mapOf(
                    "planId" to plan.id.ifBlank { "custom" }.take(128),
                    "planName" to plan.name.take(100),
                    "planTier" to plan.tier.uppercase(),
                    "planPrice" to plan.price.coerceAtLeast(0.0),
                    "planExpiresAt" to expiresAt,
                    "totalQuotaBytes" to quotaBytes,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(
                actor = actor,
                action = "ASSIGN_USER_PLAN",
                targetType = "USER",
                targetId = targetUid,
                details = "Assigned plan '${plan.name}' (${plan.tier}, ${plan.storageQuotaGb}GB, $effectiveDays days)"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun adjustUserPlanExpiry(
        actor: AdminMember,
        targetUser: UserAccount,
        deltaDays: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val docRef = db.collection("users").document(targetUser.id)
        val now = System.currentTimeMillis()
        val base = if (targetUser.planExpiresAt > now) targetUser.planExpiresAt else now
        val newExpiresAt = (base + deltaDays.toLong() * 86_400_000L).coerceAtLeast(0L)

        try {
            docRef.update(
                mapOf(
                    "planExpiresAt" to newExpiresAt,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val verb = if (deltaDays >= 0) "Extended (+$deltaDays days)" else "Reduced ($deltaDays days)"
            writeAuditLog(
                actor = actor,
                action = if (deltaDays >= 0) "EXTEND_PLAN_EXPIRY" else "REDUCE_PLAN_EXPIRY",
                targetType = "USER",
                targetId = targetUser.id,
                details = "$verb plan expiry for ${targetUser.fullName}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // Plan Management (Create, Edit, Delete)
    // ------------------------------------------------------------------------

    suspend fun savePlan(
        actor: AdminMember,
        plan: SubscriptionPlan
    ): Result<SubscriptionPlan> = withContext(Dispatchers.IO) {
        val isCreate = plan.id.isBlank()
        val planId = if (isCreate) {
            sanitizeId("pln_${UUID.randomUUID().toString().replace("-", "").take(12)}")
        } else {
            sanitizeId(plan.id)
        }
        val docRef = db.collection("plans").document(planId)
        val actorUid = requireActorUid()
        val cleanName = plan.name.trim().ifBlank { "Custom Vault Plan" }.take(80)
        val cleanTier = if (plan.tier.equals("PREMIUM", ignoreCase = true)) "PREMIUM" else "FREE"
        val cleanCurrency = plan.currency.trim().ifBlank { "USD" }.take(12)
        val cleanFeatures = plan.features.map { it.trim() }.filter { it.isNotBlank() }.take(15)

        try {
            if (isCreate) {
                val payload = mapOf(
                    "name" to cleanName,
                    "tier" to cleanTier,
                    "price" to plan.price.coerceAtLeast(0.0),
                    "currency" to cleanCurrency,
                    "durationDays" to plan.durationDays.coerceIn(0, 36500).toLong(),
                    "storageQuotaGb" to plan.storageQuotaGb.coerceIn(1L, 100000L),
                    "maxFileSizeMb" to plan.maxFileSizeMb.coerceIn(1L, 10000L),
                    "features" to cleanFeatures,
                    "adsRemoved" to plan.adsRemoved,
                    "isActive" to plan.isActive,
                    "createdByUid" to actorUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "name" to cleanName,
                    "tier" to cleanTier,
                    "price" to plan.price.coerceAtLeast(0.0),
                    "currency" to cleanCurrency,
                    "durationDays" to plan.durationDays.coerceIn(0, 36500).toLong(),
                    "storageQuotaGb" to plan.storageQuotaGb.coerceIn(1L, 100000L),
                    "maxFileSizeMb" to plan.maxFileSizeMb.coerceIn(1L, 10000L),
                    "features" to cleanFeatures,
                    "adsRemoved" to plan.adsRemoved,
                    "isActive" to plan.isActive,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }

            writeAuditLog(
                actor = actor,
                action = if (isCreate) "CREATE_PLAN" else "UPDATE_PLAN",
                targetType = "PLAN",
                targetId = planId,
                details = "$cleanName ($cleanTier, $cleanCurrency ${plan.price}, ${plan.storageQuotaGb}GB)"
            )
            Result.success(
                plan.copy(
                    id = planId,
                    name = cleanName,
                    tier = cleanTier,
                    currency = cleanCurrency,
                    features = cleanFeatures,
                    createdByUid = if (isCreate) actorUid else plan.createdByUid
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deletePlan(
        actor: AdminMember,
        planId: String,
        planName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val docRef = db.collection("plans").document(planId)
        try {
            docRef.delete().await()
            writeAuditLog(
                actor = actor,
                action = "DELETE_PLAN",
                targetType = "PLAN",
                targetId = planId,
                details = "Deleted subscription plan '$planName'"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // Referral & Anti-Fraud Control
    // ------------------------------------------------------------------------

    suspend fun reviewReferral(
        actor: AdminMember,
        referral: ReferralRecord,
        approve: Boolean,
        fraudReason: String,
        settings: GlobalAppSettings
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (referral.referredUid == referral.referrerUid) {
            return@withContext Result.failure(Exception("Self-referral blocked by anti-fraud policy."))
        }
        val refDocRef = db.collection("referrals").document(referral.id)
        val referrerDocRef = db.collection("users").document(referral.referrerUid)

        try {
            val points = if (approve) settings.referralRewardPoints.coerceAtLeast(0L) else 0L
            val bonusBytes = if (approve) settings.referralRewardBonusMb.coerceAtLeast(0L) * 1024L * 1024L else 0L

            db.runTransaction { tx ->
                val refSnap = tx.get(refDocRef)
                val currentStatus = refSnap.getString("status") ?: referral.status
                val prevPoints = refSnap.getLong("rewardPoints") ?: referral.rewardPoints
                val prevBytes = refSnap.getLong("rewardStorageBytes") ?: referral.rewardStorageBytes

                if (approve && currentStatus == "APPROVED") {
                    throw IllegalStateException("This referral reward has already been approved.")
                }

                val referrerSnap = tx.get(referrerDocRef)
                if (approve && referrerSnap.exists()) {
                    val curCount = referrerSnap.getLong("referralCount") ?: 0L
                    val curBonus = referrerSnap.getLong("referralBonusBytes") ?: 0L
                    val curPoints = referrerSnap.getLong("referralRewardPoints") ?: 0L
                    tx.update(
                        referrerDocRef,
                        mapOf(
                            "referralCount" to curCount + 1L,
                            "referralBonusBytes" to curBonus + bonusBytes,
                            "referralRewardPoints" to curPoints + points,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else if (!approve && currentStatus == "APPROVED" && referrerSnap.exists()) {
                    // Reversing a fraudulent previously approved referral
                    val curCount = referrerSnap.getLong("referralCount") ?: 0L
                    val curBonus = referrerSnap.getLong("referralBonusBytes") ?: 0L
                    val curPoints = referrerSnap.getLong("referralRewardPoints") ?: 0L
                    tx.update(
                        referrerDocRef,
                        mapOf(
                            "referralCount" to (curCount - 1L).coerceAtLeast(0L),
                            "referralBonusBytes" to (curBonus - prevBytes).coerceAtLeast(0L),
                            "referralRewardPoints" to (curPoints - prevPoints).coerceAtLeast(0L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                tx.update(
                    refDocRef,
                    mapOf(
                        "status" to if (approve) "APPROVED" else "REJECTED",
                        "rewardPoints" to points,
                        "rewardStorageBytes" to bonusBytes,
                        "fraudReason" to if (approve) "" else fraudReason.trim().ifBlank { "Flagged by admin review" }.take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            writeAuditLog(
                actor = actor,
                action = if (approve) "APPROVE_REFERRAL" else "REJECT_REFERRAL",
                targetType = "REFERRAL",
                targetId = referral.id,
                details = if (approve) {
                    "Approved referral ${referral.referralCode} for ${referral.referrerName} (+${points}pts)"
                } else {
                    "Rejected referral ${referral.referralCode}: ${fraudReason.ifBlank { "Fraud check" }}"
                }
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, refDocRef.path)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // App Settings & Broadcast Notifications
    // ------------------------------------------------------------------------

    suspend fun updateGlobalAppSettings(
        actor: AdminMember,
        settings: GlobalAppSettings
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val actorUid = requireActorUid()
        val docRef = db.collection("app_settings").document("global")
        val payload = mapOf(
            "appName" to settings.appName.trim().ifBlank { "SafeVault" }.take(40),
            "appTagline" to settings.appTagline.trim().take(160),
            "appLogoStyle" to settings.appLogoStyle.trim().ifBlank { "CYAN_SHIELD" }.take(64),
            "maintenanceMode" to settings.maintenanceMode,
            "maintenanceMessage" to settings.maintenanceMessage.trim().take(500),
            "announcementEnabled" to settings.announcementEnabled,
            "announcementTitle" to settings.announcementTitle.trim().take(120),
            "announcementMessage" to settings.announcementMessage.trim().take(1000),
            "supportEmail" to settings.supportEmail.trim().take(120),
            "supportPhone" to settings.supportPhone.trim().take(40),
            "supportWhatsApp" to settings.supportWhatsApp.trim().take(40),
            "termsAndConditions" to settings.termsAndConditions.take(15000),
            "privacyPolicy" to settings.privacyPolicy.take(15000),
            "referralEnabled" to settings.referralEnabled,
            "referralRewardPoints" to settings.referralRewardPoints.coerceIn(0L, 1_000_000L),
            "referralRewardBonusMb" to settings.referralRewardBonusMb.coerceIn(0L, 102_400L),
            "updatedByUid" to actorUid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            val exists = docRef.get().await().exists()
            if (exists) {
                docRef.update(payload).await()
            } else {
                docRef.set(payload).await()
            }
            writeAuditLog(
                actor = actor,
                action = "UPDATE_APP_SETTINGS",
                targetType = "SETTINGS",
                targetId = "global",
                details = "Updated settings (name='${settings.appName}', maintenance=${settings.maintenanceMode}, referralPts=${settings.referralRewardPoints})"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveNotification(
        actor: AdminMember,
        notification: AppNotification
    ): Result<AppNotification> = withContext(Dispatchers.IO) {
        val actorUid = requireActorUid()
        val isCreate = notification.id.isBlank()
        val notifId = if (isCreate) {
            sanitizeId("ntf_${UUID.randomUUID().toString().replace("-", "").take(12)}")
        } else {
            sanitizeId(notification.id)
        }
        val docRef = db.collection("notifications").document(notifId)
        val cleanTitle = notification.title.trim().ifBlank { "Announcement" }.take(120)
        val cleanMessage = notification.message.trim().ifBlank { "Update from SafeVault" }.take(1000)
        val cleanPriority = if (notification.priority in listOf("INFO", "WARNING", "PROMO")) notification.priority else "INFO"
        val cleanAudience = if (notification.targetAudience in listOf("ALL", "FREE", "PREMIUM")) notification.targetAudience else "ALL"

        try {
            if (isCreate) {
                docRef.set(
                    mapOf(
                        "title" to cleanTitle,
                        "message" to cleanMessage,
                        "priority" to cleanPriority,
                        "targetAudience" to cleanAudience,
                        "isActive" to notification.isActive,
                        "createdByUid" to actorUid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "title" to cleanTitle,
                        "message" to cleanMessage,
                        "priority" to cleanPriority,
                        "targetAudience" to cleanAudience,
                        "isActive" to notification.isActive,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            writeAuditLog(
                actor = actor,
                action = if (isCreate) "CREATE_NOTIFICATION" else "UPDATE_NOTIFICATION",
                targetType = "NOTIFICATION",
                targetId = notifId,
                details = "[$cleanPriority/$cleanAudience] $cleanTitle (active=${notification.isActive})"
            )
            Result.success(
                notification.copy(
                    id = notifId,
                    title = cleanTitle,
                    message = cleanMessage,
                    priority = cleanPriority,
                    targetAudience = cleanAudience
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteNotification(
        actor: AdminMember,
        notifId: String,
        title: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val docRef = db.collection("notifications").document(notifId)
        try {
            docRef.delete().await()
            writeAuditLog(
                actor = actor,
                action = "DELETE_NOTIFICATION",
                targetType = "NOTIFICATION",
                targetId = notifId,
                details = "Deleted broadcast notification '$title'"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // Admin Security, Multi-Role Management & Secondary PIN Lock
    // ------------------------------------------------------------------------

    suspend fun saveAdminMember(
        actor: AdminMember,
        targetUid: String,
        fullName: String,
        phoneNumber: String,
        email: String,
        role: AdminRole,
        permissions: List<AdminPermission>,
        isActive: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUid = sanitizeId(targetUid.trim())
        if (cleanUid.isBlank()) {
            return@withContext Result.failure(Exception("Target Firebase Auth UID is required."))
        }
        val docRef = db.collection("admins").document(cleanUid)
        val cleanName = fullName.trim().ifBlank { "SafeVault Admin" }.take(100)
        val permStrings = permissions.map { it.name }.distinct()

        try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(
                    mapOf(
                        "uid" to cleanUid,
                        "fullName" to cleanName,
                        "phoneNumber" to phoneNumber.trim().take(30),
                        "email" to email.trim().take(120),
                        "role" to role.name,
                        "permissions" to permStrings,
                        "adminPinHash" to "",
                        "adminPinSalt" to "",
                        "isActive" to isActive,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "fullName" to cleanName,
                        "phoneNumber" to phoneNumber.trim().take(30),
                        "email" to email.trim().take(120),
                        "role" to role.name,
                        "permissions" to permStrings,
                        "isActive" to isActive,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            writeAuditLog(
                actor = actor,
                action = if (!snap.exists()) "GRANT_ADMIN_ROLE" else "UPDATE_ADMIN_ROLE",
                targetType = "ADMIN",
                targetId = cleanUid,
                details = "Admin $cleanName -> role=${role.name}, active=$isActive"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun revokeAdminMember(
        actor: AdminMember,
        targetUid: String,
        targetName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (targetUid == actor.uid) {
            return@withContext Result.failure(Exception("You cannot revoke your own active Super Admin access."))
        }
        val docRef = db.collection("admins").document(targetUid)
        try {
            docRef.delete().await()
            writeAuditLog(
                actor = actor,
                action = "REVOKE_ADMIN_ROLE",
                targetType = "ADMIN",
                targetId = targetUid,
                details = "Revoked admin privileges for $targetName"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun setOwnAdminPin(
        actor: AdminMember,
        newPin: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (newPin.length < 4 || newPin.length > 8) {
            return@withContext Result.failure(Exception("Admin Security PIN must be 4 to 8 digits."))
        }
        val actorUid = requireActorUid()
        val docRef = db.collection("admins").document(actorUid)
        val salt = CryptoUtils.generateSalt()
        val hash = CryptoUtils.hashPassword(newPin, salt)

        try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(
                    mapOf(
                        "uid" to actorUid,
                        "fullName" to actor.fullName.ifBlank { "Owner Admin" }.take(100),
                        "phoneNumber" to actor.phoneNumber.take(30),
                        "email" to actor.email.take(120),
                        "role" to actor.role.name,
                        "permissions" to actor.permissions.ifEmpty { AdminPermission.entries.map { it.name } },
                        "adminPinHash" to hash,
                        "adminPinSalt" to salt,
                        "isActive" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "adminPinHash" to hash,
                        "adminPinSalt" to salt,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            writeAuditLog(
                actor = actor,
                action = "UPDATE_ADMIN_PIN",
                targetType = "ADMIN",
                targetId = actorUid,
                details = "Configured secondary Admin Security PIN"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun verifyAdminPin(admin: AdminMember, pinInput: String): Boolean {
        if (!admin.hasPinConfigured) return true
        val testHash = CryptoUtils.hashPassword(pinInput, admin.adminPinSalt)
        return testHash == admin.adminPinHash
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private suspend fun writeAuditLog(
        actor: AdminMember,
        action: String,
        targetType: String,
        targetId: String,
        details: String
    ) {
        val actorUid = auth.currentUser?.uid ?: actor.uid
        val logId = sanitizeId("log_${UUID.randomUUID().toString().replace("-", "").take(16)}")
        try {
            db.collection("audit_logs").document(logId).set(
                mapOf(
                    "actorUid" to actorUid,
                    "actorName" to actor.fullName.ifBlank { "Admin" }.take(100),
                    "actorRole" to actor.role.name,
                    "action" to action.take(64),
                    "targetType" to targetType,
                    "targetId" to targetId.ifBlank { "system" }.take(128),
                    "details" to details.take(1000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "audit_logs/$logId")
        }
    }

    private fun mapAdminDto(docId: String, dto: FirestoreAdminMember): AdminMember {
        val roleEnum = try {
            AdminRole.valueOf(dto.role)
        } catch (_: Exception) {
            AdminRole.MODERATOR
        }
        return AdminMember(
            uid = dto.uid.ifBlank { docId },
            fullName = dto.fullName,
            phoneNumber = dto.phoneNumber,
            email = dto.email,
            role = roleEnum,
            permissions = dto.permissions,
            adminPinHash = dto.adminPinHash,
            adminPinSalt = dto.adminPinSalt,
            isActive = dto.isActive,
            createdAt = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis()
        )
    }
}
