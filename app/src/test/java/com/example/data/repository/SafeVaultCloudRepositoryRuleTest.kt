package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.FileType
import com.example.data.model.VaultCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class SafeVaultCloudRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun fullEndToEnd_register_uploadAllFormats_backup_uninstall_reinstall_login_restoreAll() = runBlocking {
        val uniqueEmail = "alice_${UUID.randomUUID().toString().take(8)}@safevault.test"
        val uid = signInTestUser(uniqueEmail)
        val repoDeviceA = SafeVaultCloudRepository(firestore, auth, appContext)

        // 1. Register user profile with verified mobile number and password
        val phone = "+15559876543"
        val pass = "MasterPass#2026"
        val profile = withTimeout(TIMEOUT_MS) {
            repoDeviceA.registerCloudProfile(
                fullName = "Alice Morgan",
                phoneNumber = phone,
                password = pass,
                masterPin = "4321"
            ).getOrThrow()
        }
        assertEquals(uid, profile.id)
        assertEquals(phone, profile.phoneNumber)

        // 2. Create custom folder structure
        val mediaFolder = withTimeout(TIMEOUT_MS) {
            repoDeviceA.createFolder("Camera & Media", "#06B6D4").getOrThrow()
        }
        val docsFolder = withTimeout(TIMEOUT_MS) {
            repoDeviceA.createFolder("Office & Archives", "#3B82F6").getOrThrow()
        }

        // 3. Upload ALL required data types with distinct byte payloads
        val photoBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3, 4, 5)
        val videoBytes = "FAKE_MP4_VIDEO_STREAM_DATA_123456".toByteArray()
        val audioBytes = "ID3_AUDIO_MUSIC_TRACK_PAYLOAD_789".toByteArray()
        val pdfBytes = "%PDF-1.7 SafeVault Cloud PDF Document".toByteArray()
        val wordBytes = "PK_DOCX_WORD_DOCUMENT_CONTENT".toByteArray()
        val excelBytes = "PK_XLSX_EXCEL_SPREADSHEET_DATA".toByteArray()
        val pptBytes = "PK_PPTX_POWERPOINT_DECK_SLIDES".toByteArray()
        val zipBytes = "PK_ZIP_ARCHIVE_BUNDLE_999".toByteArray()
        val rarBytes = "Rar!_ARCHIVE_BACKUP_888".toByteArray()

        val uploads = listOf(
            Triple("Vacation_Photo.jpg", photoBytes, "image/jpeg" to mediaFolder),
            Triple("Family_Trip.mp4", videoBytes, "video/mp4" to mediaFolder),
            Triple("Voice_Memo.mp3", audioBytes, "audio/mpeg" to mediaFolder),
            Triple("Tax_Return_2026.pdf", pdfBytes, "application/pdf" to docsFolder),
            Triple("Contract.docx", wordBytes, "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to docsFolder),
            Triple("Budget_Sheet.xlsx", excelBytes, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to docsFolder),
            Triple("Pitch_Deck.pptx", pptBytes, "application/vnd.openxmlformats-officedocument.presentationml.presentation" to docsFolder),
            Triple("Project_Bundle.zip", zipBytes, "application/zip" to docsFolder),
            Triple("Legacy_Backup.rar", rarBytes, "application/x-rar-compressed" to docsFolder)
        )

        for ((name, bytes, meta) in uploads) {
            val (mime, folder) = meta
            val res = withTimeout(TIMEOUT_MS) {
                repoDeviceA.uploadBytesToCloud(
                    fileName = name,
                    rawBytes = bytes,
                    mimeType = mime,
                    userProfile = profile,
                    folderId = folder.id,
                    folderName = folder.name,
                    isEncrypted = true
                )
            }
            assertTrue("Upload failed for $name: ${res.exceptionOrNull()?.message}", res.isSuccess)
        }

        // Duplicate prevention check
        val duplicateRes = withTimeout(TIMEOUT_MS) {
            repoDeviceA.uploadBytesToCloud(
                fileName = "Tax_Return_2026.pdf",
                rawBytes = pdfBytes,
                mimeType = "application/pdf",
                userProfile = profile,
                folderId = docsFolder.id,
                folderName = docsFolder.name,
                isEncrypted = true
            )
        }
        assertTrue("Duplicate upload must be prevented", duplicateRes.isFailure)

        // Save encrypted Note and Personal Vault record
        withTimeout(TIMEOUT_MS) {
            repoDeviceA.saveNoteToCloud(
                title = "Recovery Seed Note",
                content = "secret-recovery-words-alpha-beta",
                isEncrypted = true,
                userProfile = profile,
                folderId = docsFolder.id,
                folderName = docsFolder.name
            ).getOrThrow()

            repoDeviceA.addVaultItem(
                category = VaultCategory.BANK_CARD,
                title = "Corporate Card",
                usernameOrId = "**** 4242",
                secret = "CVV-991-PIN-4455",
                notes = "Main account",
                userProfile = profile
            ).getOrThrow()
        }

        // Verify sync
        withTimeout(TIMEOUT_MS) {
            repoDeviceA.syncAndVerifyCloudBackup(profile).getOrThrow()
        }

        // 4. SIMULATE UNINSTALL / WIPE LOCAL DEVICE CACHE / LOGOUT
        repoDeviceA.clearLocalDeviceSessionOnly()
        auth.signOut()

        // 5. SIMULATE REINSTALL ON NEW DEVICE & LOGIN
        val restoredUid = signInTestUser(uniqueEmail)
        assertEquals(uid, restoredUid)
        val repoDeviceB = SafeVaultCloudRepository(firestore, auth, appContext)

        // Verify mobile number + password against Cloud Firestore profile
        val verifiedProfile = withTimeout(TIMEOUT_MS) {
            repoDeviceB.verifyCloudVaultCredentials(phone, pass).getOrThrow()
        }
        assertEquals("Alice Morgan", verifiedProfile.fullName)

        // Trigger full automatic cloud restore
        val restoredCount = withTimeout(TIMEOUT_MS * 2) {
            repoDeviceB.restoreAllFromCloud(verifiedProfile).getOrThrow()
        }
        assertTrue("Expected >= 10 restored files, got $restoredCount", restoredCount >= 10)

        // Observe restored folders, files, and vault items from Firestore
        val restoredFolders = withTimeout(TIMEOUT_MS) {
            repoDeviceB.observeFolders(restoredUid).first { it.any { f -> f.name == "Office & Archives" } }
        }
        assertTrue(restoredFolders.any { it.name == "Camera & Media" })
        assertTrue(restoredFolders.any { it.name == "Office & Archives" })

        val restoredFiles = withTimeout(TIMEOUT_MS) {
            repoDeviceB.observeFiles(restoredUid).first { list -> list.any { it.name == "Pitch_Deck.pptx" } }
        }

        // Verify every single file type, original name, folder association, AND byte-for-byte local restoration!
        val expectedBytesMap = mapOf(
            "Vacation_Photo.jpg" to (FileType.IMAGE to photoBytes),
            "Family_Trip.mp4" to (FileType.VIDEO to videoBytes),
            "Voice_Memo.mp3" to (FileType.AUDIO to audioBytes),
            "Tax_Return_2026.pdf" to (FileType.PDF to pdfBytes),
            "Contract.docx" to (FileType.DOCUMENT to wordBytes),
            "Budget_Sheet.xlsx" to (FileType.DOCUMENT to excelBytes),
            "Pitch_Deck.pptx" to (FileType.DOCUMENT to pptBytes),
            "Project_Bundle.zip" to (FileType.ARCHIVE to zipBytes),
            "Legacy_Backup.rar" to (FileType.ARCHIVE to rarBytes)
        )

        for ((name, pair) in expectedBytesMap) {
            val (expectedType, expectedBytes) = pair
            val fileObj = restoredFiles.find { it.name == name }
            assertNotNull("Missing restored file: $name", fileObj)
            assertEquals("Wrong FileType for $name", expectedType, fileObj!!.type)
            assertNotNull("Restored local file path missing for $name", fileObj.localUri)
            val diskFile = File(fileObj.localUri!!)
            assertTrue("Restored file missing on disk: $name", diskFile.exists())
            assertArrayEquals("Byte mismatch after decrypting $name", expectedBytes, diskFile.readBytes())
        }

        // Verify encrypted note & vault secret decrypted cleanly on Device B
        val restoredNote = restoredFiles.find { it.name == "Recovery Seed Note" }
        assertNotNull(restoredNote)
        assertEquals(
            "secret-recovery-words-alpha-beta",
            repoDeviceB.decryptNoteContent(restoredNote!!, verifiedProfile)
        )

        val restoredVault = withTimeout(TIMEOUT_MS) {
            repoDeviceB.observeVaultItems(restoredUid).first { it.any { v -> v.title == "Corporate Card" } }
        }
        val cardItem = restoredVault.first { it.title == "Corporate Card" }
        assertEquals("CVV-991-PIN-4455", repoDeviceB.decryptVaultSecret(cardItem, verifiedProfile))
    }

    @Test
    fun securityBoundary_unauthenticatedCaller_rejectedWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = SafeVaultCloudRepository(firestore, auth, appContext)
        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeFiles("some_user_123").first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        assertNotNull(caught)
        val firestoreEx = caught!!.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caught", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
    }

    @Test
    fun securityBoundary_crossUserAccess_rejectedWithPermissionDenied() = runBlocking {
        val aliceEmail = "alice_iso_${UUID.randomUUID().toString().take(6)}@safevault.test"
        val bobEmail = "bob_iso_${UUID.randomUUID().toString().take(6)}@safevault.test"

        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = SafeVaultCloudRepository(firestore, auth, appContext)
        val aliceProfile = withTimeout(TIMEOUT_MS) {
            aliceRepo.registerCloudProfile("Alice", "+15551110000", "Pass123!").getOrThrow()
        }
        withTimeout(TIMEOUT_MS) {
            aliceRepo.createFolder("Alice Secret Folder", "#3B82F6").getOrThrow()
        }

        // Switch to Bob
        signInTestUser(bobEmail)
        val bobRepo = SafeVaultCloudRepository(firestore, auth, appContext)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeFolders(aliceUid).first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        assertNotNull(caught)
        val firestoreEx = caught!!.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caught", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
    }

    @Test
    fun mobileOnlyLogin_registerAndRestoreOnNewDevice_withoutPassword() = runBlocking {
        val uniqueEmail = "mobile_${UUID.randomUUID().toString().take(8)}@safevault.test"
        val uid = signInTestUser(uniqueEmail)
        val repoA = SafeVaultCloudRepository(firestore, auth, appContext)

        val mobile = "+8801712345678"
        val profile = withTimeout(TIMEOUT_MS) {
            repoA.registerCloudProfile(phoneNumber = mobile).getOrThrow()
        }
        assertEquals(mobile, profile.phoneNumber)

        val pdfData = "%PDF-1.4 Mobile-Only Backup File".toByteArray()
        withTimeout(TIMEOUT_MS) {
            repoA.uploadBytesToCloud(
                fileName = "NID_Card.pdf",
                rawBytes = pdfData,
                mimeType = "application/pdf",
                userProfile = profile,
                folderId = null,
                folderName = null,
                isEncrypted = true
            ).getOrThrow()
        }

        // Simulate uninstall & wipe
        repoA.clearLocalDeviceSessionOnly()
        auth.signOut()

        // Re-authenticate session on new device using ONLY mobile number
        signInTestUser(uniqueEmail)
        val repoB = SafeVaultCloudRepository(firestore, auth, appContext)
        val verified = withTimeout(TIMEOUT_MS) {
            repoB.verifyCloudPhoneOnly(mobile).getOrThrow()
        }
        assertEquals(uid, verified.id)

        val restoredCount = withTimeout(TIMEOUT_MS) {
            repoB.restoreAllFromCloud(verified).getOrThrow()
        }
        assertTrue(restoredCount >= 1)

        val restoredFiles = withTimeout(TIMEOUT_MS) {
            repoB.observeFiles(uid).first { list -> list.any { it.name == "NID_Card.pdf" } }
        }
        val nidFile = restoredFiles.first { it.name == "NID_Card.pdf" }
        assertArrayEquals(pdfData, File(nidFile.localUri!!).readBytes())
    }

    @Test
    fun mobileLogin_wrongPasswordRejected_andOtpPasswordResetRestoresAccess() = runBlocking {
        val uniqueEmail = "pwdreset_${UUID.randomUUID().toString().take(8)}@safevault.test"
        val uid = signInTestUser(uniqueEmail)
        val repo = SafeVaultCloudRepository(firestore, auth, appContext)

        val phone = "+8801811223344"
        val initialPass = "Secret123!"
        val profile = withTimeout(TIMEOUT_MS) {
            repo.registerCloudProfile(
                fullName = "Rafiq Hasan",
                phoneNumber = phone,
                password = initialPass
            ).getOrThrow()
        }

        val secretNote = "Confidential recovery backup note".toByteArray()
        withTimeout(TIMEOUT_MS) {
            repo.uploadBytesToCloud(
                fileName = "Recovery_Plan.txt",
                rawBytes = secretNote,
                mimeType = "text/plain",
                userProfile = profile,
                folderId = null,
                folderName = null,
                isEncrypted = true
            ).getOrThrow()
        }

        // Wrong password must be rejected
        val wrongPassRes = withTimeout(TIMEOUT_MS) {
            repo.verifyCloudVaultCredentials(phone, "WrongPass999")
        }
        assertTrue("Expected failure on wrong password", wrongPassRes.isFailure)

        // Wrong phone number must be rejected
        val wrongPhoneRes = withTimeout(TIMEOUT_MS) {
            repo.verifyCloudVaultCredentials("+8801999999999", initialPass)
        }
        assertTrue("Expected failure on mismatched phone", wrongPhoneRes.isFailure)

        // Reset password after verified OTP
        val newPass = "NewVaultPass@2026"
        val resetRes = withTimeout(TIMEOUT_MS) {
            repo.resetCloudPasswordWithOtp(phone, newPass)
        }
        assertTrue("Password reset should succeed", resetRes.isSuccess)

        // Old password must now fail
        val oldPassAttempt = withTimeout(TIMEOUT_MS) {
            repo.verifyCloudVaultCredentials(phone, initialPass)
        }
        assertTrue("Old password should no longer verify", oldPassAttempt.isFailure)

        // New password with formatting spaces/dashes succeeds and restores data
        val formattedPhone = "00880 1811-223344"
        val loginRes = withTimeout(TIMEOUT_MS) {
            repo.verifyCloudVaultCredentials(formattedPhone, newPass).getOrThrow()
        }
        assertEquals(uid, loginRes.id)

        val restoredCount = withTimeout(TIMEOUT_MS) {
            repo.restoreAllFromCloud(loginRes).getOrThrow()
        }
        assertTrue(restoredCount >= 1)
    }

    @Test
    fun viewModel_pendingPhoneAuthFlow_registerWrongLoginRightLoginAndForgotPassword() = runBlocking {
        val uniqueEmail = "vm_phone_${UUID.randomUUID().toString().take(8)}@safevault.test"
        val uid = signInTestUser(uniqueEmail)
        val repo = SafeVaultCloudRepository(firestore, auth, appContext)
        val phone = "+15553334444"
        val pass = "VaultKey#77"

        // 1. Register via PendingPhoneAuthRequest
        var consumed = false
        val vmRegister = com.example.ui.SafeVaultViewModel(
            repository = repo,
            currentUserId = uid,
            initialPendingAuth = com.example.ui.PendingPhoneAuthRequest(
                mode = com.example.ui.PhoneAuthMode.REGISTER,
                fullName = "Nadia Islam",
                phoneNumber = phone,
                password = pass
            ),
            onConsumePendingAuth = { consumed = true }
        )
        val unlockedRegister = withTimeout(TIMEOUT_MS) {
            vmRegister.vaultGateState.first { it == com.example.ui.VaultGateState.UNLOCKED }
        }
        assertEquals(com.example.ui.VaultGateState.UNLOCKED, unlockedRegister)
        assertTrue(consumed)

        // 2. Wrong password via PendingPhoneAuthRequest LOGIN keeps vault locked
        val vmWrongLogin = com.example.ui.SafeVaultViewModel(
            repository = repo,
            currentUserId = uid,
            initialPendingAuth = com.example.ui.PendingPhoneAuthRequest(
                mode = com.example.ui.PhoneAuthMode.LOGIN,
                fullName = "",
                phoneNumber = phone,
                password = "BadPassword!"
            )
        )
        val lockedState = withTimeout(TIMEOUT_MS) {
            vmWrongLogin.vaultGateState.first { it == com.example.ui.VaultGateState.UNLOCK_EXISTING_VAULT }
        }
        assertEquals(com.example.ui.VaultGateState.UNLOCK_EXISTING_VAULT, lockedState)

        // 3. Correct password via PendingPhoneAuthRequest LOGIN unlocks vault
        val vmRightLogin = com.example.ui.SafeVaultViewModel(
            repository = repo,
            currentUserId = uid,
            initialPendingAuth = com.example.ui.PendingPhoneAuthRequest(
                mode = com.example.ui.PhoneAuthMode.LOGIN,
                fullName = "",
                phoneNumber = phone,
                password = pass
            )
        )
        val unlockedLogin = withTimeout(TIMEOUT_MS) {
            vmRightLogin.vaultGateState.first { it == com.example.ui.VaultGateState.UNLOCKED }
        }
        assertEquals(com.example.ui.VaultGateState.UNLOCKED, unlockedLogin)

        // 4. FORGOT_PASSWORD via verified Phone Auth resets password and unlocks vault
        val vmForgot = com.example.ui.SafeVaultViewModel(
            repository = repo,
            currentUserId = uid,
            initialPendingAuth = com.example.ui.PendingPhoneAuthRequest(
                mode = com.example.ui.PhoneAuthMode.FORGOT_PASSWORD,
                fullName = "",
                phoneNumber = phone,
                password = "UpdatedPass#88"
            )
        )
        val unlockedForgot = withTimeout(TIMEOUT_MS) {
            vmForgot.vaultGateState.first { it == com.example.ui.VaultGateState.UNLOCKED }
        }
        assertEquals(com.example.ui.VaultGateState.UNLOCKED, unlockedForgot)
        assertTrue(withTimeout(TIMEOUT_MS) { repo.verifyCloudVaultCredentials(phone, "UpdatedPass#88") }.isSuccess)
    }

    private companion object {
        const val TIMEOUT_MS = 10000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
