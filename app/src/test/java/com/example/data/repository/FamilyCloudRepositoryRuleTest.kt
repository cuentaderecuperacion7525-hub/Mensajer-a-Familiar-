package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.data.remote.FamilyCloudRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FamilyCloudRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createFamily_validOwner_createsFamilyAndAdminMember() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = FamilyCloudRepository(firestore)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createFamily(
        uid = aliceUid,
        familyName = "Familia Prueba",
        adminName = "Papá"
      )
    }
    assertTrue("createFamily failed: ${result.exceptionOrNull()}", result.isSuccess)
    val (familyId, adminMemberId) = result.getOrThrow()
    assertTrue(familyId.isNotBlank())
    assertTrue(adminMemberId.isNotBlank())

    val families = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMyFamilies(aliceUid).first { list -> list.any { it.id == familyId } }
    }
    assertTrue(families.any { it.id == familyId && it.name == "Familia Prueba" })

    val members = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMembers(familyId, aliceUid).first { list -> list.any { it.id == adminMemberId } }
    }
    assertTrue(members.any { it.id == adminMemberId && it.isAdmin })
  }

  @Test
  fun joinFamilyByInviteCode_secondUser_addsMemberUidAndReadsMembers() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = FamilyCloudRepository(firestore)

    val (familyId, adminMemberId) = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.createFamily(
        uid = aliceUid,
        familyName = "Casa Unida",
        adminName = "Papá"
      ).getOrThrow()
    }
    val family = withTimeout(FLOW_TIMEOUT_MS) {
      aliceRepo.observeFamily(familyId).first { it != null && it.inviteCode.isNotBlank() }
    }!!

    val bobUid = signInTestUser(BOB_EMAIL)
    val bobRepo = FamilyCloudRepository(firestore)
    val joinResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.joinFamilyByInviteCode(bobUid, family.inviteCode)
    }
    assertTrue("joinFamilyByInviteCode failed: ${joinResult.exceptionOrNull()}", joinResult.isSuccess)
    assertEquals(familyId, joinResult.getOrThrow())

    val rejoinResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.joinFamilyByInviteCode(bobUid, family.inviteCode)
    }
    assertTrue("re-joining existing family should succeed cleanly", rejoinResult.isSuccess)

    val bobMembers = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeMembers(familyId, bobUid).first { list -> list.any { it.id == adminMemberId } }
    }
    assertTrue(bobMembers.any { it.id == adminMemberId })

    val bobChats = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeChats(familyId, bobUid).first { it.isNotEmpty() }
    }
    val mainChatId = bobChats.first().id

    val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val msgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.createMessage(
        familyId = familyId,
        message = ChatMessageEntity(
          id = msgId,
          chatId = mainChatId,
          senderId = adminMemberId,
          text = "Hola desde Bob",
          type = MessageType.TEXT,
          status = MessageStatus.SENT,
          timestamp = System.currentTimeMillis()
        ),
        memberUids = listOf(aliceUid, bobUid),
        callerUid = bobUid
      )
    }
    assertTrue("Bob createMessage failed: ${msgResult.exceptionOrNull()}", msgResult.isSuccess)
  }

  @Test
  fun createAndObserveMessages_authenticatedMember_emitsRealtime() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = FamilyCloudRepository(firestore)

    val (familyId, adminMemberId) = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createFamily(uid = aliceUid, familyName = "Hogar", adminName = "Papá").getOrThrow()
    }
    val chats = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeChats(familyId, aliceUid).first { it.isNotEmpty() }
    }
    val chatId = chats.first().id
    val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}"

    val sendResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createMessage(
        familyId = familyId,
        message = ChatMessageEntity(
          id = msgId,
          chatId = chatId,
          senderId = adminMemberId,
          text = "Mensaje en tiempo real",
          type = MessageType.TEXT,
          status = MessageStatus.SENT,
          timestamp = System.currentTimeMillis()
        ),
        memberUids = listOf(aliceUid),
        callerUid = aliceUid
      )
    }
    assertTrue("createMessage failed: ${sendResult.exceptionOrNull()}", sendResult.isSuccess)

    val messages = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMessages(familyId, aliceUid).first { list -> list.any { it.id == msgId } }
    }
    assertTrue(messages.any { it.id == msgId && it.text == "Mensaje en tiempo real" })
  }

  @Test
  fun observeMembers_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = FamilyCloudRepository(firestore)

    var caught: Exception? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeMembers("fam_nonexistent", "unauth_uid").first()
      }
    } catch (e: Exception) {
      caught = e
    }
    assertNotNull("Expected PERMISSION_DENIED exception for unauthenticated caller", caught)
    val firestoreException = caught!!.unwrapFirestoreException()
    assertNotNull("Expected nested FirebaseFirestoreException, got $caught", firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException!!.code)
  }

  @Test
  fun getForeignFamily_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = FamilyCloudRepository(firestore)
    val (familyId, _) = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.createFamily(uid = aliceUid, familyName = "Privada", adminName = "Papá").getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    var caught: FirebaseFirestoreException? = null
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore.collection("families").document(familyId).get().await()
      }
    } catch (e: FirebaseFirestoreException) {
      caught = e
    }
    assertNotNull("Expected cross-user get on foreign family to fail", caught)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caught!!.code)
  }

  @Test
  fun statusesAndReadReceipts_authenticatedMember_persistAndSyncInRealTime() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = FamilyCloudRepository(firestore)

    val (familyId, adminMemberId) = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createFamily(uid = aliceUid, familyName = "Casa Feliz", adminName = "Papá").getOrThrow()
    }
    val chats = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeChats(familyId, aliceUid).first { it.isNotEmpty() }
    }
    val chatId = chats.first().id

    // Update member bio & photo data
    val profileRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateMemberProfileDetails(
        familyId = familyId,
        memberId = adminMemberId,
        fields = mapOf(
          "bioText" to "Disponible en casa",
          "avatarPhotoData" to "data:image/jpeg;base64,AAAA"
        )
      )
    }
    assertTrue("updateMemberProfileDetails failed: ${profileRes.exceptionOrNull()}", profileRes.isSuccess)
    val updatedMembers = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMembers(familyId, aliceUid).first { list ->
        list.any { it.id == adminMemberId && it.bioText == "Disponible en casa" }
      }
    }
    assertTrue(updatedMembers.any { it.avatarPhotoData == "data:image/jpeg;base64,AAAA" })

    // Create 24h status and mark viewed
    val now = System.currentTimeMillis()
    val statusId = "st_${UUID.randomUUID().toString().replace("-", "").take(10)}"
    val createStatusRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createStatus(
        familyId = familyId,
        status = com.example.data.model.FamilyStatusEntity(
          id = statusId,
          memberId = adminMemberId,
          text = "Buen día familia",
          backgroundHex = "#128C7E",
          createdAt = now,
          expiresAt = now + 86_400_000L,
          authorUid = aliceUid
        ),
        memberUids = listOf(aliceUid),
        callerUid = aliceUid
      )
    }
    assertTrue("createStatus failed: ${createStatusRes.exceptionOrNull()}", createStatusRes.isSuccess)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.markStatusViewed(familyId, statusId, "member_mama")
    }
    val statuses = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeStatuses(familyId, aliceUid).first { list ->
        list.any { it.id == statusId && it.viewedByMemberIdsJson.contains("member_mama") }
      }
    }
    assertTrue(statuses.any { it.id == statusId && it.text == "Buen día familia" })

    // Create forwarded message & mark read by member
    val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(10)}"
    val msgRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createMessage(
        familyId = familyId,
        message = ChatMessageEntity(
          id = msgId,
          chatId = chatId,
          senderId = adminMemberId,
          text = "Aviso importante",
          forwardedFromName = "Mamá",
          timestamp = now
        ),
        memberUids = listOf(aliceUid),
        callerUid = aliceUid
      )
    }
    assertTrue("createMessage forwarded failed: ${msgRes.exceptionOrNull()}", msgRes.isSuccess)

    val readRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.markMessageReadByMember(familyId, msgId, "member_mama")
    }
    assertTrue("markMessageReadByMember failed: ${readRes.exceptionOrNull()}", readRes.isSuccess)
    val messages = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMessages(familyId, aliceUid).first { list ->
        list.any { it.id == msgId && it.status == MessageStatus.READ }
      }
    }
    val storedMsg = messages.first { it.id == msgId }
    assertEquals("Mamá", storedMsg.forwardedFromName)
    assertTrue(storedMsg.readByMemberIdsJson.contains("member_mama"))

    // Register & unregister FCM push token on member profile
    val regRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.registerMemberFcmToken(familyId, adminMemberId, "test_fcm_token_xyz")
    }
    assertTrue("registerMemberFcmToken failed: ${regRes.exceptionOrNull()}", regRes.isSuccess)
    val membersAfterToken = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMembers(familyId, aliceUid).first { list ->
        list.any { it.id == adminMemberId && it.fcmTokens.contains("test_fcm_token_xyz") }
      }
    }
    assertTrue(membersAfterToken.any { it.fcmTokens.contains("test_fcm_token_xyz") })

    val unregRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.unregisterMemberFcmToken(familyId, adminMemberId, "test_fcm_token_xyz")
    }
    assertTrue("unregisterMemberFcmToken failed: ${unregRes.exceptionOrNull()}", unregRes.isSuccess)
  }

  @Test
  fun documentMessages_liveLocations_andWebRtcSignaling_persistCorrectly() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = FamilyCloudRepository(firestore)

    val (familyId, adminMemberId) = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createFamily(uid = aliceUid, familyName = "Casa RTC", adminName = "Papá").getOrThrow()
    }
    val chats = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeChats(familyId, aliceUid).first { it.isNotEmpty() }
    }
    val chatId = chats.first().id
    val now = System.currentTimeMillis()

    // Document message
    val docMsgId = "msg_doc_${UUID.randomUUID().toString().replace("-", "").take(8)}"
    val docRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createMessage(
        familyId = familyId,
        message = ChatMessageEntity(
          id = docMsgId,
          chatId = chatId,
          senderId = adminMemberId,
          text = "Boleta",
          type = MessageType.DOCUMENT,
          mediaUri = "data:application/pdf;base64,JVBERi0xLjQ=",
          fileName = "boleta.pdf",
          fileMimeType = "application/pdf",
          fileSizeBytes = 1024L,
          timestamp = now
        ),
        memberUids = listOf(aliceUid),
        callerUid = aliceUid
      )
    }
    assertTrue("Document createMessage failed: ${docRes.exceptionOrNull()}", docRes.isSuccess)

    // Live location update
    val locMsgId = "msg_loc_${UUID.randomUUID().toString().replace("-", "").take(8)}"
    val locCreateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createMessage(
        familyId = familyId,
        message = ChatMessageEntity(
          id = locMsgId,
          chatId = chatId,
          senderId = adminMemberId,
          text = "En camino",
          type = MessageType.LOCATION,
          locationName = "Ubicación en vivo",
          locationAddress = "19.43, -99.13",
          locationLat = 19.43,
          locationLng = -99.13,
          locationLiveUntil = now + 900_000L,
          timestamp = now
        ),
        memberUids = listOf(aliceUid),
        callerUid = aliceUid
      )
    }
    assertTrue("Live location create failed: ${locCreateRes.exceptionOrNull()}", locCreateRes.isSuccess)

    val locUpdateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateMessageFields(
        familyId = familyId,
        messageId = locMsgId,
        fields = mapOf(
          "locationLat" to 19.44,
          "locationLng" to -99.14,
          "locationLiveUntil" to 0L
        )
      )
    }
    assertTrue("Live location update failed: ${locUpdateRes.exceptionOrNull()}", locUpdateRes.isSuccess)

    // WebRTC call SDP + ICE candidate
    val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(8)}"
    val callCreateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createCall(
        familyId = familyId,
        call = com.example.data.model.CallRecordEntity(
          id = callId,
          chatId = chatId,
          title = "Casa RTC",
          callerId = adminMemberId,
          avatarColorHex = "#1A73E8",
          avatarEmoji = "🏡",
          callType = com.example.data.model.CallType.VIDEO,
          direction = com.example.data.model.CallDirection.OUTGOING,
          durationSec = 0,
          timestamp = now,
          callStatus = com.example.data.model.CallSignalStatus.RINGING,
          targetMemberIdsJson = com.example.data.model.JsonHelpers.encodeStringList(listOf("member_mama")),
          offerSdp = "v=0\r\no=- 100 2 IN IP4 127.0.0.1\r\n",
          authorUid = aliceUid
        ),
        memberUids = listOf(aliceUid),
        callerUid = aliceUid
      )
    }
    assertTrue("WebRTC createCall failed: ${callCreateRes.exceptionOrNull()}", callCreateRes.isSuccess)

    val ansRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateCallSdpAnswer(familyId, callId, "v=0\r\no=- 200 2 IN IP4 127.0.0.1\r\n")
    }
    assertTrue("updateCallSdpAnswer failed: ${ansRes.exceptionOrNull()}", ansRes.isSuccess)

    val candRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.addCallIceCandidate(
        familyId = familyId,
        callId = callId,
        candidate = com.example.data.model.CallIceCandidateEntity(
          id = "cand_1",
          senderMemberId = adminMemberId,
          sdpMid = "0",
          sdpMLineIndex = 0,
          candidate = "candidate:1 1 UDP 2122252543 192.168.1.5 54321 typ host",
          createdAtMs = now,
          authorUid = aliceUid
        ),
        memberUids = listOf(aliceUid),
        callerUid = aliceUid
      )
    }
    assertTrue("addCallIceCandidate failed: ${candRes.exceptionOrNull()}", candRes.isSuccess)

    val candidates = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeCallIceCandidates(familyId, callId, aliceUid).first { it.isNotEmpty() }
    }
    assertEquals("0", candidates.first().sdpMid)

    // Video message with media_chunks + voice playedByMemberIds + delivered receipt
    val vidMsgId = "msg_vid_${UUID.randomUUID().toString().replace("-", "").take(8)}"
    val chunkRes0 = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.uploadMediaChunk(
        familyId = familyId,
        chunk = com.example.data.model.MediaChunkEntity(
          id = "${vidMsgId}_c0",
          messageId = vidMsgId,
          chunkIndex = 0,
          totalChunks = 2,
          data = "data:video/mp4;base64,AAAA"
        ),
        memberUids = listOf(aliceUid),
        callerUid = aliceUid
      )
    }
    assertTrue("uploadMediaChunk 0 failed: ${chunkRes0.exceptionOrNull()}", chunkRes0.isSuccess)
    val chunkRes1 = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.uploadMediaChunk(
        familyId = familyId,
        chunk = com.example.data.model.MediaChunkEntity(
          id = "${vidMsgId}_c1",
          messageId = vidMsgId,
          chunkIndex = 1,
          totalChunks = 2,
          data = "BBBB"
        ),
        memberUids = listOf(aliceUid),
        callerUid = aliceUid
      )
    }
    assertTrue("uploadMediaChunk 1 failed: ${chunkRes1.exceptionOrNull()}", chunkRes1.isSuccess)

    val vidMsgRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createMessage(
        familyId = familyId,
        message = ChatMessageEntity(
          id = vidMsgId,
          chatId = chatId,
          senderId = adminMemberId,
          text = "Video corto",
          type = MessageType.VIDEO,
          videoThumbnailData = "data:image/jpeg;base64,/9j/4AAQ=",
          fileName = "saludo.mp4",
          fileMimeType = "video/mp4",
          fileSizeBytes = 204800L,
          chunkCount = 2,
          voiceDurationSec = 6,
          timestamp = now
        ),
        memberUids = listOf(aliceUid),
        callerUid = aliceUid
      )
    }
    assertTrue("Video createMessage failed: ${vidMsgRes.exceptionOrNull()}", vidMsgRes.isSuccess)

    val reassembled = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.fetchMediaChunks(familyId, vidMsgId, 2)
    }
    assertEquals("data:video/mp4;base64,AAAABBBB", reassembled.getOrNull())

    val delivRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.markMessageDeliveredToRecipient(familyId, vidMsgId)
    }
    assertTrue("markMessageDeliveredToRecipient failed", delivRes.isSuccess)

    val playedRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.markMessageVoicePlayedByMember(familyId, vidMsgId, "member_mama")
    }
    assertTrue("markMessageVoicePlayedByMember failed", playedRes.isSuccess)
  }

  private companion object {
    const val ALICE_EMAIL = "alice@test.com"
    const val BOB_EMAIL = "bob@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
