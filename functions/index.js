const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const DATABASE_ID = "ai-studio-android-nidofami-a8e12eb9-edf1-4ad5-a4f0-5cadecff7ae7";
const db = getFirestore(DATABASE_ID);

function stripMarkdownPreview(raw) {
  return String(raw || "")
    .replace(/```[\s\S]*?```/g, "")
    .replace(/[*_~`]/g, "")
    .trim();
}

function buildMessageBody(msg) {
  switch (msg.type) {
    case "IMAGE":
      return msg.text && String(msg.text).trim()
        ? `📷 ${stripMarkdownPreview(msg.text)}`
        : "📷 Foto";
    case "VOICE": {
      const sec = Number(msg.voiceDurationSec || 0);
      const mins = Math.floor(sec / 60);
      const rem = sec % 60;
      const dur = `${mins}:${String(rem).padStart(2, "0")}`;
      return `🎤 Nota de voz (${dur})`;
    }
    case "LOCATION":
      return `📍 ${msg.locationName || "Ubicación"}`;
    case "POLL":
      return `📊 ${msg.pollQuestion || "Encuesta"}`;
    default:
      return stripMarkdownPreview(msg.text || "Nuevo mensaje");
  }
}

async function cleanupInvalidTokens(familyId, memberTokenPairs, sendResponse) {
  const staleByMember = new Map();
  sendResponse.responses.forEach((resp, idx) => {
    if (!resp.success && resp.error) {
      const code = resp.error.code || "";
      if (
        code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token"
      ) {
        const pair = memberTokenPairs[idx];
        if (!staleByMember.has(pair.memberId)) {
          staleByMember.set(pair.memberId, []);
        }
        staleByMember.get(pair.memberId).push(pair.token);
      }
    }
  });

  await Promise.all(
    Array.from(staleByMember.entries()).map(([memberId, badTokens]) =>
      db
        .collection("families")
        .doc(familyId)
        .collection("members")
        .doc(memberId)
        .update({
          fcmTokens: FieldValue.arrayRemove(...badTokens),
          updatedAt: FieldValue.serverTimestamp(),
        })
        .catch(() => null)
    )
  );
}

exports.onNewFamilyMessage = onDocumentCreated(
  {
    document: "families/{familyId}/messages/{messageId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const msg = snap.data();
    if (!msg || msg.type === "DELETED") return;

    const { familyId } = event.params;
    const familyRef = db.collection("families").doc(familyId);

    const [chatSnap, membersSnap] = await Promise.all([
      familyRef.collection("chats").doc(String(msg.chatId)).get(),
      familyRef.collection("members").get(),
    ]);
    if (!chatSnap.exists) return;

    const chat = chatSnap.data() || {};
    const participantIds = Array.isArray(chat.participantIds) ? chat.participantIds : [];
    const recipientIds = participantIds.filter((id) => id && id !== msg.senderId);
    if (recipientIds.length === 0) return;

    let senderName = "Familiar";
    const tokenPairs = [];
    const seenTokens = new Set();

    membersSnap.docs.forEach((doc) => {
      const m = doc.data() || {};
      if (doc.id === msg.senderId && m.name) {
        senderName = String(m.name);
      }
      if (recipientIds.includes(doc.id) && Array.isArray(m.fcmTokens)) {
        m.fcmTokens.forEach((tok) => {
          const clean = typeof tok === "string" ? tok.trim() : "";
          if (clean && !seenTokens.has(clean)) {
            seenTokens.add(clean);
            tokenPairs.push({ memberId: doc.id, token: clean });
          }
        });
      }
    });

    if (tokenPairs.length === 0) return;

    const isGroup = chat.type === "GROUP";
    const chatTitle = isGroup ? String(chat.title || "Grupo familiar") : senderName;
    const body = buildMessageBody(msg);
    if (!body) return;

    const sendResponse = await getMessaging().sendEachForMulticast({
      tokens: tokenPairs.map((p) => p.token),
      data: {
        type: "message",
        chatId: String(msg.chatId),
        chatTitle,
        senderId: String(msg.senderId || ""),
        senderName,
        body: body.slice(0, 500),
        isGroup: String(isGroup),
        targetMemberIds: recipientIds.join(","),
        timestamp: String(msg.timestamp || Date.now()),
      },
      android: {
        priority: "high",
        ttl: 86400 * 1000,
      },
    });

    await cleanupInvalidTokens(familyId, tokenPairs, sendResponse);
  }
);

exports.onNewFamilyCall = onDocumentCreated(
  {
    document: "families/{familyId}/calls/{callId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const call = snap.data();
    if (!call || call.callStatus !== "RINGING") return;

    const { familyId, callId } = event.params;
    const targetIds = Array.isArray(call.targetMemberIds)
      ? call.targetMemberIds.filter((id) => id && id !== call.callerMemberId)
      : [];
    if (targetIds.length === 0) return;

    const membersSnap = await db.collection("families").doc(familyId).collection("members").get();
    let callerName = String(call.title || "Familiar");
    const tokenPairs = [];
    const seenTokens = new Set();

    membersSnap.docs.forEach((doc) => {
      const m = doc.data() || {};
      if (doc.id === call.callerMemberId && m.name) {
        callerName = String(m.name);
      }
      if (targetIds.includes(doc.id) && Array.isArray(m.fcmTokens)) {
        m.fcmTokens.forEach((tok) => {
          const clean = typeof tok === "string" ? tok.trim() : "";
          if (clean && !seenTokens.has(clean)) {
            seenTokens.add(clean);
            tokenPairs.push({ memberId: doc.id, token: clean });
          }
        });
      }
    });

    if (tokenPairs.length === 0) return;

    const sendResponse = await getMessaging().sendEachForMulticast({
      tokens: tokenPairs.map((p) => p.token),
      data: {
        type: "call",
        callId: String(callId),
        chatId: String(call.chatId || ""),
        callerMemberId: String(call.callerMemberId || ""),
        callerName,
        isVideo: String(call.callType === "VIDEO"),
        timestamp: String(call.timestamp || Date.now()),
      },
      android: {
        priority: "high",
        ttl: 60 * 1000,
      },
    });

    await cleanupInvalidTokens(familyId, tokenPairs, sendResponse);
  }
);
