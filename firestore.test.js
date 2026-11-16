const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const rulesUnitDir = require.resolve("@firebase/rules-unit-testing");
const firebase = require(require.resolve("firebase/compat/app", { paths: [rulesUnitDir] }));
require(require.resolve("firebase/compat/firestore", { paths: [rulesUnitDir] }));

const serverTs = () => firebase.firestore.FieldValue.serverTimestamp();
const arrayUnion = (v) => firebase.firestore.FieldValue.arrayUnion(v);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1";
const BOB_UID = "bob_uid_2";
const EVE_UID = "eve_stranger_3";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

async function seedFamilyAsAlice() {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const batch = aliceDb.batch();
  const famRef = aliceDb.collection("families").doc("fam_1");
  const invRef = aliceDb.collection("familyInvites").doc("ABC123");
  batch.set(famRef, {
    ownerUid: ALICE_UID,
    name: "Familia García",
    inviteCode: "ABC123",
    memberUids: [ALICE_UID],
    masterPin: "752581",
    switchPin: "258046",
    updateRepoSlug: "",
    createdAt: serverTs(),
    updatedAt: serverTs(),
  });
  batch.set(invRef, {
    inviteCode: "ABC123",
    familyId: "fam_1",
    familyName: "Familia García",
    ownerUid: ALICE_UID,
    active: true,
    createdAt: serverTs(),
    updatedAt: serverTs(),
  });
  await batch.commit();

  await aliceDb.collection("families").doc("fam_1").collection("chats").doc("chat_familia").set({
    memberUids: [ALICE_UID],
    authorUid: ALICE_UID,
    type: "GROUP",
    title: "Familia Unida",
    avatarColorHex: "#1A73E8",
    avatarEmoji: "🏡",
    participantIds: ["member_papa"],
    lastMessageAt: Date.now(),
    createdAt: serverTs(),
    updatedAt: serverTs(),
  });
  return aliceDb;
}

test("Unauthenticated user cannot read families or invites", async () => {
  await seedFamilyAsAlice();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("families").doc("fam_1").get());
  await assertFails(unauthDb.collection("familyInvites").doc("ABC123").get());
  await assertFails(unauthDb.collection("families").get());
});

test("Stranger cannot list family invites or read another family", async () => {
  await seedFamilyAsAlice();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  await assertFails(eveDb.collection("familyInvites").get());
  await assertFails(eveDb.collection("families").doc("fam_1").get());
  await assertFails(eveDb.collection("families").where("memberUids", "array-contains", ALICE_UID).get());
});

test("Invite join succeeds via inviteCode lookup + arrayUnion; rejects arbitrary field hijack", async () => {
  await seedFamilyAsAlice();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const invSnap = await assertSucceeds(bobDb.collection("familyInvites").doc("ABC123").get());
  const familyId = invSnap.data().familyId;

  // Reject hijacking ownerUid or masterPin while joining
  await assertFails(
    bobDb.collection("families").doc(familyId).update({
      memberUids: arrayUnion(BOB_UID),
      ownerUid: BOB_UID,
      updatedAt: serverTs(),
    })
  );

  // Valid join with arrayUnion
  await assertSucceeds(
    bobDb.collection("families").doc(familyId).update({
      memberUids: arrayUnion(BOB_UID),
      updatedAt: serverTs(),
    })
  );

  // Bob can now query his families
  await assertSucceeds(bobDb.collection("families").where("memberUids", "array-contains", BOB_UID).get());
});

test("Shadow update (ghost field) and ID poisoning are rejected", async () => {
  const aliceDb = await seedFamilyAsAlice();

  // Ghost field in message
  await assertFails(
    aliceDb.collection("families").doc("fam_1").collection("messages").doc("msg_1").set({
      memberUids: [ALICE_UID],
      authorUid: ALICE_UID,
      chatId: "chat_familia",
      senderId: "member_papa",
      text: "Hola",
      type: "TEXT",
      status: "SENT",
      timestamp: Date.now(),
      isEdited: false,
      ghostField: "hack",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Valid message
  await assertSucceeds(
    aliceDb.collection("families").doc("fam_1").collection("messages").doc("msg_1").set({
      memberUids: [ALICE_UID],
      authorUid: ALICE_UID,
      chatId: "chat_familia",
      senderId: "member_papa",
      text: "Hola familia",
      type: "TEXT",
      status: "SENT",
      timestamp: Date.now(),
      isEdited: false,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Orphaned message referencing non-existent chatId fails
  await assertFails(
    aliceDb.collection("families").doc("fam_1").collection("messages").doc("msg_orphan").set({
      memberUids: [ALICE_UID],
      authorUid: ALICE_UID,
      chatId: "chat_missing",
      senderId: "member_papa",
      text: "Orphan",
      type: "TEXT",
      status: "SENT",
      timestamp: Date.now(),
      isEdited: false,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
});

test("Call signaling enforces terminal state lock once ENDED", async () => {
  const aliceDb = await seedFamilyAsAlice();
  const callRef = aliceDb.collection("families").doc("fam_1").collection("calls").doc("call_1");

  await assertSucceeds(
    callRef.set({
      memberUids: [ALICE_UID],
      authorUid: ALICE_UID,
      chatId: "chat_familia",
      title: "Familia Unida",
      callerMemberId: "member_papa",
      targetMemberIds: ["member_mama"],
      avatarColorHex: "#1A73E8",
      avatarEmoji: "🏡",
      callType: "VOICE",
      callStatus: "RINGING",
      direction: "OUTGOING",
      durationSec: 0,
      timestamp: Date.now(),
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // End the call
  await assertSucceeds(
    callRef.update({
      callStatus: "ENDED",
      durationSec: 12,
      updatedAt: serverTs(),
    })
  );

  // Re-opening terminal call must fail
  await assertFails(
    callRef.update({
      callStatus: "RINGING",
      updatedAt: serverTs(),
    })
  );
});
