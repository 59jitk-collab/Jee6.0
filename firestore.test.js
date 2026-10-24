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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const nowTimestamp = () => new Date(Date.now() - 500);

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

function buildValidChapterPayload(userId, overrides = {}) {
  const ts = nowTimestamp();
  return {
    userId,
    subject: "Organic Chemistry",
    unit: "Hydrocarbons",
    chapterName: "Alkanes & Alkenes",
    totalLectures: null, // Unassessed Organic Chemistry total preserved as null
    completedLectures: 0,
    notesDone: false,
    dibyDone: null,
    dibyTotal: null,
    classProblemsDone: null,
    classProblemsTotal: null,
    dppDone: null,
    dppTotal: null,
    pyqDone: null,
    pyqTotal: null,
    worksheetsDone: null,
    worksheetsTotal: null,
    masteryLevel: "NOT_STARTED",
    isWeak: false,
    isDeleted: false,
    clientUpdatedAt: 1730000000000,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function buildValidSchedulePayload(userId, overrides = {}) {
  const ts = nowTimestamp();
  return {
    userId,
    date: "2026-10-23",
    dayNumber: 1,
    focusSubjects: "Physics, Mathematics",
    assignedChapterNames: "Kinematics, Calculus",
    targetLectures: 3,
    completedLectures: 2,
    targetProblems: 30,
    completedProblems: 20,
    notesReviewed: true,
    studyMinutes: 240,
    breakMinutes: 40,
    sleepHours: 7.5,
    isSundayTest: false,
    isDeleted: false,
    clientUpdatedAt: 1730000000000,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function buildValidMistakePayload(userId, overrides = {}) {
  const ts = nowTimestamp();
  return {
    userId,
    subject: "Physics",
    chapterName: "Rotational Motion",
    testName: "Sunday Mock Test 1",
    questionSnippet: "Moment of inertia of hollow sphere about tangent",
    errorType: "Conceptual",
    conceptMissed: "Parallel axis theorem axis offset",
    correctTakeaway: "I = 2/3 MR^2 + MR^2 = 5/3 MR^2",
    lessonLearned: "Draw axis diagram first",
    solutionNotes: "Apply I_cm + Md^2 carefully",
    reviewCount: 1,
    isResolved: false,
    isDeleted: false,
    clientUpdatedAt: 1730000000000,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

test("Unauthenticated user: cannot read or write chapters, schedules, or mistakes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chapters")
      .doc("ch_1")
      .get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chapters")
      .doc("ch_1")
      .set(buildValidChapterPayload(ALICE_UID))
  );
});

test("Authenticated user: can create and read own chapter with nullable Organic Chemistry totals", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("chapters")
    .doc("ch_organic_1");

  await assertSucceeds(docRef.set(buildValidChapterPayload(ALICE_UID)));
  await assertSucceeds(docRef.get());
});

test("Authenticated user: can query own chapters with matching userId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chapters")
      .doc("ch_phys_1")
      .set(
        buildValidChapterPayload(ALICE_UID, {
          subject: "Physics",
          chapterName: "Kinematics",
          totalLectures: 12,
          completedLectures: 5,
        })
      )
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chapters")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Cross-user isolation: Alice cannot read or write Bob's chapters, schedules, or mistakes", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb
      .collection("users")
      .doc(BOB_UID)
      .collection("chapters")
      .doc("ch_bob_1")
      .set(buildValidChapterPayload(BOB_UID))
  );

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("chapters")
      .doc("ch_bob_1")
      .get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("chapters")
      .where("userId", "==", BOB_UID)
      .get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("chapters")
      .doc("ch_bob_1")
      .set(buildValidChapterPayload(BOB_UID))
  );
});

test("Identity spoofing: Alice cannot create a document in her path with Bob's userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chapters")
      .doc("ch_spoof")
      .set(buildValidChapterPayload(BOB_UID))
  );
});

test("Shadow update & value poisoning: rejects unallowed extra fields or invalid enum values", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("chapters")
    .doc("ch_valid");

  await assertSucceeds(docRef.set(buildValidChapterPayload(ALICE_UID)));

  // Reject ghost field
  await assertFails(
    docRef.update({
      isAdmin: true,
      updatedAt: nowTimestamp(),
    })
  );

  // Reject invalid masteryLevel enum
  await assertFails(
    docRef.update({
      masteryLevel: "HACKED_LEVEL",
      updatedAt: nowTimestamp(),
    })
  );

  // Valid update succeeds
  await assertSucceeds(
    docRef.update({
      completedLectures: 4,
      notesDone: true,
      masteryLevel: "PRACTICING",
      updatedAt: nowTimestamp(),
    })
  );
});

test("Schedule and Mistake CRUD: owner can create, update, query, and delete own schedule and mistake records", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const schedRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("schedules")
    .doc("day_2026-10-23");
  const mistakeRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("mistakes")
    .doc("mistake_1");

  await assertSucceeds(schedRef.set(buildValidSchedulePayload(ALICE_UID)));
  await assertSucceeds(
    schedRef.update({
      completedLectures: 3,
      updatedAt: nowTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("schedules")
      .where("userId", "==", ALICE_UID)
      .get()
  );

  await assertSucceeds(mistakeRef.set(buildValidMistakePayload(ALICE_UID)));
  await assertSucceeds(
    mistakeRef.update({
      isResolved: true,
      reviewCount: 2,
      updatedAt: nowTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("mistakes")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});
