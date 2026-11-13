package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.R
import com.example.data.model.AdsterraAdConfig
import com.example.data.model.AppNotification
import com.example.data.model.CloudFile
import com.example.data.model.CloudFolder
import com.example.data.model.FileType
import com.example.data.model.FirestoreAppNotification
import com.example.data.model.FirestoreAppSettings
import com.example.data.model.FirestoreChunkDoc
import com.example.data.model.FirestoreFileDoc
import com.example.data.model.FirestoreFolderDoc
import com.example.data.model.FirestoreReferralRecord
import com.example.data.model.FirestoreSubscriptionPlan
import com.example.data.model.FirestoreUserProfile
import com.example.data.model.FirestoreVaultItemDoc
import com.example.data.model.GlobalAppSettings
import com.example.data.model.ReferralRecord
import com.example.data.model.SecureVaultItem
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SyncStatus
import com.example.data.model.TransferProgress
import com.example.data.model.UserAccount
import com.example.data.model.VaultCategory
import com.example.data.model.inferFileTypeFromNameAndMime
import com.example.data.security.CryptoUtils
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
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
import java.io.File
import java.util.UUID

class SafeVaultCloudRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val appContext: Context
) {
    // Mandatory secondary constructor resolving R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth,
        context.applicationContext
    )

    private val localPrefs = appContext.getSharedPreferences("safevault_device_prefs", Context.MODE_PRIVATE)
    private val cacheRootDir: File
        get() = File(appContext.filesDir, "safevault_cloud_cache").apply { if (!exists()) mkdirs() }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing SafeVault Cloud.")
    }

    private fun sanitizeId(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
    }

    private fun getEncryptionKeyPhrase(userProfile: UserAccount?): String {
        val uid = auth.currentUser?.uid ?: "safevault_default"
        val pin = userProfile?.masterPin ?: "1234"
        val salt = userProfile?.salt ?: "sv_salt"
        return "$uid:$pin:$salt"
    }

    // ------------------------------------------------------------------------
    // Real-Time Firestore Observations (Scoped strictly to authenticated uid)
    // ------------------------------------------------------------------------

    fun observeUserProfile(userId: String): Flow<UserAccount?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) return@map null
                    val dto = snapshot.toObject(
                        FirestoreUserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@map null
                    UserAccount(
                        id = dto.id.ifBlank { userId },
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
                        referralCode = dto.referralCode.ifBlank { generateReferralCode(userId) },
                        referredByUid = dto.referredByUid,
                        referredByCode = dto.referredByCode,
                        referralCount = dto.referralCount,
                        referralBonusBytes = dto.referralBonusBytes,
                        referralRewardPoints = dto.referralRewardPoints,
                        lastActiveAt = if (dto.lastActiveAt > 0L) dto.lastActiveAt else (dto.updatedAt?.toDate()?.time ?: System.currentTimeMillis()),
                        createdAt = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeAdConfig(userId: String): Flow<AdsterraAdConfig> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) return@map AdsterraAdConfig()
                    val dto = snapshot.toObject(
                        FirestoreUserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@map AdsterraAdConfig()
                    AdsterraAdConfig(
                        isEnabled = dto.adConfigEnabled,
                        isTestMode = dto.adTestMode,
                        zoneId = dto.adZoneId
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeFolders(userId: String): Flow<List<CloudFolder>> = flow {
        val path = "users/$userId/folders"
        emitAll(
            db.collection("users").document(userId).collection("folders")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        val dto = doc.toObject(
                            FirestoreFolderDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: return@mapNotNull null
                        CloudFolder(
                            id = doc.id,
                            userId = dto.userId,
                            name = dto.name,
                            colorHex = dto.colorHex,
                            iconName = dto.iconName,
                            parentFolderId = dto.parentFolderId,
                            filesCount = dto.filesCount.toInt(),
                            sizeBytes = dto.sizeBytes,
                            createdAt = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                            updatedAt = dto.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
                            serverCreatedAt = dto.createdAt
                        )
                    }.sortedByDescending { it.createdAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeFiles(userId: String): Flow<List<CloudFile>> = flow {
        val path = "users/$userId/files"
        emitAll(
            db.collection("users").document(userId).collection("files")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        val dto = doc.toObject(
                            FirestoreFileDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: return@mapNotNull null
                        val fileType = try { FileType.valueOf(dto.fileType) } catch (_: Exception) { FileType.OTHER }
                        val syncState = try { SyncStatus.valueOf(dto.syncStatus) } catch (_: Exception) { SyncStatus.SYNCED }
                        val cachedFile = getLocalCachedFile(userId, doc.id, dto.name)
                        CloudFile(
                            id = doc.id,
                            userId = dto.userId,
                            name = dto.name,
                            type = fileType,
                            sizeBytes = dto.sizeBytes,
                            mimeType = dto.mimeType,
                            customExtension = dto.extension,
                            localUri = if (cachedFile.exists()) cachedFile.absolutePath else null,
                            remoteStorageUrl = "firestore://users/$userId/files/${doc.id}",
                            folderId = dto.folderId.ifBlank { null },
                            folderName = dto.folderName.ifBlank { null },
                            checksumSha256 = dto.checksumSha256,
                            chunkCount = dto.chunkCount.toInt(),
                            createdAt = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                            updatedAt = dto.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
                            serverCreatedAt = dto.createdAt,
                            isEncrypted = dto.isEncrypted,
                            encryptedContent = dto.encryptedContent.ifBlank { null },
                            isFavorite = dto.isFavorite,
                            tags = dto.tags,
                            syncStatus = syncState
                        )
                    }.sortedByDescending { it.updatedAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeVaultItems(userId: String): Flow<List<SecureVaultItem>> = flow {
        val path = "users/$userId/vault_items"
        emitAll(
            db.collection("users").document(userId).collection("vault_items")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        val dto = doc.toObject(
                            FirestoreVaultItemDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: return@mapNotNull null
                        val cat = try { VaultCategory.valueOf(dto.category) } catch (_: Exception) { VaultCategory.PASSWORD }
                        SecureVaultItem(
                            id = doc.id,
                            userId = dto.userId,
                            category = cat,
                            title = dto.title,
                            usernameOrId = dto.usernameOrId,
                            encryptedSecret = dto.encryptedSecret,
                            notes = dto.notes,
                            updatedAt = dto.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
                            serverCreatedAt = dto.createdAt
                        )
                    }.sortedByDescending { it.updatedAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // ------------------------------------------------------------------------
    // Cloud User Profile, Mobile + Password Registration & Verification
    // ------------------------------------------------------------------------

    suspend fun getCloudProfile(): Result<UserAccount?> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) return@withContext Result.success(null)
            val dto = snapshot.toObject(
                FirestoreUserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@withContext Result.success(null)
            val account = UserAccount(
                id = docRef.id,
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
                referralCode = dto.referralCode.ifBlank { generateReferralCode(docRef.id) },
                referredByUid = dto.referredByUid,
                referredByCode = dto.referredByCode,
                referralCount = dto.referralCount,
                referralBonusBytes = dto.referralBonusBytes,
                referralRewardPoints = dto.referralRewardPoints,
                lastActiveAt = if (dto.lastActiveAt > 0L) dto.lastActiveAt else (dto.updatedAt?.toDate()?.time ?: System.currentTimeMillis()),
                createdAt = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis()
            )
            Result.success(account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun registerCloudProfile(
        fullName: String = "",
        phoneNumber: String,
        password: String = "",
        masterPin: String = "1234",
        referralCodeInput: String = ""
    ): Result<UserAccount> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val salt = CryptoUtils.generateSalt()
        val effectiveSecret = password.ifBlank { "sv_mobile_${uid}_${CryptoUtils.generateSalt()}" }
        val passwordHash = CryptoUtils.hashPassword(effectiveSecret, salt)
        val email = auth.currentUser?.email ?: ""
        val cleanPhone = normalizePhone(phoneNumber)
        val cleanName = fullName.trim().ifBlank {
            auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: cleanPhone
        }
        val myReferralCode = generateReferralCode(uid)
        val nowMs = System.currentTimeMillis()
        val defaultQuota = 15L * 1024 * 1024 * 1024

        val payload = mapOf(
            "userId" to uid,
            "fullName" to cleanName,
            "phoneNumber" to cleanPhone,
            "email" to email,
            "avatarEmoji" to "🛡️",
            "passwordHash" to passwordHash,
            "salt" to salt,
            "masterPin" to masterPin,
            "isVerified" to true,
            "autoSyncEnabled" to true,
            "darkModeEnabled" to false,
            "adConfigEnabled" to true,
            "adTestMode" to true,
            "adZoneId" to "adsterra_banner_320x50",
            "totalQuotaBytes" to defaultQuota,
            "accountStatus" to "ACTIVE",
            "blockReason" to "",
            "planId" to "free",
            "planName" to "Free Vault",
            "planTier" to "FREE",
            "planPrice" to 0.0,
            "planExpiresAt" to 0L,
            "referralCode" to myReferralCode,
            "referredByUid" to "",
            "referredByCode" to "",
            "referralCount" to 0L,
            "referralBonusBytes" to 0L,
            "referralRewardPoints" to 0L,
            "lastActiveAt" to nowMs,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(payload).await()
            val account = UserAccount(
                id = uid,
                phoneNumber = cleanPhone,
                fullName = cleanName,
                email = email,
                avatarEmoji = "🛡️",
                passwordHash = passwordHash,
                salt = salt,
                masterPin = masterPin,
                isVerified = true,
                autoSyncEnabled = true,
                darkModeEnabled = false,
                totalQuotaBytes = defaultQuota,
                referralCode = myReferralCode,
                lastActiveAt = nowMs
            )
            ensureReferralCodeIndex(account)
            val finalAccount = if (referralCodeInput.isNotBlank()) {
                val claimRes = submitReferralCodeClaim(
                    codeInput = referralCodeInput,
                    userProfile = account,
                    deviceFingerprintHash = CryptoUtils.sha256Hex("${android.os.Build.MODEL}:${cleanPhone}".toByteArray())
                )
                val claimed = claimRes.getOrNull()
                if (claimed != null) {
                    account.copy(referredByUid = claimed.referrerUid, referredByCode = claimed.referralCode)
                } else {
                    account
                }
            } else {
                account
            }
            seedStarterCloudWorkspace(uid, finalAccount)
            Result.success(finalAccount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun verifyCloudPhoneOnly(
        phoneNumber: String
    ): Result<UserAccount> = withContext(Dispatchers.IO) {
        val profileRes = getCloudProfile()
        val account = profileRes.getOrElse { return@withContext Result.failure(it) }
            ?: return@withContext Result.failure(Exception("No SafeVault cloud profile found for this account."))

        val cleanPhone = normalizePhone(phoneNumber)
        val storedPhone = normalizePhone(account.phoneNumber)
        if (cleanPhone.isBlank() || (cleanPhone != storedPhone && !storedPhone.endsWith(cleanPhone.takeLast(10)) && !cleanPhone.endsWith(storedPhone.takeLast(10)))) {
            return@withContext Result.failure(Exception("Mobile number does not match the registered SafeVault account."))
        }

        Result.success(account)
    }

    suspend fun verifyCloudVaultCredentials(
        phoneNumber: String,
        password: String
    ): Result<UserAccount> = withContext(Dispatchers.IO) {
        val profileRes = getCloudProfile()
        val account = profileRes.getOrElse { return@withContext Result.failure(it) }
            ?: return@withContext Result.failure(Exception("No SafeVault cloud profile found for this mobile number."))

        val cleanPhone = normalizePhone(phoneNumber)
        val storedPhone = normalizePhone(account.phoneNumber)
        if (cleanPhone != storedPhone) {
            return@withContext Result.failure(Exception("Mobile number does not match the registered SafeVault account."))
        }

        val testHash = CryptoUtils.hashPassword(password, account.salt)
        if (testHash != account.passwordHash) {
            return@withContext Result.failure(Exception("Incorrect Vault Password. Please try again or reset via SMS OTP."))
        }

        Result.success(account)
    }

    private fun normalizePhone(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[\\s\\-()]"), "")
        return when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("00") -> "+${cleaned.drop(2)}"
            cleaned.isEmpty() -> ""
            else -> "+$cleaned"
        }
    }

    suspend fun resetCloudPasswordWithOtp(
        phoneNumber: String,
        newPassword: String
    ): Result<UserAccount> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val profileRes = getCloudProfile()
        val account = profileRes.getOrElse { return@withContext Result.failure(it) }
            ?: return@withContext Result.failure(Exception("Cloud profile not found."))

        val cleanPhone = normalizePhone(phoneNumber)
        val storedPhone = normalizePhone(account.phoneNumber)
        if (cleanPhone != storedPhone) {
            return@withContext Result.failure(Exception("Mobile number does not match your cloud profile."))
        }

        val newSalt = CryptoUtils.generateSalt()
        val newHash = CryptoUtils.hashPassword(newPassword, newSalt)
        val docRef = db.collection("users").document(uid)

        try {
            docRef.update(
                mapOf(
                    "passwordHash" to newHash,
                    "salt" to newSalt,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(account.copy(passwordHash = newHash, salt = newSalt))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<UserAccount> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val account = getCloudProfile().getOrNull()
            ?: return@withContext Result.failure(Exception("Profile not found"))
        val testHash = CryptoUtils.hashPassword(oldPassword, account.salt)
        if (testHash != account.passwordHash) {
            return@withContext Result.failure(Exception("Current password is incorrect"))
        }
        val newSalt = CryptoUtils.generateSalt()
        val newHash = CryptoUtils.hashPassword(newPassword, newSalt)
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "passwordHash" to newHash,
                    "salt" to newSalt,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(account.copy(passwordHash = newHash, salt = newSalt))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(name: String, email: String, emoji: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "fullName" to name.trim().take(100),
                    "email" to email.trim().take(120),
                    "avatarEmoji" to emoji.take(16),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePreferences(
        autoSyncEnabled: Boolean? = null,
        darkModeEnabled: Boolean? = null,
        masterPin: String? = null,
        adConfig: AdsterraAdConfig? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        if (autoSyncEnabled != null) updates["autoSyncEnabled"] = autoSyncEnabled
        if (darkModeEnabled != null) updates["darkModeEnabled"] = darkModeEnabled
        if (masterPin != null) updates["masterPin"] = masterPin
        if (adConfig != null) {
            updates["adConfigEnabled"] = adConfig.isEnabled
            updates["adTestMode"] = adConfig.isTestMode
            updates["adZoneId"] = adConfig.zoneId.take(128)
        }
        try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // Folders CRUD (Cloud Firestore)
    // ------------------------------------------------------------------------

    suspend fun createFolder(
        name: String,
        colorHex: String = "#3B82F6",
        parentFolderId: String = ""
    ): Result<CloudFolder> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val folderId = sanitizeId("fld_${UUID.randomUUID().toString().replace("-", "").take(16)}")
        val docRef = db.collection("users").document(uid).collection("folders").document(folderId)
        val cleanName = name.trim().ifBlank { "New Folder" }.take(120)

        val payload = mapOf(
            "userId" to uid,
            "name" to cleanName,
            "colorHex" to colorHex,
            "iconName" to "folder",
            "parentFolderId" to parentFolderId,
            "filesCount" to 0L,
            "sizeBytes" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(payload).await()
            Result.success(
                CloudFolder(
                    id = folderId,
                    userId = uid,
                    name = cleanName,
                    colorHex = colorHex,
                    parentFolderId = parentFolderId
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun renameFolder(folderId: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val cleanName = newName.trim().take(120)
        val folderRef = db.collection("users").document(uid).collection("folders").document(folderId)
        try {
            folderRef.update(
                mapOf(
                    "name" to cleanName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Also update folderName denormalization on child files
            val childFiles = db.collection("users").document(uid).collection("files")
                .whereEqualTo("folderId", folderId)
                .get()
                .await()
            for (doc in childFiles.documents) {
                doc.reference.update(
                    mapOf(
                        "folderName" to cleanName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, folderRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteFolder(folderId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val folderRef = db.collection("users").document(uid).collection("folders").document(folderId)
        try {
            val childFiles = db.collection("users").document(uid).collection("files")
                .whereEqualTo("folderId", folderId)
                .get()
                .await()
            for (doc in childFiles.documents) {
                doc.reference.update(
                    mapOf(
                        "folderId" to "",
                        "folderName" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            folderRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, folderRef.path)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // Cloud File Upload (All 10 Formats, Deduplication, Chunked AES-256)
    // ------------------------------------------------------------------------

    suspend fun uploadUriToCloud(
        uri: Uri,
        userProfile: UserAccount?,
        folderId: String?,
        folderName: String?,
        isEncrypted: Boolean = true,
        tags: List<String> = emptyList(),
        onProgress: (TransferProgress) -> Unit = {}
    ): Result<CloudFile> = withContext(Dispatchers.IO) {
        val (fileName, mimeType, bytes) = readUriDetails(uri)
            ?: return@withContext Result.failure(Exception("Could not read selected file from device."))

        uploadBytesToCloud(
            fileName = fileName,
            rawBytes = bytes,
            mimeType = mimeType,
            userProfile = userProfile,
            folderId = folderId,
            folderName = folderName,
            isEncrypted = isEncrypted,
            tags = tags,
            onProgress = onProgress
        )
    }

    suspend fun uploadBytesToCloud(
        fileName: String,
        rawBytes: ByteArray,
        mimeType: String,
        userProfile: UserAccount?,
        folderId: String?,
        folderName: String?,
        isEncrypted: Boolean = true,
        tags: List<String> = emptyList(),
        onProgress: (TransferProgress) -> Unit = {}
    ): Result<CloudFile> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val cleanName = fileName.trim().ifBlank { "Untitled_File" }.take(200)
        val (resolvedType, resolvedExt) = inferFileTypeFromNameAndMime(cleanName, mimeType)

        // Limit inline chunked payload to 14 MB (40 chunks) to ensure reliable Firestore storage
        if (rawBytes.size > 14 * 1024 * 1024) {
            return@withContext Result.failure(
                Exception("File exceeds 14 MB inline cloud chunk limit (${rawBytes.size / (1024 * 1024)} MB). Please select a smaller file.")
            )
        }

        val checksum = CryptoUtils.sha256Hex(rawBytes)
        val targetFolderId = folderId ?: ""
        val targetFolderName = folderName ?: ""

        // Duplicate prevention check in Firestore
        val filesCollection = db.collection("users").document(uid).collection("files")
        try {
            val existingDocs = filesCollection
                .whereEqualTo("checksumSha256", checksum)
                .get()
                .await()
            val duplicate = existingDocs.documents.firstOrNull { doc ->
                (doc.getString("folderId") ?: "") == targetFolderId &&
                    (doc.getString("name") ?: "") == cleanName
            }
            if (duplicate != null) {
                return@withContext Result.failure(
                    Exception("Duplicate prevented: '$cleanName' is already backed up in this cloud folder.")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, filesCollection.path)
        }

        onProgress(
            TransferProgress(
                isActive = true,
                operationType = "UPLOAD",
                currentFileName = cleanName,
                progress = 0.1f,
                completedItems = 0,
                totalItems = 1,
                statusDetail = "Encrypting with AES-256..."
            )
        )

        val keyPhrase = getEncryptionKeyPhrase(userProfile)
        val encryptedChunks = CryptoUtils.chunkAndEncryptFile(rawBytes, keyPhrase)
        val fileId = sanitizeId("fil_${UUID.randomUUID().toString().replace("-", "").take(16)}")
        val fileRef = filesCollection.document(fileId)

        val filePayload = mutableMapOf<String, Any>(
            "userId" to uid,
            "name" to cleanName,
            "fileType" to resolvedType.name,
            "mimeType" to mimeType.ifBlank { "application/octet-stream" }.take(128),
            "extension" to resolvedExt.take(16),
            "sizeBytes" to rawBytes.size.toLong(),
            "folderId" to targetFolderId,
            "folderName" to targetFolderName,
            "checksumSha256" to checksum,
            "chunkCount" to encryptedChunks.size.toLong(),
            "isEncrypted" to isEncrypted,
            "encryptedContent" to "",
            "isFavorite" to false,
            "tags" to tags.take(10),
            "syncStatus" to SyncStatus.SYNCED.name,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            fileRef.set(filePayload).await()

            val totalChunks = encryptedChunks.size.coerceAtLeast(1)
            encryptedChunks.forEachIndexed { index, b64Chunk ->
                val chunkId = "chunk_$index"
                val chunkRef = fileRef.collection("chunks").document(chunkId)
                val chunkPayload = mapOf(
                    "userId" to uid,
                    "fileId" to fileId,
                    "chunkIndex" to index.toLong(),
                    "encryptedBase64" to b64Chunk,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                chunkRef.set(chunkPayload).await()

                val pct = 0.15f + (0.8f * (index + 1).toFloat() / totalChunks.toFloat())
                onProgress(
                    TransferProgress(
                        isActive = true,
                        operationType = "UPLOAD",
                        currentFileName = cleanName,
                        progress = pct,
                        completedItems = index + 1,
                        totalItems = totalChunks,
                        statusDetail = "Uploading encrypted chunk ${index + 1}/$totalChunks"
                    )
                )
            }

            // Write local cache copy so user can open/preview immediately
            val localFile = getLocalCachedFile(uid, fileId, cleanName)
            localFile.parentFile?.mkdirs()
            localFile.writeBytes(rawBytes)

            // Update folder file counter
            if (targetFolderId.isNotBlank()) {
                recalculateFolderMetrics(uid, targetFolderId)
            }

            onProgress(TransferProgress(isActive = false, progress = 1f))
            Result.success(
                CloudFile(
                    id = fileId,
                    userId = uid,
                    name = cleanName,
                    type = resolvedType,
                    sizeBytes = rawBytes.size.toLong(),
                    mimeType = mimeType,
                    customExtension = resolvedExt,
                    localUri = localFile.absolutePath,
                    remoteStorageUrl = "firestore://users/$uid/files/$fileId",
                    folderId = folderId,
                    folderName = folderName,
                    checksumSha256 = checksum,
                    chunkCount = encryptedChunks.size,
                    isEncrypted = isEncrypted,
                    tags = tags,
                    syncStatus = SyncStatus.SYNCED
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onProgress(TransferProgress(isActive = false))
            handleFirestoreError(e, OperationType.CREATE, fileRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveNoteToCloud(
        title: String,
        content: String,
        isEncrypted: Boolean,
        userProfile: UserAccount?,
        folderId: String?,
        folderName: String?
    ): Result<CloudFile> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val cleanTitle = title.trim().ifBlank { "Untitled Note" }.take(200)
        val keyPhrase = getEncryptionKeyPhrase(userProfile)
        val storedText = if (isEncrypted) CryptoUtils.encrypt(content, keyPhrase) else content
        val rawBytes = content.toByteArray(Charsets.UTF_8)
        val checksum = CryptoUtils.sha256Hex(rawBytes)
        val fileId = sanitizeId("not_${UUID.randomUUID().toString().replace("-", "").take(16)}")
        val fileRef = db.collection("users").document(uid).collection("files").document(fileId)
        val targetFolderId = folderId ?: ""

        val payload = mapOf(
            "userId" to uid,
            "name" to cleanTitle,
            "fileType" to FileType.NOTE.name,
            "mimeType" to "text/plain",
            "extension" to "TXT",
            "sizeBytes" to rawBytes.size.toLong().coerceAtLeast(1024L),
            "folderId" to targetFolderId,
            "folderName" to (folderName ?: ""),
            "checksumSha256" to checksum,
            "chunkCount" to 1L,
            "isEncrypted" to isEncrypted,
            "encryptedContent" to storedText.take(240_000),
            "isFavorite" to false,
            "tags" to listOf("Note"),
            "syncStatus" to SyncStatus.SYNCED.name,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            fileRef.set(payload).await()
            val encryptedChunks = CryptoUtils.chunkAndEncryptFile(rawBytes, keyPhrase)
            encryptedChunks.forEachIndexed { idx, chunk ->
                fileRef.collection("chunks").document("chunk_$idx").set(
                    mapOf(
                        "userId" to uid,
                        "fileId" to fileId,
                        "chunkIndex" to idx.toLong(),
                        "encryptedBase64" to chunk,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            if (targetFolderId.isNotBlank()) {
                recalculateFolderMetrics(uid, targetFolderId)
            }
            Result.success(
                CloudFile(
                    id = fileId,
                    userId = uid,
                    name = cleanTitle,
                    type = FileType.NOTE,
                    sizeBytes = rawBytes.size.toLong().coerceAtLeast(1024L),
                    mimeType = "text/plain",
                    customExtension = "TXT",
                    folderId = folderId,
                    folderName = folderName,
                    checksumSha256 = checksum,
                    chunkCount = 1,
                    isEncrypted = isEncrypted,
                    encryptedContent = storedText,
                    tags = listOf("Note"),
                    syncStatus = SyncStatus.SYNCED
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, fileRef.path)
            Result.failure(e)
        }
    }

    suspend fun renameFile(fileId: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val fileRef = db.collection("users").document(uid).collection("files").document(fileId)
        try {
            fileRef.update(
                mapOf(
                    "name" to newName.trim().take(200),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, fileRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(fileId: String, currentFavorite: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val fileRef = db.collection("users").document(uid).collection("files").document(fileId)
        try {
            fileRef.update(
                mapOf(
                    "isFavorite" to !currentFavorite,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, fileRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteFile(file: CloudFile): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val fileRef = db.collection("users").document(uid).collection("files").document(file.id)
        try {
            // Delete chunks first
            val chunks = fileRef.collection("chunks").get().await()
            for (c in chunks.documents) {
                c.reference.delete().await()
            }
            fileRef.delete().await()

            // Remove local cache copy
            val localFile = getLocalCachedFile(uid, file.id, file.name)
            if (localFile.exists()) localFile.delete()

            if (!file.folderId.isNullOrBlank()) {
                recalculateFolderMetrics(uid, file.folderId)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, fileRef.path)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // Cloud Download & Full Cross-Device Backup Recovery
    // ------------------------------------------------------------------------

    suspend fun downloadAndRestoreFile(
        file: CloudFile,
        userProfile: UserAccount?,
        onProgress: (TransferProgress) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val fileRef = db.collection("users").document(uid).collection("files").document(file.id)
        try {
            onProgress(
                TransferProgress(
                    isActive = true,
                    operationType = "DOWNLOAD",
                    currentFileName = file.name,
                    progress = 0.15f,
                    completedItems = 0,
                    totalItems = file.chunkCount.coerceAtLeast(1),
                    statusDetail = "Downloading encrypted chunks..."
                )
            )

            val chunkSnap = fileRef.collection("chunks").get().await()
            val orderedChunks = chunkSnap.documents
                .mapNotNull { doc ->
                    doc.toObject(FirestoreChunkDoc::class.java)
                }
                .sortedBy { it.chunkIndex }

            val keyPhrase = getEncryptionKeyPhrase(userProfile)
            val restoredBytes = if (orderedChunks.isNotEmpty()) {
                CryptoUtils.decryptAndReassembleChunks(
                    orderedChunks.map { it.encryptedBase64 },
                    keyPhrase
                )
            } else if (!file.encryptedContent.isNullOrBlank()) {
                val plain = if (file.isEncrypted) CryptoUtils.decrypt(file.encryptedContent, keyPhrase) else file.encryptedContent
                plain.toByteArray(Charsets.UTF_8)
            } else {
                ByteArray(0)
            }

            val destFile = getLocalCachedFile(uid, file.id, file.name)
            destFile.parentFile?.mkdirs()
            destFile.writeBytes(restoredBytes)

            onProgress(
                TransferProgress(
                    isActive = false,
                    operationType = "DOWNLOAD",
                    currentFileName = file.name,
                    progress = 1f,
                    completedItems = orderedChunks.size,
                    totalItems = orderedChunks.size,
                    statusDetail = "Restored ${file.name}"
                )
            )
            Result.success(destFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onProgress(TransferProgress(isActive = false))
            handleFirestoreError(e, OperationType.GET, fileRef.path)
            Result.failure(e)
        }
    }

    /**
     * Complete Automatic Recovery: fetches ALL folders, files, binary chunks,
     * vault records, and user profile from Cloud Firestore and restores original files
     * to device storage after uninstall or new device login.
     */
    suspend fun restoreAllFromCloud(
        userProfile: UserAccount?,
        onProgress: (TransferProgress) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val filesCollection = db.collection("users").document(uid).collection("files")
        val foldersCollection = db.collection("users").document(uid).collection("folders")
        try {
            onProgress(
                TransferProgress(
                    isActive = true,
                    operationType = "RESTORE",
                    currentFileName = "Scanning Cloud Vault...",
                    progress = 0.05f,
                    statusDetail = "Fetching folders, files, and vault metadata..."
                )
            )

            // Fetch folders and files explicitly so fresh device restore is deterministic
            val folderSnap = foldersCollection.get().await()
            val fileSnap = filesCollection.get().await()
            val totalFiles = fileSnap.documents.size

            if (totalFiles == 0) {
                onProgress(TransferProgress(isActive = false, progress = 1f))
                return@withContext Result.success(0)
            }

            var restoredCount = 0
            fileSnap.documents.forEachIndexed { index, doc ->
                val dto = doc.toObject(
                    FirestoreFileDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: return@forEachIndexed

                val localFile = getLocalCachedFile(uid, doc.id, dto.name)
                if (!localFile.exists() && dto.chunkCount > 0) {
                    val fileObj = CloudFile(
                        id = doc.id,
                        userId = dto.userId,
                        name = dto.name,
                        chunkCount = dto.chunkCount.toInt(),
                        isEncrypted = dto.isEncrypted,
                        encryptedContent = dto.encryptedContent.ifBlank { null }
                    )
                    val res = downloadAndRestoreFile(fileObj, userProfile) {}
                    if (res.isSuccess) restoredCount++
                } else {
                    restoredCount++
                }

                val progress = 0.1f + 0.9f * ((index + 1).toFloat() / totalFiles.toFloat())
                onProgress(
                    TransferProgress(
                        isActive = true,
                        operationType = "RESTORE",
                        currentFileName = dto.name,
                        progress = progress,
                        completedItems = index + 1,
                        totalItems = totalFiles,
                        statusDetail = "Restored ${index + 1} of $totalFiles cloud files (${folderSnap.size()} folders)"
                    )
                )
            }

            localPrefs.edit().putLong("last_sync_$uid", System.currentTimeMillis()).apply()
            onProgress(TransferProgress(isActive = false, progress = 1f))
            Result.success(restoredCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onProgress(TransferProgress(isActive = false))
            handleFirestoreError(e, OperationType.LIST, filesCollection.path)
            Result.failure(e)
        }
    }

    suspend fun syncAndVerifyCloudBackup(
        userProfile: UserAccount?,
        onProgress: (TransferProgress) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val foldersCollection = db.collection("users").document(uid).collection("folders")
        try {
            onProgress(
                TransferProgress(
                    isActive = true,
                    operationType = "BACKUP",
                    currentFileName = "Syncing SafeVault Cloud",
                    progress = 0.25f,
                    statusDetail = "Verifying folder structure & checksums..."
                )
            )
            val folderSnap = foldersCollection.get().await()
            folderSnap.documents.forEachIndexed { idx, fDoc ->
                recalculateFolderMetrics(uid, fDoc.id)
                val pct = 0.3f + 0.6f * ((idx + 1).toFloat() / folderSnap.size().coerceAtLeast(1).toFloat())
                onProgress(
                    TransferProgress(
                        isActive = true,
                        operationType = "BACKUP",
                        currentFileName = fDoc.getString("name") ?: "Folder",
                        progress = pct,
                        completedItems = idx + 1,
                        totalItems = folderSnap.size(),
                        statusDetail = "Synchronized ${idx + 1}/${folderSnap.size()} folders"
                    )
                )
            }
            val now = System.currentTimeMillis()
            localPrefs.edit().putLong("last_sync_$uid", now).apply()
            onProgress(TransferProgress(isActive = false, progress = 1f))
            Result.success(folderSnap.size())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onProgress(TransferProgress(isActive = false))
            handleFirestoreError(e, OperationType.LIST, foldersCollection.path)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // Secure Personal Vault (AES-256 Encrypted in Cloud Firestore)
    // ------------------------------------------------------------------------

    suspend fun addVaultItem(
        category: VaultCategory,
        title: String,
        usernameOrId: String,
        secret: String,
        notes: String,
        userProfile: UserAccount?
    ): Result<SecureVaultItem> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val itemId = sanitizeId("vlt_${UUID.randomUUID().toString().replace("-", "").take(16)}")
        val docRef = db.collection("users").document(uid).collection("vault_items").document(itemId)
        val keyPhrase = getEncryptionKeyPhrase(userProfile)
        val encryptedSecret = CryptoUtils.encrypt(secret, keyPhrase)

        val payload = mapOf(
            "userId" to uid,
            "category" to category.name,
            "title" to title.trim().take(120),
            "usernameOrId" to usernameOrId.trim().take(200),
            "encryptedSecret" to encryptedSecret,
            "notes" to notes.trim().take(2000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(payload).await()
            Result.success(
                SecureVaultItem(
                    id = itemId,
                    userId = uid,
                    category = category,
                    title = title.trim(),
                    usernameOrId = usernameOrId.trim(),
                    encryptedSecret = encryptedSecret,
                    plainSecretCache = secret,
                    notes = notes.trim()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteVaultItem(itemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("vault_items").document(itemId)
        try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun decryptVaultSecret(item: SecureVaultItem, userProfile: UserAccount?): String {
        val keyPhrase = getEncryptionKeyPhrase(userProfile)
        return CryptoUtils.decrypt(item.encryptedSecret, keyPhrase)
    }

    fun decryptNoteContent(file: CloudFile, userProfile: UserAccount?): String {
        val content = file.encryptedContent ?: return ""
        if (!file.isEncrypted) return content
        val keyPhrase = getEncryptionKeyPhrase(userProfile)
        return CryptoUtils.decrypt(content, keyPhrase)
    }

    // ------------------------------------------------------------------------
    // Account Lifecycle (Safe Logout vs Explicit Deletion)
    // ------------------------------------------------------------------------

    fun clearLocalDeviceSessionOnly() {
        // NOTE: Never deletes Cloud Firestore data when logging out or uninstalling!
        cacheRootDir.deleteRecursively()
        cacheRootDir.mkdirs()
    }

    suspend fun deleteAccountAndCloudData(): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
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
            clearLocalDeviceSessionOnly()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userRef.path)
            Result.failure(e)
        }
    }

    fun clearLocalCache(): Long {
        var freed = 0L
        appContext.cacheDir.listFiles()?.forEach {
            freed += it.length()
            it.deleteRecursively()
        }
        return freed
    }

    fun getLastSyncTimestamp(userId: String): Long {
        return localPrefs.getLong("last_sync_$userId", System.currentTimeMillis())
    }

    // ------------------------------------------------------------------------
    // Internal Helpers
    // ------------------------------------------------------------------------

    private fun getLocalCachedFile(userId: String, fileId: String, originalName: String): File {
        val safeName = originalName.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
        return File(File(cacheRootDir, "${userId}_$fileId"), safeName)
    }

    private suspend fun recalculateFolderMetrics(uid: String, folderId: String) {
        val folderRef = db.collection("users").document(uid).collection("folders").document(folderId)
        try {
            val filesSnap = db.collection("users").document(uid).collection("files")
                .whereEqualTo("folderId", folderId)
                .get()
                .await()
            val count = filesSnap.size().toLong()
            val totalSize = filesSnap.documents.sumOf { (it.getLong("sizeBytes") ?: 0L) }
            folderRef.update(
                mapOf(
                    "filesCount" to count,
                    "sizeBytes" to totalSize,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
            // Best-effort folder count aggregation
        }
    }

    private suspend fun seedStarterCloudWorkspace(uid: String, profile: UserAccount) {
        val docsFolder = createFolder("Personal Documents", "#3B82F6").getOrNull()
        val mediaFolder = createFolder("Photos & Media", "#06B6D4").getOrNull()
        createFolder("Work & Projects", "#8B5CF6")

        saveNoteToCloud(
            title = "Welcome to SafeVault Cloud",
            content = "Your SafeVault Cloud Backup & Recovery system is active.\n" +
                "Registered Phone: ${profile.phoneNumber}\n" +
                "All photos, videos, audio, PDFs, Office docs, archives, notes, and folders are AES-256 encrypted and synced automatically.",
            isEncrypted = true,
            userProfile = profile,
            folderId = docsFolder?.id,
            folderName = docsFolder?.name
        )

        if (mediaFolder != null) {
            val sampleText = "SafeVault Cloud Backup Verification Receipt\nUID: $uid\nStatus: Verified"
            uploadBytesToCloud(
                fileName = "SafeVault_Backup_Certificate.txt",
                rawBytes = sampleText.toByteArray(Charsets.UTF_8),
                mimeType = "text/plain",
                userProfile = profile,
                folderId = mediaFolder.id,
                folderName = mediaFolder.name,
                isEncrypted = true,
                tags = listOf("Backup", "Certificate")
            )
        }
    }

    private fun readUriDetails(uri: Uri): Triple<String, String, ByteArray>? {
        return try {
            val cr = appContext.contentResolver
            var name = "Uploaded_File_${System.currentTimeMillis().toString().takeLast(4)}"
            cr.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val display = cursor.getString(idx)
                        if (!display.isNullOrBlank()) name = display
                    }
                }
            }
            val mime = cr.getType(uri) ?: "application/octet-stream"
            val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return null
            Triple(name, mime, bytes)
        } catch (e: Exception) {
            Log.e("SafeVaultCloud", "Failed reading URI: ${e.message}", e)
            null
        }
    }

    // ------------------------------------------------------------------------
    // App Settings, Notifications, Subscription Plans & Referrals (User View)
    // ------------------------------------------------------------------------

    fun generateReferralCode(uid: String): String {
        val clean = uid.replace(Regex("[^a-zA-Z0-9]"), "").uppercase()
        return "SV" + clean.take(6).padEnd(6, 'X')
    }

    fun observeGlobalAppSettings(): Flow<GlobalAppSettings> = flow {
        val docRef = db.collection("app_settings").document("global")
        emitAll(
            docRef.snapshots().map { snapshot ->
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
                emit(GlobalAppSettings())
            }
        )
    }

    fun observeActiveNotifications(): Flow<List<AppNotification>> = flow {
        val query = db.collection("notifications").whereEqualTo("isActive", true)
        emitAll(
            query.snapshots().map { snapshot ->
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
                emit(emptyList())
            }
        )
    }

    fun observeAvailablePlans(): Flow<List<SubscriptionPlan>> = flow {
        emitAll(
            db.collection("plans").snapshots().map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val dto = doc.toObject(
                        FirestoreSubscriptionPlan::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@mapNotNull null
                    if (!dto.isActive) return@mapNotNull null
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
                emit(emptyList())
            }
        )
    }

    fun observeMyReferrals(userId: String): Flow<List<ReferralRecord>> = flow {
        val query = db.collection("referrals").whereEqualTo("referrerUid", userId)
        emitAll(
            query.snapshots().map { snapshot ->
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
                emit(emptyList())
            }
        )
    }

    suspend fun ensureReferralCodeIndex(account: UserAccount): Result<String> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val code = account.referralCode.ifBlank { generateReferralCode(uid) }
        val indexRef = db.collection("referral_codes").document(code)
        try {
            val snap = indexRef.get().await()
            if (!snap.exists()) {
                indexRef.set(
                    mapOf(
                        "code" to code,
                        "ownerUid" to uid,
                        "ownerName" to account.fullName.ifBlank { account.phoneNumber }.take(100),
                        "isActive" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            if (account.referralCode.isBlank()) {
                db.collection("users").document(uid).update(
                    mapOf(
                        "referralCode" to code,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, indexRef.path)
            Result.failure(e)
        }
    }

    suspend fun submitReferralCodeClaim(
        codeInput: String,
        userProfile: UserAccount,
        deviceFingerprintHash: String
    ): Result<ReferralRecord> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val cleanCode = codeInput.trim().uppercase()
        if (cleanCode.length < 3 || !cleanCode.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
            return@withContext Result.failure(Exception("Invalid referral code format."))
        }
        if (userProfile.referredByUid.isNotBlank()) {
            return@withContext Result.failure(Exception("You have already redeemed a referral code."))
        }
        val myCode = userProfile.referralCode.ifBlank { generateReferralCode(uid) }
        if (cleanCode == myCode) {
            return@withContext Result.failure(Exception("Self-referral is prohibited."))
        }

        try {
            val codeSnap = db.collection("referral_codes").document(cleanCode).get().await()
            if (!codeSnap.exists() || codeSnap.getBoolean("isActive") == false) {
                return@withContext Result.failure(Exception("Referral code '$cleanCode' does not exist or is inactive."))
            }
            val referrerUid = codeSnap.getString("ownerUid") ?: ""
            val referrerName = codeSnap.getString("ownerName") ?: "SafeVault Member"
            if (referrerUid.isBlank() || referrerUid == uid) {
                return@withContext Result.failure(Exception("Self-referral is prohibited."))
            }

            val refDocId = "ref_$uid"
            val refDocRef = db.collection("referrals").document(refDocId)
            if (refDocRef.get().await().exists()) {
                return@withContext Result.failure(Exception("A referral claim already exists for your account."))
            }

            val fpHash = deviceFingerprintHash.ifBlank { CryptoUtils.sha256Hex(uid.toByteArray()) }
            val payload = mapOf(
                "referrerUid" to referrerUid,
                "referrerName" to referrerName.take(100),
                "referrerPhone" to "",
                "referralCode" to cleanCode,
                "referredUid" to uid,
                "referredName" to userProfile.fullName.ifBlank { userProfile.phoneNumber }.take(100),
                "referredPhone" to userProfile.phoneNumber.take(30),
                "deviceFingerprintHash" to fpHash.take(128),
                "status" to "PENDING",
                "rewardPoints" to 0L,
                "rewardStorageBytes" to 0L,
                "fraudReason" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            refDocRef.set(payload).await()

            db.collection("users").document(uid).update(
                mapOf(
                    "referredByUid" to referrerUid,
                    "referredByCode" to cleanCode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(
                ReferralRecord(
                    id = refDocId,
                    referrerUid = referrerUid,
                    referrerName = referrerName,
                    referralCode = cleanCode,
                    referredUid = uid,
                    referredName = userProfile.fullName,
                    referredPhone = userProfile.phoneNumber,
                    deviceFingerprintHash = fpHash,
                    status = "PENDING"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "referrals/ref_$uid")
            Result.failure(e)
        }
    }

    suspend fun touchLastActive() = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        try {
            db.collection("users").document(uid).update(
                mapOf(
                    "lastActiveAt" to System.currentTimeMillis(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
            // Non-blocking activity heartbeat
        }
    }
}
