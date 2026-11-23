const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProjectPayload(uid, offsetMs = 10000) {
  const ts = new Date(Date.now() - offsetMs);
  return {
    userId: uid,
    appName: "Neon Blaster",
    packageName: "com.forge.neonblaster",
    versionName: "1.0.0",
    versionCode: 1,
    appType: "GAME",
    language: "C# (Unity)",
    prompt: "Create a neon space shooter game",
    summary: "2D arcade space shooter with waves",
    accentHex: "#00E5FF",
    playableHtml: "<html><body><canvas id='c'></canvas></body></html>",
    files: [
      {
        path: "Assets/Scripts/GameController.cs",
        language: "C#",
        content: "public class GameController {}",
      },
    ],
    createdAt: ts,
    updatedAt: ts,
  };
}

function validChatPayload(uid, sessionId = "global_architect") {
  return {
    userId: uid,
    sessionId,
    role: "MODEL",
    text: "Voici une proposition d'architecture Compose.",
    suggestions: [
      {
        title: "Écran principal",
        filePath: "app/src/main/java/com/app/MainActivity.kt",
        language: "Kotlin",
        code: "class MainActivity {}",
      },
    ],
    followUps: ["Ajouter une base Room", "Ajouter le mode sombre"],
    createdAt: new Date(Date.now() - 1000),
  };
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

test("Unauthenticated user cannot list, get, or create projects or chat messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1").set(validProjectPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("chat_messages").where("sessionId", "==", "global_architect").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("chat_messages").doc("msg_1").set(validChatPayload(ALICE_UID))
  );
});

test("Authenticated owner can create, list, update code, record APK build, and delete project", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projectsRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects");
  const docRef = projectsRef.doc("proj_1");

  await assertSucceeds(docRef.set(validProjectPayload(ALICE_UID)));
  await assertSucceeds(projectsRef.get());
  await assertSucceeds(docRef.get());

  await assertSucceeds(
    docRef.update({
      appName: "Neon Blaster DX",
      versionCode: 2,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    docRef.update({
      lastBuildApkName: "neon_blaster_dx-v1.0.0-signed.apk",
      lastBuildSizeBytes: 18432,
      lastBuildSha256: "abcdef0123456789",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(docRef.delete());
});

test("Authenticated owner can create, query, and delete chat messages; updates rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const chatRef = aliceDb.collection("users").doc(ALICE_UID).collection("chat_messages");
  const msgRef = chatRef.doc("msg_1");

  await assertSucceeds(msgRef.set(validChatPayload(ALICE_UID)));
  await assertSucceeds(chatRef.where("sessionId", "==", "global_architect").get());

  // Append-only messages cannot be updated
  await assertFails(msgRef.update({ text: "Tampered" }));

  // Invalid role rejected
  await assertFails(
    chatRef.doc("msg_bad_role").set({
      ...validChatPayload(ALICE_UID),
      role: "SYSTEM",
    })
  );

  await assertSucceeds(msgRef.delete());
});

test("Cross-user access is strictly denied for projects and chat messages", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).collection("projects").doc("bob_proj").set(validProjectPayload(BOB_UID));
    await ctx.firestore().collection("users").doc(BOB_UID).collection("chat_messages").doc("bob_msg").set(validChatPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDoc = aliceDb.collection("users").doc(BOB_UID).collection("projects").doc("bob_proj");

  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("projects").get());
  await assertFails(bobDoc.get());
  await assertFails(bobDoc.update({ appName: "Hijacked", updatedAt: new Date(Date.now() - 500) }));
  await assertFails(bobDoc.delete());

  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("chat_messages").where("sessionId", "==", "global_architect").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("chat_messages").doc("bob_msg").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("chat_messages").doc("bob_msg").delete());
});

test("Rejects invalid schema payloads, ghost fields, immutable mutation, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projectsRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects");

  await assertSucceeds(projectsRef.doc("proj_valid").set(validProjectPayload(ALICE_UID)));

  await assertFails(
    projectsRef.doc("proj_bad_id").set({
      ...validProjectPayload(ALICE_UID),
      id: "proj_bad_id",
    })
  );

  await assertFails(
    projectsRef.doc("proj_bad_type").set({
      ...validProjectPayload(ALICE_UID),
      appType: "WEBSITE",
    })
  );

  await assertFails(
    projectsRef.doc("proj_empty_files").set({
      ...validProjectPayload(ALICE_UID),
      files: [],
    })
  );

  await assertFails(
    projectsRef.doc("proj_future_ts").set({
      ...validProjectPayload(ALICE_UID),
      createdAt: new Date(Date.now() + 60000),
    })
  );

  await assertFails(
    projectsRef.doc("proj_valid").update({
      createdAt: new Date(Date.now() - 2000),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    projectsRef.doc("proj_valid").update({
      appName: "Mixed",
      lastBuildApkName: "hack.apk",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
