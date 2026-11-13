package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class FileType {
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    DOCUMENT,
    ARCHIVE,
    NOTE,
    OTHER
}

enum class SyncStatus {
    SYNCED,
    PENDING,
    RESTORED
}

enum class VaultCategory(val displayName: String) {
    PASSWORD("Password"),
    BANK_CARD("Bank & Card"),
    IDENTITY_DOC("ID & Passport"),
    SECRET_NOTE("Secure Note"),
    CRYPTO_KEY("Recovery Key")
}

data class UserAccount(
    val id: String = "",
    val phoneNumber: String = "",
    val fullName: String = "",
    val email: String = "",
    val avatarEmoji: String = "🛡️",
    val passwordHash: String = "",
    val salt: String = "",
    val masterPin: String = "1234",
    val isVerified: Boolean = true,
    val autoSyncEnabled: Boolean = true,
    val darkModeEnabled: Boolean = false,
    val totalQuotaBytes: Long = 15L * 1024 * 1024 * 1024,
    val accountStatus: String = "ACTIVE", // ACTIVE, BLOCKED
    val blockReason: String = "",
    val planId: String = "free",
    val planName: String = "Free Vault",
    val planTier: String = "FREE", // FREE, PREMIUM
    val planPrice: Double = 0.0,
    val planExpiresAt: Long = 0L,
    val referralCode: String = "",
    val referredByUid: String = "",
    val referredByCode: String = "",
    val referralCount: Long = 0L,
    val referralBonusBytes: Long = 0L,
    val referralRewardPoints: Long = 0L,
    val lastActiveAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val effectiveQuotaBytes: Long
        get() = (totalQuotaBytes + referralBonusBytes).coerceAtLeast(1024L * 1024L)

    val isBlocked: Boolean
        get() = accountStatus.equals("BLOCKED", ignoreCase = true)

    val isPremium: Boolean
        get() = planTier.equals("PREMIUM", ignoreCase = true) &&
            (planExpiresAt == 0L || planExpiresAt > System.currentTimeMillis())
}

data class CloudFolder(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val colorHex: String = "#3B82F6",
    val iconName: String = "folder",
    val parentFolderId: String = "",
    val filesCount: Int = 0,
    val sizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val serverCreatedAt: Timestamp? = null
) {
    val formattedSize: String
        get() = formatStorageSize(sizeBytes)
}

data class CloudFile(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val type: FileType = FileType.OTHER,
    val sizeBytes: Long = 0L,
    val mimeType: String = "application/octet-stream",
    val customExtension: String = "",
    val localUri: String? = null,
    val remoteStorageUrl: String? = null,
    val folderId: String? = null,
    val folderName: String? = null,
    val checksumSha256: String = "",
    val chunkCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val serverCreatedAt: Timestamp? = null,
    val isEncrypted: Boolean = true,
    val encryptedContent: String? = null,
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.SYNCED
) {
    val formattedSize: String
        get() = formatStorageSize(sizeBytes)

    val extension: String
        get() = if (customExtension.isNotBlank()) customExtension.uppercase() else when (type) {
            FileType.PDF -> "PDF"
            FileType.IMAGE -> "PNG"
            FileType.VIDEO -> "MP4"
            FileType.AUDIO -> "MP3"
            FileType.DOCUMENT -> "DOCX"
            FileType.NOTE -> "TXT"
            FileType.ARCHIVE -> "ZIP"
            FileType.OTHER -> "FILE"
        }
}

data class SecureVaultItem(
    val id: String = "",
    val userId: String = "",
    val category: VaultCategory = VaultCategory.PASSWORD,
    val title: String = "",
    val usernameOrId: String = "",
    val encryptedSecret: String = "",
    val plainSecretCache: String? = null,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val serverCreatedAt: Timestamp? = null
)

data class StorageStats(
    val usedBytes: Long = 0L,
    val totalBytes: Long = 15L * 1024 * 1024 * 1024, // 15 GB default quota
    val totalFiles: Int = 0,
    val totalFolders: Int = 0,
    val imageBytes: Long = 0L,
    val videoBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val docBytes: Long = 0L,
    val archiveBytes: Long = 0L,
    val noteBytes: Long = 0L,
    val otherBytes: Long = 0L
) {
    val usedPercentage: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val freePercentage: Float
        get() = (1f - usedPercentage).coerceIn(0f, 1f)

    val usedGbString: String
        get() = formatStorageSize(usedBytes)

    val freeGbString: String
        get() = formatStorageSize((totalBytes - usedBytes).coerceAtLeast(0L))
}

data class TransferProgress(
    val isActive: Boolean = false,
    val operationType: String = "", // "UPLOAD", "DOWNLOAD", "BACKUP", "RESTORE"
    val currentFileName: String = "",
    val progress: Float = 0f,
    val completedItems: Int = 0,
    val totalItems: Int = 0,
    val statusDetail: String = ""
)

data class AdsterraAdConfig(
    val isEnabled: Boolean = true,
    val isTestMode: Boolean = true,
    val zoneId: String = "adsterra_banner_sample_468x60",
    val customBannerScript: String = "<!-- Adsterra Banner Script Placement -->\n<script type=\"text/javascript\">\n  atOptions = {\n    'key' : 'sample_adsterra_zone_key',\n    'format' : 'iframe',\n    'height' : 50,\n    'width' : 320,\n    'params' : {}\n  };\n</script>",
    val showOnDashboard: Boolean = true,
    val showOnStoragePage: Boolean = true,
    val showOnBottomArea: Boolean = true
)

// Firestore DTOs with @DocumentId, @ServerTimestamp, and no-arg default constructors
@IgnoreExtraProperties
data class FirestoreUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val avatarEmoji: String = "🛡️",
    val passwordHash: String = "",
    val salt: String = "",
    val masterPin: String = "1234",
    val isVerified: Boolean = true,
    val autoSyncEnabled: Boolean = true,
    val darkModeEnabled: Boolean = false,
    val adConfigEnabled: Boolean = true,
    val adTestMode: Boolean = true,
    val adZoneId: String = "adsterra_banner_320x50",
    val totalQuotaBytes: Long = 15L * 1024 * 1024 * 1024,
    val accountStatus: String = "ACTIVE",
    val blockReason: String = "",
    val planId: String = "free",
    val planName: String = "Free Vault",
    val planTier: String = "FREE",
    val planPrice: Double = 0.0,
    val planExpiresAt: Long = 0L,
    val referralCode: String = "",
    val referredByUid: String = "",
    val referredByCode: String = "",
    val referralCount: Long = 0L,
    val referralBonusBytes: Long = 0L,
    val referralRewardPoints: Long = 0L,
    val lastActiveAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreFolderDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val colorHex: String = "#3B82F6",
    val iconName: String = "folder",
    val parentFolderId: String = "",
    val filesCount: Long = 0L,
    val sizeBytes: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreFileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val fileType: String = FileType.OTHER.name,
    val mimeType: String = "application/octet-stream",
    val extension: String = "FILE",
    val sizeBytes: Long = 0L,
    val folderId: String = "",
    val folderName: String = "",
    val checksumSha256: String = "",
    val chunkCount: Long = 0L,
    val isEncrypted: Boolean = true,
    val encryptedContent: String = "",
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val syncStatus: String = SyncStatus.SYNCED.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreChunkDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fileId: String = "",
    val chunkIndex: Long = 0L,
    val encryptedBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreVaultItemDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = VaultCategory.PASSWORD.name,
    val title: String = "",
    val usernameOrId: String = "",
    val encryptedSecret: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun formatStorageSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}

fun inferFileTypeFromNameAndMime(fileName: String, mimeType: String): Pair<FileType, String> {
    val ext = fileName.substringAfterLast('.', "").uppercase()
    val lowerMime = mimeType.lowercase()
    val type = when {
        lowerMime.startsWith("image/") || ext in listOf("JPG", "JPEG", "PNG", "GIF", "WEBP", "HEIC", "BMP", "SVG") -> FileType.IMAGE
        lowerMime.startsWith("video/") || ext in listOf("MP4", "MOV", "AVI", "MKV", "WEBM", "3GP") -> FileType.VIDEO
        lowerMime.startsWith("audio/") || ext in listOf("MP3", "WAV", "M4A", "AAC", "FLAC", "OGG") -> FileType.AUDIO
        lowerMime == "application/pdf" || ext == "PDF" -> FileType.PDF
        ext in listOf("DOC", "DOCX", "XLS", "XLSX", "PPT", "PPTX", "ODT", "ODS", "ODP", "CSV", "RTF") ||
                lowerMime.contains("word") || lowerMime.contains("excel") || lowerMime.contains("spreadsheet") ||
                lowerMime.contains("powerpoint") || lowerMime.contains("presentation") || lowerMime.contains("document") -> FileType.DOCUMENT
        ext in listOf("ZIP", "RAR", "7Z", "TAR", "GZ", "BZ2") ||
                lowerMime.contains("zip") || lowerMime.contains("rar") || lowerMime.contains("compressed") -> FileType.ARCHIVE
        lowerMime.startsWith("text/") || ext in listOf("TXT", "MD", "JSON", "XML") -> FileType.NOTE
        else -> FileType.OTHER
    }
    val resolvedExt = ext.ifBlank {
        when (type) {
            FileType.IMAGE -> "JPG"
            FileType.VIDEO -> "MP4"
            FileType.AUDIO -> "MP3"
            FileType.PDF -> "PDF"
            FileType.DOCUMENT -> "DOCX"
            FileType.ARCHIVE -> "ZIP"
            FileType.NOTE -> "TXT"
            FileType.OTHER -> "FILE"
        }
    }
    return type to resolvedExt
}
