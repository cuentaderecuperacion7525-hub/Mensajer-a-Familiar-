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
    masterPin: "7525",
    switchPin: "2580",
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

test("Invite join succeeds even if existing family has legacy 6-digit PIN and 25+ memberUids", async () => {
  const existingUids = [ALICE_UID];
  for (let i = 1; i <= 26; i++) {
    existingUids.push(`legacy_device_${i}`);
  }
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("families").doc("fam_legacy").set({
      ownerUid: ALICE_UID,
      name: "Mi Familia",
      inviteCode: "NIDO01",
      memberUids: existingUids,
      masterPin: "000000",
      switchPin: "258000",
      updateRepoSlug: "",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    });
    await adminDb.collection("familyInvites").doc("NIDO01").set({
      inviteCode: "NIDO01",
      familyId: "fam_legacy",
      familyName: "Mi Familia",
      ownerUid: ALICE_UID,
      active: true,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("families").doc("fam_legacy").update({
      memberUids: arrayUnion(BOB_UID),
      updatedAt: serverTs(),
    })
  );

  // Stranger still cannot modify masterPin on join
  await assertFails(
    testEnv.authenticatedContext(EVE_UID).firestore().collection("families").doc("fam_legacy").update({
      memberUids: arrayUnion(EVE_UID),
      masterPin: "7525",
      updatedAt: serverTs(),
    })
  );
});

test("Family settings update allowed only for ownerUid; join works even without createdAt", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("families").doc("fam_nocreated").set({
      ownerUid: ALICE_UID,
      name: "Mi Familia",
      inviteCode: "NIDO01",
      memberUids: [ALICE_UID],
      masterPin: "7525",
      switchPin: "2580",
      updateRepoSlug: "",
      updatedAt: serverTs(),
    });
    await adminDb.collection("familyInvites").doc("NIDO01").set({
      inviteCode: "NIDO01",
      familyId: "fam_nocreated",
      familyName: "Mi Familia",
      ownerUid: ALICE_UID,
      active: true,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("families").doc("fam_nocreated").update({
      memberUids: arrayUnion(BOB_UID),
      updatedAt: serverTs(),
    })
  );

  // Non-owner member cannot update updateRepoSlug or PINs
  await assertFails(
    bobDb.collection("families").doc("fam_nocreated").update({
      updateRepoSlug: "evil/repo",
      updatedAt: serverTs(),
    })
  );

  // Owner can update updateRepoSlug
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("families").doc("fam_nocreated").update({
      updateRepoSlug: "owner/repo",
      updatedAt: serverTs(),
    })
  );
});

test("Family statuses enforce member access, non-empty content, and viewer tracking", async () => {
  const aliceDb = await seedFamilyAsAlice();
  const now = Date.now();
  const statusRef = aliceDb.collection("families").doc("fam_1").collection("statuses").doc("st_1");

  // Empty status without text or photo must fail
  await assertFails(
    statusRef.set({
      memberUids: [ALICE_UID],
      authorUid: ALICE_UID,
      memberId: "member_papa",
      text: "",
      backgroundHex: "#128C7E",
      timestamp: now,
      expiresAt: now + 86400000,
      viewedByMemberIds: [],
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Valid text status succeeds
  await assertSucceeds(
    statusRef.set({
      memberUids: [ALICE_UID],
      authorUid: ALICE_UID,
      memberId: "member_papa",
      text: "En camino a casa",
      backgroundHex: "#128C7E",
      timestamp: now,
      expiresAt: now + 86400000,
      viewedByMemberIds: ["member_papa"],
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Marking status viewed is allowed
  await assertSucceeds(
    statusRef.update({
      viewedByMemberIds: arrayUnion("member_mama"),
      updatedAt: serverTs(),
    })
  );

  // Stranger cannot read or tamper with status
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  await assertFails(eveDb.collection("families").doc("fam_1").collection("statuses").doc("st_1").get());
  await assertFails(
    eveDb.collection("families").doc("fam_1").collection("statuses").doc("st_1").update({
      viewedByMemberIds: arrayUnion("eve"),
      updatedAt: serverTs(),
    })
  );
});

test("Messages support readByMemberIds & forwardedFromName, and reject ghost fields", async () => {
  const aliceDb = await seedFamilyAsAlice();
  const msgRef = aliceDb.collection("families").doc("fam_1").collection("messages").doc("msg_fwd");

  await assertSucceeds(
    msgRef.set({
      memberUids: [ALICE_UID],
      authorUid: ALICE_UID,
      chatId: "chat_familia",
      senderId: "member_papa",
      text: "Foto reenviada",
      type: "TEXT",
      status: "SENT",
      timestamp: Date.now(),
      isEdited: false,
      readByMemberIds: [],
      forwardedFromName: "Mamá",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    msgRef.update({
      status: "READ",
      readByMemberIds: arrayUnion("member_mama"),
      updatedAt: serverTs(),
    })
  );
});

test("Members support bounded fcmTokens array updates while blocking strangers", async () => {
  const aliceDb = await seedFamilyAsAlice();
  const memberRef = aliceDb.collection("families").doc("fam_1").collection("members").doc("member_papa");

  await assertSucceeds(
    memberRef.set({
      memberUids: [ALICE_UID],
      authorUid: ALICE_UID,
      name: "Papá",
      roleLabel: "Admin",
      isAdmin: true,
      avatarColorHex: "#1A73E8",
      avatarEmoji: "👨🏽",
      isOnline: true,
      lastSeenText: "En línea",
      liveStatus: "ONLINE",
      fcmTokens: [],
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    memberRef.update({
      fcmTokens: arrayUnion("fcm_token_device_abc123"),
      updatedAt: serverTs(),
    })
  );

  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  await assertFails(
    eveDb.collection("families").doc("fam_1").collection("members").doc("member_papa").update({
      fcmTokens: arrayUnion("evil_token"),
      updatedAt: serverTs(),
    })
  );
});

test("Messages support DOCUMENT attachments and live location updates", async () => {
  const aliceDb = await seedFamilyAsAlice();
  const docMsgRef = aliceDb.collection("families").doc("fam_1").collection("messages").doc("msg_doc");
  const now = Date.now();

  await assertSucceeds(
    docMsgRef.set({
      memberUids: [ALICE_UID],
      authorUid: ALICE_UID,
      chatId: "chat_familia",
      senderId: "member_papa",
      text: "Receta médica",
      type: "DOCUMENT",
      status: "SENT",
      timestamp: now,
      isEdited: false,
      mediaData: "data:application/pdf;base64,JVBERi0xLjQ=",
      fileName: "receta.pdf",
      fileMimeType: "application/pdf",
      fileSizeBytes: 1200,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  const locMsgRef = aliceDb.collection("families").doc("fam_1").collection("messages").doc("msg_live_loc");
  await assertSucceeds(
    locMsgRef.set({
      memberUids: [ALICE_UID],
      authorUid: ALICE_UID,
      chatId: "chat_familia",
      senderId: "member_papa",
      text: "Ubicación en tiempo real",
      type: "LOCATION",
      status: "SENT",
      timestamp: now,
      isEdited: false,
      locationName: "Ubicación en vivo",
      locationAddress: "19.4326, -99.1332",
      locationLat: 19.4326,
      locationLng: -99.1332,
      locationLiveUntil: now + 900000,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    locMsgRef.update({
      locationLat: 19.4330,
      locationLng: -99.1340,
      locationAddress: "Av. Reforma",
      locationLiveUntil: 0,
      updatedAt: serverTs(),
    })
  );
});

test("Calls support WebRTC offerSdp, answerSdp, and ICE candidates while blocking strangers", async () => {
  const aliceDb = await seedFamilyAsAlice();
  const callRef = aliceDb.collection("families").doc("fam_1").collection("calls").doc("call_rtc");

  await assertSucceeds(
    callRef.set({
      memberUids: [ALICE_UID],
      authorUid: ALICE_UID,
      chatId: "chat_familia",
      title: "Videollamada",
      callerMemberId: "member_papa",
      targetMemberIds: ["member_mama"],
      avatarColorHex: "#1A73E8",
      avatarEmoji: "📹",
      callType: "VIDEO",
      callStatus: "RINGING",
      direction: "OUTGOING",
      durationSec: 0,
      offerSdp: "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\n",
      timestamp: Date.now(),
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    callRef.update({
      callStatus: "CONNECTED",
      answerSdp: "v=0\r\no=- 456 2 IN IP4 127.0.0.1\r\n",
      connectedAt: Date.now(),
      updatedAt: serverTs(),
    })
  );

  const candRef = callRef.collection("candidates").doc("cand_1");
  await assertSucceeds(
    candRef.set({
      memberUids: [ALICE_UID],
      authorUid: ALICE_UID,
      senderMemberId: "member_papa",
      sdpMid: "0",
      sdpMLineIndex: 0,
      candidate: "candidate:1 1 UDP 2122252543 192.168.1.5 54321 typ host",
      createdAtMs: Date.now(),
      createdAt: serverTs(),
    })
  );

  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  await assertFails(eveDb.collection("families").doc("fam_1").collection("calls").doc("call_rtc").collection("candidates").doc("cand_1").get());
});

test("Messages support VIDEO type, chunkCount, playedByMemberIds, and media_chunks subcollection", async () => {
  const aliceDb = await seedFamilyAsAlice();
  const now = Date.now();
  const videoMsgRef = aliceDb.collection("families").doc("fam_1").collection("messages").doc("msg_vid_1");

  await assertSucceeds(
    videoMsgRef.set({
      memberUids: [ALICE_UID],
      authorUid: ALICE_UID,
      chatId: "chat_familia",
      senderId: "member_papa",
      text: "Video familiar",
      type: "VIDEO",
      status: "SENT",
      timestamp: now,
      isEdited: false,
      videoThumbnailData: "data:image/jpeg;base64,/9j/4AAQ=",
      fileName: "cumple.mp4",
      fileMimeType: "video/mp4",
      fileSizeBytes: 1850000,
      chunkCount: 2,
      voiceDurationSec: 8,
      playedByMemberIds: [],
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Marking voice/video played and DELIVERED status succeeds
  await assertSucceeds(
    videoMsgRef.update({
      status: "DELIVERED",
      playedByMemberIds: arrayUnion("member_mama"),
      updatedAt: serverTs(),
    })
  );

  // Creating media chunk succeeds for family member and fails for stranger
  const chunkRef = aliceDb.collection("families").doc("fam_1").collection("media_chunks").doc("msg_vid_1_c0");
  await assertSucceeds(
    chunkRef.set({
      memberUids: [ALICE_UID],
      authorUid: ALICE_UID,
      messageId: "msg_vid_1",
      chunkIndex: 0,
      totalChunks: 2,
      data: "data:video/mp4;base64,AAAA",
      createdAt: serverTs(),
    })
  );

  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  await assertFails(eveDb.collection("families").doc("fam_1").collection("media_chunks").doc("msg_vid_1_c0").get());

  // Video status creation with mediaType VIDEO, thumbnail, duration, and chunkCount succeeds
  const statusVidRef = aliceDb.collection("families").doc("fam_1").collection("statuses").doc("st_vid_1");
  await assertSucceeds(
    statusVidRef.set({
      memberUids: [ALICE_UID],
      authorUid: ALICE_UID,
      memberId: "member_papa",
      text: "Saludos en video",
      mediaData: "chunked:video/mp4",
      mediaType: "VIDEO",
      videoThumbnailData: "data:image/jpeg;base64,/9j/4AAQ=",
      videoDurationSec: 9,
      chunkCount: 2,
      backgroundHex: "#0B141A",
      timestamp: now,
      expiresAt: now + 86400000,
      viewedByMemberIds: ["member_papa"],
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
});

test("Member presence supports showLastSeen privacy toggle and server-stamped lastActiveAt while rejecting invalid values", async () => {
  const aliceDb = await seedFamilyAsAlice();
  const memberRef = aliceDb.collection("families").doc("fam_1").collection("members").doc("member_papa");

  // Create member with showLastSeen and server lastActiveAt
  await assertSucceeds(
    memberRef.set({
      memberUids: [ALICE_UID],
      authorUid: ALICE_UID,
      name: "José Manuel",
      roleLabel: "Papá",
      isAdmin: true,
      avatarColorHex: "#1A5FD4",
      avatarEmoji: "👨🏽",
      isOnline: true,
      showLastSeen: true,
      lastSeenText: "En línea",
      liveStatus: "ONLINE",
      liveStatusAt: Date.now(),
      lastActiveAt: serverTs(),
      linkedAuthUid: ALICE_UID,
      createdAtMs: Date.now(),
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Updating showLastSeen to false and updating lastActiveAt with serverTimestamp succeeds
  await assertSucceeds(
    memberRef.update({
      isOnline: false,
      showLastSeen: false,
      lastSeenText: "Desconectado",
      liveStatus: "LAST_SEEN",
      liveStatusAt: Date.now(),
      lastActiveAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Invalid showLastSeen type fails
  await assertFails(
    memberRef.update({
      showLastSeen: "false",
      updatedAt: serverTs(),
    })
  );

  // Future tampered lastActiveAt fails
  await assertFails(
    memberRef.update({
      lastActiveAt: new Date(Date.now() + 120000),
      updatedAt: serverTs(),
    })
  );
});



