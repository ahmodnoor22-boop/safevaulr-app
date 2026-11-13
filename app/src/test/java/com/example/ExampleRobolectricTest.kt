package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.FileType
import com.example.data.model.inferFileTypeFromNameAndMime
import com.example.data.security.CryptoUtils
import com.example.ui.screens.PhoneAuthLoginScreen
import com.example.ui.screens.PhoneNumberFormatter
import com.example.ui.theme.SafeVaultTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SafeVault", appName)
    }

    @Test
    fun `test AES-256 string encryption and decryption`() {
        val plainSecret = "SuperSecretRecoveryKey#2026"
        val key = "uid_123:4321:salt_abc"

        val encrypted = CryptoUtils.encrypt(plainSecret, key)
        assertNotEquals(plainSecret, encrypted)

        val decrypted = CryptoUtils.decrypt(encrypted, key)
        assertEquals(plainSecret, decrypted)
    }

    @Test
    fun `test chunked AES-256 binary encryption and reassembly`() {
        val original = ByteArray(900_000) { (it % 251).toByte() }
        val key = "cloud_vault_master_key"

        val chunks = CryptoUtils.chunkAndEncryptFile(original, key, rawChunkSize = 350_000)
        assertEquals(3, chunks.size)

        val reassembled = CryptoUtils.decryptAndReassembleChunks(chunks, key)
        assertArrayEquals(original, reassembled)
        assertEquals(CryptoUtils.sha256Hex(original), CryptoUtils.sha256Hex(reassembled))
    }

    @Test
    fun `test file type inference for all supported cloud backup formats`() {
        assertEquals(FileType.IMAGE to "JPG", inferFileTypeFromNameAndMime("photo.jpg", "image/jpeg"))
        assertEquals(FileType.VIDEO to "MP4", inferFileTypeFromNameAndMime("trip.mp4", "video/mp4"))
        assertEquals(FileType.AUDIO to "MP3", inferFileTypeFromNameAndMime("song.mp3", "audio/mpeg"))
        assertEquals(FileType.PDF to "PDF", inferFileTypeFromNameAndMime("contract.pdf", "application/pdf"))
        assertEquals(
            FileType.DOCUMENT to "DOCX",
            inferFileTypeFromNameAndMime("report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        )
        assertEquals(
            FileType.DOCUMENT to "XLSX",
            inferFileTypeFromNameAndMime("budget.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        )
        assertEquals(
            FileType.DOCUMENT to "PPTX",
            inferFileTypeFromNameAndMime("deck.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
        )
        assertEquals(FileType.ARCHIVE to "ZIP", inferFileTypeFromNameAndMime("backup.zip", "application/zip"))
        assertEquals(FileType.ARCHIVE to "RAR", inferFileTypeFromNameAndMime("archive.rar", "application/x-rar-compressed"))
        assertEquals(FileType.NOTE to "TXT", inferFileTypeFromNameAndMime("keys.txt", "text/plain"))
    }

    @Test
    fun `test E164 phone number normalization and validation`() {
        assertEquals("+8801712345678", PhoneNumberFormatter.normalizeE164("+880 1712-345678"))
        assertEquals("+15551234567", PhoneNumberFormatter.normalizeE164("001 (555) 123-4567"))
        assertEquals("+447911123456", PhoneNumberFormatter.normalizeE164("447911123456"))

        assertTrue(PhoneNumberFormatter.isValidPhone("+8801712345678"))
        assertTrue(PhoneNumberFormatter.isValidPhone("+1 (555) 123-4567"))
        assertTrue(PhoneNumberFormatter.isValidPhone("008801712345678"))

        assertFalse(PhoneNumberFormatter.isValidPhone("+123"))
        assertFalse(PhoneNumberFormatter.isValidPhone("+0123456789"))
        assertFalse(PhoneNumberFormatter.isValidPhone("abc12345678"))
        assertFalse(PhoneNumberFormatter.isValidPhone(""))
    }

    @Test
    fun `phone auth login screen renders mobile login without Google or Gmail redirect and validates inputs`() {
        composeTestRule.setContent {
            SafeVaultTheme {
                PhoneAuthLoginScreen(
                    onPreparePendingAuth = {},
                    onPhoneAuthCompleted = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Verify mobile number + password inputs and SMS OTP button are present
        composeTestRule.onNodeWithTag("mobile_login_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("send_mobile_otp_button").performScrollTo().assertIsDisplayed()

        // Ensure zero Google/Gmail Sign-In affordances exist
        assertEquals(0, composeTestRule.onAllNodesWithText("Google", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Gmail", substring = true, ignoreCase = true).fetchSemanticsNodes().size)

        // Invalid phone number triggers validation error before SMS dispatch
        composeTestRule.onNodeWithTag("mobile_login_input").performScrollTo().performTextReplacement("12345")
        composeTestRule.onNodeWithTag("send_mobile_otp_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Enter a valid mobile number", substring = true).performScrollTo().assertIsDisplayed()

        // Valid phone + short password triggers password validation error
        composeTestRule.onNodeWithTag("mobile_login_input").performScrollTo().performTextReplacement("+8801712345678")
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("123")
        composeTestRule.onNodeWithTag("send_mobile_otp_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("at least 6 characters", substring = true).performScrollTo().assertIsDisplayed()

        // Switching to Register tab reveals Full Name and optional Referral Code inputs
        composeTestRule.onNodeWithTag("auth_tab_register").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("register_name_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("register_referral_input").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `blocked and maintenance security screens display admin messages and sign out`() {
        var blockedSignOutClicks = 0
        composeTestRule.setContent {
            SafeVaultTheme {
                AccountBlockedScreen(
                    reason = "Policy abuse detected",
                    supportEmail = "support@safevault.app",
                    supportPhone = "+1 800 555 0199",
                    onSignOut = { blockedSignOutClicks++ }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("account_blocked_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Account Suspended").assertIsDisplayed()
        composeTestRule.onNodeWithText("Policy abuse detected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign Out").performClick()
        assertEquals(1, blockedSignOutClicks)
    }

    @Test
    fun `maintenance mode screen displays app name and message`() {
        var switchAccountClicks = 0
        composeTestRule.setContent {
            SafeVaultTheme {
                MaintenanceModeScreen(
                    appName = "SafeVault Pro",
                    message = "Scheduled database maintenance",
                    onSignOut = { switchAccountClicks++ }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("maintenance_mode_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("SafeVault Pro Maintenance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scheduled database maintenance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Switch Account / Sign Out").performClick()
        assertEquals(1, switchAccountClicks)
    }

    @Test
    fun `admin panel screen blocks unauthorized non-admin user`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = com.google.firebase.FirebaseApp.getApps(context).firstOrNull()
            ?: com.google.firebase.FirebaseApp.initializeApp(
                context,
                com.google.firebase.FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.safevault.storage")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key")
                    .build()
            )
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance(
            app,
            context.getString(R.string.firestore_database_id)
        )
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance(app)
        val adminVm = com.example.ui.AdminViewModel(
            adminRepository = com.example.data.repository.SafeVaultAdminRepository(db, auth),
            currentUserId = "unauthorized_uid"
        )
        var exitCalls = 0
        composeTestRule.setContent {
            SafeVaultTheme {
                com.example.ui.screens.admin.AdminPanelScreen(
                    adminViewModel = adminVm,
                    onExitAdminPanel = { exitCalls++ }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Unauthorized Admin Access").assertIsDisplayed()
        composeTestRule.onNodeWithText("Return to SafeVault").performClick()
        assertEquals(1, exitCalls)
    }
}
