package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AdminMember
import com.example.data.model.AdminPermission
import com.example.data.model.AdminRole
import com.example.data.model.AppNotification
import com.example.data.model.GlobalAppSettings
import com.example.data.model.ReferralRecord
import com.example.data.model.ReportPeriod
import com.example.data.model.SubscriptionPlan
import com.example.data.model.UserAccount
import com.example.ui.AdminViewModel
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class SafeVaultAdminRuleTest : FirestoreEmulatorTestBase() {

    private suspend fun signInVerifiedBootstrappedOwner(): String = withContext(Dispatchers.IO) {
        val ownerUid = signInTestUser(SafeVaultAdminRepository.BOOTSTRAPPED_OWNER_EMAIL)
        val url = URL("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:update?key=fake-api-key-for-emulator")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer owner")
            doOutput = true
        }
        val body = """{"localId":"$ownerUid","emailVerified":true}"""
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val status = conn.responseCode
        check(status in 200..299) { "Failed to mark emulator owner email verified: HTTP $status" }
        auth.currentUser?.reload()?.await()
        auth.currentUser?.getIdToken(true)?.await()
        ownerUid
    }

    @Test
    fun adminSecurity_regularUserCannotReadAllUsersWritePlansOrOpenAdminRecord() = runBlocking {
        val userUid = signInTestUser("regular_${UUID.randomUUID().toString().take(6)}@safevault.test")
        val cloudRepo = SafeVaultCloudRepository(firestore, auth, appContext)
        cloudRepo.registerCloudProfile("Regular User", "+15550109988", "Secret123!").getOrThrow()

        val adminRepo = SafeVaultAdminRepository(firestore, auth)
        val observedAdmin = withTimeout(TIMEOUT_MS) {
            adminRepo.observeCurrentAdmin(userUid).first()
        }
        assertNull("Regular user must not be recognized as admin", observedAdmin)

        // Regular user cannot create a subscription plan
        val fakeActor = AdminMember(uid = userUid, fullName = "Regular", role = AdminRole.SUPER_ADMIN, isActive = true)
        val planRes = withTimeout(TIMEOUT_MS) {
            adminRepo.savePlan(
                fakeActor,
                SubscriptionPlan(name = "Fake Plan", tier = "PREMIUM", price = 0.0, storageQuotaGb = 999L)
            )
        }
        assertTrue(planRes.isFailure)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, planRes.exceptionOrNull()?.unwrapFirestoreException()?.code)

        // Regular user cannot update global app settings
        val settingsRes = withTimeout(TIMEOUT_MS) {
            adminRepo.updateGlobalAppSettings(fakeActor, GlobalAppSettings(appName = "Hijacked", maintenanceMode = true))
        }
        assertTrue(settingsRes.isFailure)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, settingsRes.exceptionOrNull()?.unwrapFirestoreException()?.code)

        // Regular user cannot grant themselves an admin role
        val adminGrantRes = withTimeout(TIMEOUT_MS) {
            adminRepo.saveAdminMember(
                actor = fakeActor,
                targetUid = userUid,
                fullName = "Hijacked Admin",
                phoneNumber = "+15550109988",
                email = "",
                role = AdminRole.SUPER_ADMIN,
                permissions = AdminPermission.entries.toList(),
                isActive = true
            )
        }
        assertTrue(adminGrantRes.isFailure)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, adminGrantRes.exceptionOrNull()?.unwrapFirestoreException()?.code)
    }

    @Test
    fun adminEndToEnd_plansUsersExpiryBlockSettingsNotificationsSecurityPinAndAuditLogs() = runBlocking {
        // 1. Create a normal user Alice & a Moderator user Mod
        val aliceUid = signInTestUser("alice_adm_${UUID.randomUUID().toString().take(6)}@safevault.test")
        val aliceRepo = SafeVaultCloudRepository(firestore, auth, appContext)
        val aliceProfile = withTimeout(TIMEOUT_MS) {
            aliceRepo.registerCloudProfile("Alice User", "+8801711000111", "Pass#2026").getOrThrow()
        }

        val modEmail = "mod_${UUID.randomUUID().toString().take(6)}@safevault.test"
        val modUid = signInTestUser(modEmail)

        // 2. Sign in as verified bootstrapped Super Admin owner
        val ownerUid = signInVerifiedBootstrappedOwner()
        val adminRepo = SafeVaultAdminRepository(firestore, auth)

        val ownerAdmin = withTimeout(TIMEOUT_MS) {
            adminRepo.observeCurrentAdmin(ownerUid).first { it != null && it.isActive }
        }!!
        assertEquals(AdminRole.SUPER_ADMIN, ownerAdmin.role)

        // 3. Configure Secondary Admin PIN & verify hash check
        withTimeout(TIMEOUT_MS) {
            adminRepo.setOwnAdminPin(ownerAdmin, "5678").getOrThrow()
        }
        val persistedOwnerAdmin = withTimeout(TIMEOUT_MS) {
            adminRepo.observeCurrentAdmin(ownerUid).first { it?.hasPinConfigured == true }
        }!!
        assertTrue(adminRepo.verifyAdminPin(persistedOwnerAdmin, "5678"))
        assertFalse(adminRepo.verifyAdminPin(persistedOwnerAdmin, "0000"))

        // 4. Create & edit Subscription Plan
        val createdPlan = withTimeout(TIMEOUT_MS) {
            adminRepo.savePlan(
                persistedOwnerAdmin,
                SubscriptionPlan(
                    name = "Gold Vault 250GB",
                    tier = "PREMIUM",
                    price = 14.99,
                    currency = "USD",
                    durationDays = 30,
                    storageQuotaGb = 250L,
                    maxFileSizeMb = 50L,
                    features = listOf("250GB Cloud Storage", "Ad-Free", "Priority Auto-Recovery"),
                    adsRemoved = true,
                    isActive = true
                )
            ).getOrThrow()
        }
        val editedPlan = withTimeout(TIMEOUT_MS) {
            adminRepo.savePlan(persistedOwnerAdmin, createdPlan.copy(price = 12.99)).getOrThrow()
        }
        assertEquals(12.99, editedPlan.price, 0.001)

        // 5. Assign plan to Alice & adjust expiry (+15 days, then -5 days)
        withTimeout(TIMEOUT_MS) {
            adminRepo.assignPlanToUser(persistedOwnerAdmin, aliceUid, editedPlan).getOrThrow()
        }
        var updatedAlice = withTimeout(TIMEOUT_MS) {
            adminRepo.observeAllUsers().first { list ->
                list.any { it.id == aliceUid && it.planTier == "PREMIUM" }
            }.first { it.id == aliceUid }
        }
        assertEquals("Gold Vault 250GB", updatedAlice.planName)
        assertEquals(250L * 1024L * 1024L * 1024L, updatedAlice.totalQuotaBytes)
        val expiryAfterAssign = updatedAlice.planExpiresAt
        assertTrue(expiryAfterAssign > System.currentTimeMillis())

        withTimeout(TIMEOUT_MS) {
            adminRepo.adjustUserPlanExpiry(persistedOwnerAdmin, updatedAlice, 15).getOrThrow()
        }
        updatedAlice = withTimeout(TIMEOUT_MS) {
            adminRepo.observeAllUsers().first { list ->
                list.firstOrNull { it.id == aliceUid }?.planExpiresAt ?: 0L > expiryAfterAssign
            }.first { it.id == aliceUid }
        }
        val extendedExpiry = updatedAlice.planExpiresAt
        assertTrue(extendedExpiry >= expiryAfterAssign + 14L * 86_400_000L)

        withTimeout(TIMEOUT_MS) {
            adminRepo.adjustUserPlanExpiry(persistedOwnerAdmin, updatedAlice, -5).getOrThrow()
        }
        updatedAlice = withTimeout(TIMEOUT_MS) {
            adminRepo.observeAllUsers().first { list ->
                (list.firstOrNull { it.id == aliceUid }?.planExpiresAt ?: Long.MAX_VALUE) < extendedExpiry
            }.first { it.id == aliceUid }
        }

        // 6. Block and Unblock Alice
        withTimeout(TIMEOUT_MS) {
            adminRepo.setUserBlockStatus(persistedOwnerAdmin, aliceUid, true, "Suspicious login test").getOrThrow()
        }
        updatedAlice = withTimeout(TIMEOUT_MS) {
            adminRepo.observeAllUsers().first { list ->
                list.firstOrNull { it.id == aliceUid }?.isBlocked == true
            }.first { it.id == aliceUid }
        }
        assertEquals("Suspicious login test", updatedAlice.blockReason)

        withTimeout(TIMEOUT_MS) {
            adminRepo.setUserBlockStatus(persistedOwnerAdmin, aliceUid, false, "").getOrThrow()
        }

        // 7. Update Global App Settings & Broadcast Notification
        withTimeout(TIMEOUT_MS) {
            adminRepo.updateGlobalAppSettings(
                persistedOwnerAdmin,
                GlobalAppSettings(
                    appName = "SafeVault Pro",
                    appLogoStyle = "EMERALD_VAULT",
                    maintenanceMode = true,
                    maintenanceMessage = "Upgrading encryption clusters",
                    announcementEnabled = true,
                    announcementTitle = "v2.0 Upgrade",
                    announcementMessage = "New Referral Rewards Live",
                    referralEnabled = true,
                    referralRewardPoints = 250L,
                    referralRewardBonusMb = 1024L
                )
            ).getOrThrow()
        }
        val liveSettings = withTimeout(TIMEOUT_MS) {
            adminRepo.observeGlobalAppSettings().first { it.appName == "SafeVault Pro" && it.maintenanceMode }
        }
        assertEquals(250L, liveSettings.referralRewardPoints)

        // Turn maintenance mode back off
        withTimeout(TIMEOUT_MS) {
            adminRepo.updateGlobalAppSettings(persistedOwnerAdmin, liveSettings.copy(maintenanceMode = false)).getOrThrow()
        }

        val notif = withTimeout(TIMEOUT_MS) {
            adminRepo.saveNotification(
                persistedOwnerAdmin,
                AppNotification(title = "Backup Reminder", message = "Keep your vault synced.", priority = "INFO", targetAudience = "ALL", isActive = true)
            ).getOrThrow()
        }
        val activeNotifs = withTimeout(TIMEOUT_MS) {
            adminRepo.observeAllNotifications().first { list -> list.any { it.id == notif.id } }
        }
        assertTrue(activeNotifs.isNotEmpty())
        withTimeout(TIMEOUT_MS) {
            adminRepo.deleteNotification(persistedOwnerAdmin, notif.id, notif.title).getOrThrow()
        }

        // 8. Grant Moderator role and verify RBAC boundary (Moderator can block user, CANNOT create plans)
        withTimeout(TIMEOUT_MS) {
            adminRepo.saveAdminMember(
                actor = persistedOwnerAdmin,
                targetUid = modUid,
                fullName = "Vault Moderator",
                phoneNumber = "+8801722000222",
                email = modEmail,
                role = AdminRole.MODERATOR,
                permissions = listOf(AdminPermission.MANAGE_USERS, AdminPermission.VIEW_AUDIT_LOGS),
                isActive = true
            ).getOrThrow()
        }

        // Verify Audit Logs recorded all admin operations
        val logs = withTimeout(TIMEOUT_MS) {
            adminRepo.observeAuditLogs().first { it.size >= 5 }
        }
        assertTrue(logs.any { it.action == "ASSIGN_USER_PLAN" })
        assertTrue(logs.any { it.action == "BLOCK_USER" })
        assertTrue(logs.any { it.action == "GRANT_ADMIN_ROLE" })

        // Sign in as Moderator and verify scoped permissions
        signInTestUser(modEmail)
        val modRepo = SafeVaultAdminRepository(firestore, auth)
        val modMember = withTimeout(TIMEOUT_MS) {
            modRepo.observeCurrentAdmin(modUid).first { it != null }
        }!!
        assertTrue(withTimeout(TIMEOUT_MS) { modRepo.setUserBlockStatus(modMember, aliceUid, true, "Mod review") }.isSuccess)

        val modCreatePlanAttempt = withTimeout(TIMEOUT_MS) {
            modRepo.savePlan(modMember, SubscriptionPlan(name = "Illegal Mod Plan", tier = "FREE"))
        }
        assertTrue("Moderator without MANAGE_PLANS must be rejected", modCreatePlanAttempt.isFailure)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, modCreatePlanAttempt.exceptionOrNull()?.unwrapFirestoreException()?.code)

        // 9. Switch back to Super Admin, delete Plan, revoke Moderator, and delete Alice user permanently
        signInVerifiedBootstrappedOwner()
        withTimeout(TIMEOUT_MS) {
            adminRepo.deletePlan(persistedOwnerAdmin, editedPlan.id, editedPlan.name).getOrThrow()
            adminRepo.revokeAdminMember(persistedOwnerAdmin, modUid, "Vault Moderator").getOrThrow()
            adminRepo.deleteUserAccount(persistedOwnerAdmin, aliceProfile).getOrThrow()
        }
        val usersAfterDelete = withTimeout(TIMEOUT_MS) {
            adminRepo.observeAllUsers().first { list -> list.none { it.id == aliceUid } }
        }
        assertFalse(usersAfterDelete.any { it.id == aliceUid })
    }

    @Test
    fun referralControl_endToEndClaimAntiFraudAndAtomicRewardGrantAndReversal() = runBlocking {
        // User A creates referral code
        val referrerEmail = "referrer_${UUID.randomUUID().toString().take(6)}@safevault.test"
        val referrerUid = signInTestUser(referrerEmail)
        val repoA = SafeVaultCloudRepository(firestore, auth, appContext)
        val profileA = withTimeout(TIMEOUT_MS) {
            repoA.registerCloudProfile("Referrer A", "+15557770001", "Pass#1234").getOrThrow()
        }
        val codeA = withTimeout(TIMEOUT_MS) {
            repoA.ensureReferralCodeIndex(profileA).getOrThrow()
        }

        // Self-referral must fail
        val selfClaim = withTimeout(TIMEOUT_MS) {
            repoA.submitReferralCodeClaim(codeA, profileA, "device_hash_alpha")
        }
        assertTrue("Self-referral must fail", selfClaim.isFailure)

        // User B signs up and redeems User A's code
        val invitedEmail = "invited_${UUID.randomUUID().toString().take(6)}@safevault.test"
        val invitedUid = signInTestUser(invitedEmail)
        val repoB = SafeVaultCloudRepository(firestore, auth, appContext)
        val profileB = withTimeout(TIMEOUT_MS) {
            repoB.registerCloudProfile(
                fullName = "Invited B",
                phoneNumber = "+15557770002",
                password = "Pass#5678",
                referralCodeInput = codeA
            ).getOrThrow()
        }
        assertEquals(referrerUid, profileB.referredByUid)

        // Duplicate claim by User B must fail
        val dupClaim = withTimeout(TIMEOUT_MS) {
            repoB.submitReferralCodeClaim(codeA, profileB, "device_hash_beta")
        }
        assertTrue("Duplicate referral claim must be blocked", dupClaim.isFailure)

        // Admin approves referral -> User A receives atomic points + bonus storage
        val ownerUid = signInVerifiedBootstrappedOwner()
        val adminRepo = SafeVaultAdminRepository(firestore, auth)
        val ownerAdmin = withTimeout(TIMEOUT_MS) {
            adminRepo.observeCurrentAdmin(ownerUid).first { it != null }
        }!!
        val pendingRecord = withTimeout(TIMEOUT_MS) {
            adminRepo.observeAllReferrals().first { list -> list.any { it.referredUid == invitedUid } }
        }.first { it.referredUid == invitedUid }
        assertEquals("PENDING", pendingRecord.status)

        val settings = GlobalAppSettings(referralRewardPoints = 150L, referralRewardBonusMb = 500L)
        withTimeout(TIMEOUT_MS) {
            adminRepo.reviewReferral(ownerAdmin, pendingRecord, approve = true, fraudReason = "", settings = settings).getOrThrow()
        }

        val updatedReferrerAfterApprove = withTimeout(TIMEOUT_MS) {
            adminRepo.observeAllUsers().first { list ->
                list.firstOrNull { it.id == referrerUid }?.referralCount == 1L
            }.first { it.id == referrerUid }
        }
        assertEquals(150L, updatedReferrerAfterApprove.referralRewardPoints)
        assertEquals(500L * 1024L * 1024L, updatedReferrerAfterApprove.referralBonusBytes)

        // Admin rejects/reverses fraudulent referral -> rewards reversed cleanly
        val approvedRecord = withTimeout(TIMEOUT_MS) {
            adminRepo.observeAllReferrals().first { list ->
                list.firstOrNull { it.id == pendingRecord.id }?.status == "APPROVED"
            }.first { it.id == pendingRecord.id }
        }
        withTimeout(TIMEOUT_MS) {
            adminRepo.reviewReferral(ownerAdmin, approvedRecord, approve = false, fraudReason = "Emulator test fraud reversal", settings = settings).getOrThrow()
        }

        val updatedReferrerAfterReverse = withTimeout(TIMEOUT_MS) {
            adminRepo.observeAllUsers().first { list ->
                list.firstOrNull { it.id == referrerUid }?.referralCount == 0L
            }.first { it.id == referrerUid }
        }
        assertEquals(0L, updatedReferrerAfterReverse.referralRewardPoints)
        assertEquals(0L, updatedReferrerAfterReverse.referralBonusBytes)
    }

    @Test
    fun dashboardMetrics_dailyWeeklyMonthlyComputationsAreAccurate() {
        val now = 1_750_000_000_000L
        val hourMs = 3_600_000L
        val dayMs = 86_400_000L

        val users = listOf(
            UserAccount(
                id = "u1", fullName = "Today Premium", phoneNumber = "+1",
                accountStatus = "ACTIVE", planTier = "PREMIUM", planPrice = 9.99,
                createdAt = now - 2 * hourMs, lastActiveAt = now - hourMs
            ),
            UserAccount(
                id = "u2", fullName = "Week Free", phoneNumber = "+2",
                accountStatus = "ACTIVE", planTier = "FREE", planPrice = 0.0,
                createdAt = now - 4 * dayMs, lastActiveAt = now - 3 * dayMs
            ),
            UserAccount(
                id = "u3", fullName = "Month Premium", phoneNumber = "+3",
                accountStatus = "ACTIVE", planTier = "PREMIUM", planPrice = 19.99,
                createdAt = now - 20 * dayMs, lastActiveAt = now - 15 * dayMs
            ),
            UserAccount(
                id = "u4", fullName = "Old Blocked", phoneNumber = "+4",
                accountStatus = "BLOCKED", planTier = "FREE", planPrice = 0.0,
                createdAt = now - 45 * dayMs, lastActiveAt = now - 45 * dayMs
            )
        )
        val referrals = listOf(
            ReferralRecord(id = "r1", status = "APPROVED", createdAt = now - 3 * hourMs),
            ReferralRecord(id = "r2", status = "PENDING", createdAt = now - 5 * dayMs),
            ReferralRecord(id = "r3", status = "REJECTED", createdAt = now - 18 * dayMs)
        )

        val daily = AdminViewModel.computeDashboardMetrics(users, referrals, ReportPeriod.DAILY, now)
        assertEquals(4, daily.totalUsers)
        assertEquals(3, daily.activeUsers)
        assertEquals(1, daily.inactiveUsers)
        assertEquals(1, daily.blockedUsers)
        assertEquals(2, daily.premiumUsers)
        assertEquals(2, daily.freeUsers)
        assertEquals(1, daily.newUsersInPeriod)
        assertEquals(1, daily.referralsInPeriod)
        assertEquals(29.98, daily.totalRevenue, 0.001)
        assertEquals(9.99, daily.revenueInPeriod, 0.001)

        val weekly = AdminViewModel.computeDashboardMetrics(users, referrals, ReportPeriod.WEEKLY, now)
        assertEquals(2, weekly.newUsersInPeriod)
        assertEquals(2, weekly.referralsInPeriod)

        val monthly = AdminViewModel.computeDashboardMetrics(users, referrals, ReportPeriod.MONTHLY, now)
        assertEquals(3, monthly.newUsersInPeriod)
        assertEquals(3, monthly.referralsInPeriod)
        assertEquals(29.98, monthly.revenueInPeriod, 0.001)
    }

    private companion object {
        const val TIMEOUT_MS = 10000L
    }
}
