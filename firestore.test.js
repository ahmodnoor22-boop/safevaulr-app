const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_safevault_1";
const BOB_UID = "bob_safevault_2";
const SUPER_ADMIN_UID = "super_admin_1";
const MODERATOR_UID = "moderator_1";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function baseUserPayload(uid, name, phone, now) {
  return {
    userId: uid,
    fullName: name,
    phoneNumber: phone,
    email: "",
    avatarEmoji: "🛡️",
    passwordHash: "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
    salt: "randomsalt1234",
    masterPin: "1234",
    isVerified: true,
    autoSyncEnabled: true,
    darkModeEnabled: false,
    adConfigEnabled: true,
    adTestMode: true,
    adZoneId: "adsterra_320x50",
    totalQuotaBytes: 16106127360,
    accountStatus: "ACTIVE",
    blockReason: "",
    planId: "free",
    planName: "Free Vault",
    planTier: "FREE",
    planPrice: 0,
    planExpiresAt: 0,
    referralCode: "SV_" + uid.slice(0, 6).toUpperCase(),
    referredByUid: "",
    referredByCode: "",
    referralCount: 0,
    referralBonusBytes: 0,
    referralRewardPoints: 0,
    lastActiveAt: Date.now(),
    createdAt: now,
    updatedAt: now,
  };
}

async function seedAdmin(uid, role, permissions, now) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("admins").doc(uid).set({
      uid,
      fullName: role === "SUPER_ADMIN" ? "Owner Admin" : "Moderator Admin",
      phoneNumber: "+8801700000000",
      email: "admin@safevault.app",
      role,
      permissions,
      isActive: true,
      createdAt: now,
      updatedAt: now,
    });
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write user profile or files", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("files").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("folders").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("vault_items").get());
  await assertFails(unauthDb.collection("plans").get());
  await assertFails(unauthDb.collection("audit_logs").get());
  await assertFails(unauthDb.collection("app_settings").doc("global").get());
});

test("Authenticated user: cannot list /users collection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").get());
});

test("Authenticated user: can create, read, and update their own UserProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(baseUserPayload(ALICE_UID, "Alice SafeVault", "+15550101111", now))
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      fullName: "Alice Updated",
      updatedAt: new Date(),
    })
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's profile, folders, files, or vault items", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(baseUserPayload(ALICE_UID, "Alice", "+15550101111", now));
    await db.collection("users").doc(ALICE_UID).collection("files").doc("file_1").set({
      userId: ALICE_UID,
      name: "Passport.pdf",
      fileType: "PDF",
      mimeType: "application/pdf",
      extension: "PDF",
      sizeBytes: 1024,
      checksumSha256: "abc123",
      chunkCount: 1,
      isEncrypted: true,
      isFavorite: false,
      syncStatus: "SYNCED",
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("files").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("files").doc("file_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("files").doc("file_1").delete());
});

test("Shadow Update & Ghost Field rejection: rejects unknown fields on profile and files", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...baseUserPayload(ALICE_UID, "Alice", "+15550101111", now),
      isAdmin: true, // Ghost field
    })
  );
});

test("Authenticated owner: can create folders, files, binary chunks, and vault items", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("folders").doc("folder_docs").set({
      userId: ALICE_UID,
      name: "Work Docs",
      colorHex: "#3B82F6",
      filesCount: 1,
      sizeBytes: 4096,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("files").doc("file_video").set({
      userId: ALICE_UID,
      name: "Holiday.mp4",
      fileType: "VIDEO",
      mimeType: "video/mp4",
      extension: "MP4",
      sizeBytes: 4096,
      folderId: "folder_docs",
      folderName: "Work Docs",
      checksumSha256: "deadbeef1234",
      chunkCount: 1,
      isEncrypted: true,
      isFavorite: false,
      tags: ["Travel"],
      syncStatus: "SYNCED",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("files").doc("file_video").collection("chunks").doc("chunk_0").set({
      userId: ALICE_UID,
      fileId: "file_video",
      chunkIndex: 0,
      encryptedBase64: "U2FmZVZhdWx0RW5jcnlwdGVk",
      createdAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("vault_items").doc("vault_1").set({
      userId: ALICE_UID,
      category: "BANK_CARD",
      title: "Visa Gold",
      usernameOrId: "4111****1111",
      encryptedSecret: "enc_secret_payload",
      notes: "Primary card",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Privilege escalation prevention: regular user cannot become admin, self-grant premium, edit app settings, or access audit logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  // Cannot create self as admin
  await assertFails(
    aliceDb.collection("admins").doc(ALICE_UID).set({
      uid: ALICE_UID,
      fullName: "Fake Admin",
      role: "SUPER_ADMIN",
      permissions: ["MANAGE_USERS", "MANAGE_PLANS"],
      isActive: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cannot create profile directly as PREMIUM
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...baseUserPayload(ALICE_UID, "Alice", "+15550101111", now),
      planTier: "PREMIUM",
    })
  );

  // Valid FREE profile creation succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(baseUserPayload(ALICE_UID, "Alice", "+15550101111", now))
  );

  // Cannot self-upgrade to PREMIUM or increase referralRewardPoints
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      planTier: "PREMIUM",
      updatedAt: new Date(),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      referralRewardPoints: 5000,
      updatedAt: new Date(),
    })
  );

  // Cannot create plans, edit app settings, send notifications, or read audit logs
  await assertFails(
    aliceDb.collection("plans").doc("plan_free").set({
      name: "Hacked Plan",
      tier: "PREMIUM",
      price: 0,
      currency: "USD",
      durationDays: 365,
      storageQuotaGb: 9999,
      maxFileSizeMb: 500,
      features: ["Unlimited"],
      adsRemoved: true,
      isActive: true,
      createdByUid: ALICE_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("app_settings").doc("global").set({
      appName: "HackedApp",
      maintenanceMode: true,
      referralEnabled: true,
      referralRewardPoints: 100,
      referralRewardBonusMb: 512,
      updatedByUid: ALICE_UID,
      updatedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("notifications").doc("notif_1").set({
      title: "Fake Promo",
      message: "Click here",
      priority: "PROMO",
      targetAudience: "ALL",
      isActive: true,
      createdByUid: ALICE_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(aliceDb.collection("audit_logs").get());
});

test("Email spoofing test: unverified owner email rejected, verified owner email accepted", async () => {
  const now = new Date();
  const spoofedDb = testEnv.authenticatedContext("spoof_uid", {
    email: "ahmodnoor22@gmail.com",
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofedDb.collection("app_settings").doc("global").set({
      appName: "SafeVault",
      maintenanceMode: false,
      referralEnabled: true,
      referralRewardPoints: 100,
      referralRewardBonusMb: 500,
      updatedByUid: "spoof_uid",
      updatedAt: now,
    })
  );

  const verifiedOwnerDb = testEnv.authenticatedContext("owner_uid", {
    email: "ahmodnoor22@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    verifiedOwnerDb.collection("app_settings").doc("global").set({
      appName: "SafeVault Pro",
      maintenanceMode: false,
      referralEnabled: true,
      referralRewardPoints: 100,
      referralRewardBonusMb: 500,
      updatedByUid: "owner_uid",
      updatedAt: now,
    })
  );
});

test("Multi-role RBAC & Admin Security: SuperAdmin vs Moderator permissions + Immutable Audit Logs", async () => {
  const now = new Date();
  await seedAdmin(SUPER_ADMIN_UID, "SUPER_ADMIN", [
    "MANAGE_USERS", "MANAGE_PLANS", "MANAGE_REFERRALS", "MANAGE_SETTINGS", "MANAGE_ADMINS", "VIEW_AUDIT_LOGS"
  ], now);
  await seedAdmin(MODERATOR_UID, "MODERATOR", ["MANAGE_USERS", "VIEW_AUDIT_LOGS"], now);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set(baseUserPayload(ALICE_UID, "Alice", "+15550101111", now));
  });

  const superDb = testEnv.authenticatedContext(SUPER_ADMIN_UID).firestore();
  const modDb = testEnv.authenticatedContext(MODERATOR_UID).firestore();

  // SuperAdmin can create plans, manage settings, send notifications, list/update users
  await assertSucceeds(
    superDb.collection("plans").doc("plan_pro").set({
      name: "Pro Cloud 100GB",
      tier: "PREMIUM",
      price: 9.99,
      currency: "USD",
      durationDays: 30,
      storageQuotaGb: 100,
      maxFileSizeMb: 50,
      features: ["100GB Cloud", "No Ads", "Priority Restore"],
      adsRemoved: true,
      isActive: true,
      createdByUid: SUPER_ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    superDb.collection("users").doc(ALICE_UID).update({
      planId: "plan_pro",
      planName: "Pro Cloud 100GB",
      planTier: "PREMIUM",
      planPrice: 9.99,
      planExpiresAt: Date.now() + 30 * 86400000,
      totalQuotaBytes: 100 * 1024 * 1024 * 1024,
      updatedAt: new Date(),
    })
  );

  // Moderator CAN list and block Alice and read audit logs
  await assertSucceeds(modDb.collection("users").get());
  await assertSucceeds(
    modDb.collection("users").doc(ALICE_UID).update({
      accountStatus: "BLOCKED",
      blockReason: "Policy violation check",
      updatedAt: new Date(),
    })
  );

  // Moderator CANNOT create/delete plans, edit app settings, or grant admin roles
  await assertFails(
    modDb.collection("plans").doc("plan_mod").set({
      name: "Unauthorized Plan",
      tier: "FREE",
      price: 0,
      currency: "USD",
      durationDays: 30,
      storageQuotaGb: 5,
      maxFileSizeMb: 10,
      features: ["Basic"],
      adsRemoved: false,
      isActive: true,
      createdByUid: MODERATOR_UID,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(modDb.collection("plans").doc("plan_pro").delete());
  await assertFails(
    modDb.collection("admins").doc(BOB_UID).set({
      uid: BOB_UID,
      fullName: "Illegal Promote",
      role: "SUPER_ADMIN",
      permissions: ["MANAGE_ADMINS"],
      isActive: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Audit logs can be created by admins and are strictly immutable (cannot update/delete even by SuperAdmin)
  await assertSucceeds(
    superDb.collection("audit_logs").doc("log_1").set({
      actorUid: SUPER_ADMIN_UID,
      actorName: "Owner Admin",
      actorRole: "SUPER_ADMIN",
      action: "CREATE_PLAN",
      targetType: "PLAN",
      targetId: "plan_pro",
      details: "Created Pro Cloud 100GB",
      createdAt: now,
    })
  );
  await assertSucceeds(modDb.collection("audit_logs").get());
  await assertFails(superDb.collection("audit_logs").doc("log_1").update({ details: "tampered" }));
  await assertFails(superDb.collection("audit_logs").doc("log_1").delete());
});

test("Referral anti-fraud & admin approval rules", async () => {
  const now = new Date();
  await seedAdmin(SUPER_ADMIN_UID, "SUPER_ADMIN", ["MANAGE_USERS", "MANAGE_REFERRALS"], now);

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const superDb = testEnv.authenticatedContext(SUPER_ADMIN_UID).firestore();

  // Alice creates her referral code index
  await assertSucceeds(
    aliceDb.collection("referral_codes").doc("SVALICE1").set({
      code: "SVALICE1",
      ownerUid: ALICE_UID,
      ownerName: "Alice",
      isActive: true,
      createdAt: now,
    })
  );

  // Self-referral must fail
  await assertFails(
    aliceDb.collection("referrals").doc("ref_" + ALICE_UID).set({
      referrerUid: ALICE_UID,
      referralCode: "SVALICE1",
      referredUid: ALICE_UID,
      deviceFingerprintHash: "fp_device_1",
      status: "PENDING",
      rewardPoints: 0,
      rewardStorageBytes: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Fake referral code must fail
  await assertFails(
    bobDb.collection("referrals").doc("ref_" + BOB_UID).set({
      referrerUid: ALICE_UID,
      referralCode: "FAKECODE99",
      referredUid: BOB_UID,
      deviceFingerprintHash: "fp_device_2",
      status: "PENDING",
      rewardPoints: 0,
      rewardStorageBytes: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Client trying to pre-approve or award self points must fail
  await assertFails(
    bobDb.collection("referrals").doc("ref_" + BOB_UID).set({
      referrerUid: ALICE_UID,
      referralCode: "SVALICE1",
      referredUid: BOB_UID,
      deviceFingerprintHash: "fp_device_2",
      status: "APPROVED",
      rewardPoints: 9999,
      rewardStorageBytes: 1000000,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Checking existence of own referral slot before claim must succeed
  await assertSucceeds(bobDb.collection("referrals").doc("ref_" + BOB_UID).get());

  // Valid PENDING claim by Bob succeeds
  await assertSucceeds(
    bobDb.collection("referrals").doc("ref_" + BOB_UID).set({
      referrerUid: ALICE_UID,
      referrerName: "Alice",
      referralCode: "SVALICE1",
      referredUid: BOB_UID,
      referredName: "Bob",
      deviceFingerprintHash: "fp_device_2",
      status: "PENDING",
      rewardPoints: 0,
      rewardStorageBytes: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot approve his own referral; SuperAdmin can
  await assertFails(
    bobDb.collection("referrals").doc("ref_" + BOB_UID).update({
      status: "APPROVED",
      rewardPoints: 100,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    superDb.collection("referrals").doc("ref_" + BOB_UID).update({
      status: "APPROVED",
      rewardPoints: 150,
      rewardStorageBytes: 536870912,
      fraudReason: "",
      updatedAt: new Date(),
    })
  );
});
