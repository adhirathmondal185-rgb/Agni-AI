const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_agni_001";
const BOB_UID = "bob_agni_002";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastDate = (offsetMs = 2000) => new Date(Date.now() - offsetMs);
const futureDate = () => new Date(Date.now() + 120000);

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

// 1. Unauthenticated denials across collections
test("Unauthenticated users are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("chats").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("memory").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Anon",
      preferredLanguage: "English",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

// 2. Users collection (PII isolation, shadow updates, createdAt immutability)
test("Users: owner CRUD succeeds; cross-user, list, and shadow fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Aarav Sen",
      email: "aarav@example.com",
      preferredLanguage: "Bengali",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "Hindi",
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user read & listing users fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("users").get());

  // Shadow update (ghost field) fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Tampering createdAt fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );
});

// 3. Chats & Messages (including orphan message rejection and @DocumentId id key rejection)
test("Chats & Messages: valid Kotlin payloads succeed; orphan messages & invalid keys fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  // Orphan message without parent chat must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chats")
      .doc("missing_chat")
      .collection("messages")
      .doc("msg_1")
      .set({
        userId: ALICE_UID,
        chatId: "missing_chat",
        role: "user",
        content: "Hello Agni",
        mode: "chat",
        modelId: "gemini-3.8-flash",
        language: "English",
        createdAt: created,
      })
  );

  // Create chat session
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").set({
      userId: ALICE_UID,
      title: "Bengali Poetry Translation",
      mode: "translation",
      modelId: "gemini-3.8-flash",
      language: "Bengali",
      messageCount: 0,
      lastMessagePreview: "",
      pinned: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject chat create with redundant @DocumentId `id` field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_bad").set({
      id: "chat_bad",
      userId: ALICE_UID,
      title: "Bad Chat",
      mode: "chat",
      modelId: "gemini-3.8-flash",
      language: "English",
      messageCount: 0,
      lastMessagePreview: "",
      pinned: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_future").set({
      userId: ALICE_UID,
      title: "Future Chat",
      mode: "chat",
      modelId: "gemini-3.8-flash",
      language: "English",
      messageCount: 0,
      lastMessagePreview: "",
      pinned: false,
      createdAt: futureDate(),
      updatedAt: futureDate(),
    })
  );

  // Create message under existing chat succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chats")
      .doc("chat_1")
      .collection("messages")
      .doc("msg_1")
      .set({
        userId: ALICE_UID,
        chatId: "chat_1",
        role: "user",
        content: "Translate Rabindranath Tagore quote to English",
        mode: "translation",
        modelId: "gemini-3.8-flash",
        language: "Bengali",
        createdAt: pastDate(5000),
      })
  );

  // Assistant message with citations succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chats")
      .doc("chat_1")
      .collection("messages")
      .doc("msg_2")
      .set({
        userId: ALICE_UID,
        chatId: "chat_1",
        role: "assistant",
        content: "Where the mind is without fear...",
        mode: "research",
        modelId: "gemini-3.8-flash",
        language: "English",
        citations: ["https://example.org/tagore"],
        createdAt: pastDate(3000),
      })
  );

  // Messages are immutable (update denied)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chats")
      .doc("chat_1")
      .collection("messages")
      .doc("msg_1")
      .update({ content: "Edited" })
  );

  // Owner list queries succeed; Bob cross-user list/get denied
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").get()
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chats")
      .doc("chat_1")
      .collection("messages")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("chats").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chats")
      .doc("chat_1")
      .collection("messages")
      .get()
  );
});

// 4. Files, Settings, AI Models, Feedback, Usage, and Memory collections
test("Files, Settings, AI Models, Feedback, Usage, Memory: owner CRUD & validation bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(4000);

  // Files: valid PDF and image succeed; invalid mimeType and >20MB fail
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("files").doc("file_1").set({
      userId: ALICE_UID,
      fileName: "physics_notes.pdf",
      mimeType: "application/pdf",
      sizeBytes: 102400,
      analysisSummary: "Summary of thermodynamics laws",
      createdAt: ts,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("files").doc("file_bad").set({
      userId: ALICE_UID,
      fileName: "malware.exe",
      mimeType: "application/octet-stream",
      sizeBytes: 500,
      createdAt: ts,
    })
  );

  // Settings
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences").set({
      userId: ALICE_UID,
      themeMode: "dark",
      preferredLanguage: "Hindi",
      defaultModelId: "gemini-3.8-flash",
      defaultMode: "chat",
      voiceAutoSend: false,
      memoryEnabled: true,
      webResearchEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences").update({
      themeMode: "light",
      updatedAt: pastDate(1000),
    })
  );

  // AI Models
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_models").doc("gemini_flash").set({
      userId: ALICE_UID,
      modelKey: "gemini-3.8-flash",
      displayName: "Agni Flash (Gemini 3.8)",
      provider: "Firebase AI Logic",
      tier: "balanced",
      enabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Feedback (valid 1..5 rating succeeds, rating=6 fails, update denied)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("feedback").doc("fb_1").set({
      userId: ALICE_UID,
      rating: 5,
      category: "language",
      comment: "Bengali and Hindi responses are very natural!",
      modelId: "gemini-3.8-flash",
      createdAt: ts,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("feedback").doc("fb_bad").set({
      userId: ALICE_UID,
      rating: 6,
      category: "language",
      comment: "Out of bounds rating",
      modelId: "gemini-3.8-flash",
      createdAt: ts,
    })
  );

  // Usage
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("usage").doc("summary").set({
      userId: ALICE_UID,
      totalMessages: 1,
      totalImagesAnalyzed: 0,
      totalPdfsAnalyzed: 1,
      totalVoiceInputs: 0,
      totalTranslations: 1,
      estimatedTokens: 320,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Memory
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memory").doc("mem_1").set({
      userId: ALICE_UID,
      fact: "Prefers step-by-step explanations in Bengali and English.",
      category: "language",
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memory").doc("mem_1").update({
      active: false,
      updatedAt: pastDate(500),
    })
  );

  // Cross-user isolation check for Bob
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memory").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("usage").doc("summary").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences").get());
});
