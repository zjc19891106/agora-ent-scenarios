package io.agora.scene.show.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.agora.scene.base.BuildConfig
import io.agora.scene.base.manager.UserManager
import io.agora.scene.base.utils.TimeUtils
import io.agora.scene.show.ShowLogger
import io.agora.syncmanager.rtm.*
import io.agora.syncmanager.rtm.Sync.EventListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.random.Random

class ShowSyncManagerServiceImpl(
    private val context: Context,
    private val errorHandler: (Exception) -> Unit
) : ShowServiceProtocol {
    private val TAG = "ShowSyncManagerServiceImpl"
    private val kSceneId = "scene_show"
    private val kCollectionIdUser = "userCollection"
    private val kCollectionIdMessage = "show_message_collection"
    private val kCollectionIdSeatApply = "show_seat_apply_collection"
    private val kCollectionIdSeatInvitation = "show_seat_invitation_collection"
    private val kCollectionIdPKInvitation = "show_pk_invitation_collection"
    private val kCollectionIdInteractionInfo = "show_interaction_collection"

    @Volatile
    private var syncInitialized = false
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val workerExecutor by lazy { Executors.newSingleThreadExecutor() }
    private val cloudPlayerService by lazy { CloudPlayerService() }

    // global cache data
    private val roomMap = mutableMapOf<String, ShowRoomDetailModel>()
    private val objIdOfUserId = mutableMapOf<String, String>() // key: userId, value: objectId
    private val userList = ArrayList<ShowUser>()
    private val micSeatApplyList = ArrayList<ShowMicSeatApply>()
    private val micSeatInvitationList = ArrayList<ShowUser>()
    private val pKInvitationList = ArrayList<ShowPKInvitation>()
    private val interactionInfoList = ArrayList<ShowInteractionInfo>()

    // cache objectId
    private val objIdOfSeatApply = ArrayList<String>() // objectId of seat Apply
    private val objIdOfSeatInvitation = ArrayList<String>() // objectId of seat Invitation
    private val objIdOfPKInvitation = ArrayList<String>() // objectId of pk Invitation
    private val objIdOfInteractionInfo = ArrayList<String>() // objectId of pk Invitation

    // pk competitor
    private val pKCompetitorInvitationList = ArrayList<ShowPKInvitation>()
    private val objIdOfPKCompetitorInvitation =
        ArrayList<String>() // objectId of pk competitor Invitation

    // current room cache data
    private var currRoomNo: String = ""
    private val sceneReferenceMap = mutableMapOf<String, SceneReference>()
    private val currEventListeners = mutableListOf<EventListener>()
    private val pkCompetitorEventListenerMap = mutableMapOf<String, EventListener>()

    private var currRoomChangeSubscriber: ((ShowServiceProtocol.ShowSubscribeStatus, ShowRoomDetailModel?) -> Unit)? =
        null
    private var currUserChangeSubscriber: ((ShowServiceProtocol.ShowSubscribeStatus, ShowUser?) -> Unit)? =
        null
    private var micSeatApplySubscriber: ((ShowServiceProtocol.ShowSubscribeStatus, ShowMicSeatApply?) -> Unit)? =
        null
    private var micSeatInvitationSubscriber: ((ShowServiceProtocol.ShowSubscribeStatus, ShowMicSeatInvitation?) -> Unit)? =
        null
    private var micPKInvitationSubscriber: ((ShowServiceProtocol.ShowSubscribeStatus, ShowPKInvitation?) -> Unit)? =
        null
    private var micInteractionInfoSubscriber: ((ShowServiceProtocol.ShowSubscribeStatus, ShowInteractionInfo?) -> Unit)? =
        null

    private var onReconnectSubscriber: (() -> Unit)? = null

    override fun destroy() {
        if (syncInitialized) {
            reset()
            roomMap.clear()
            Sync.Instance().destroy()
            syncInitialized = false
        }
    }

    private fun reset() {
        if (currRoomNo.isNotEmpty()) {
            sceneReferenceMap.remove(currRoomNo)?.let { sceneReference ->
                currEventListeners.forEach {
                    sceneReference.unsubscribe(it)
                }
            }
            currRoomNo = ""
        }

        objIdOfUserId.clear()
        objIdOfSeatApply.clear()
        objIdOfSeatInvitation.clear()
        objIdOfPKInvitation.clear()
        objIdOfInteractionInfo.clear()
        pKCompetitorInvitationList.clear()
        objIdOfPKCompetitorInvitation.clear()
        pkCompetitorEventListenerMap.clear()

        userList.clear()
        micSeatApplyList.clear()
        micSeatInvitationList.clear()
        pKInvitationList.clear()
        interactionInfoList.clear()

        currEventListeners.clear()

        currRoomChangeSubscriber = null
        currUserChangeSubscriber = null
        onReconnectSubscriber = null
        micInteractionInfoSubscriber = null
        micPKInvitationSubscriber = null
        micSeatInvitationSubscriber = null
        micSeatApplySubscriber = null
    }

    override fun getRoomList(
        success: (List<ShowRoomDetailModel>) -> Unit,
        error: ((Exception) -> Unit)?
    ) {
        val returnFakeData = true
        val fakeDataSize = 2

        initSync {
            Sync.Instance().getScenes(object : Sync.DataListCallback {
                override fun onSuccess(result: MutableList<IObject>?) {
                    workerExecutor.execute {
                        val roomList = result!!.map {
                            it.toObject(ShowRoomDetailModel::class.java)
                        }
                        roomMap.clear()
                        if (returnFakeData) {
                            // check if has fake data
                            val fakeList = roomList.filter { it.isFakeData }
                            if (fakeList.size < fakeDataSize) {
                                val createFakeCount = fakeDataSize - fakeList.size

                                val letchCount = CountDownLatch(createFakeCount)

                                runOnMainThread {
                                    for (i in 0 until createFakeCount) {
                                        val channelName =
                                            Random(System.currentTimeMillis()).nextInt(10000) + 1000000 * (i + 1)
                                        val uid =
                                            Random(System.currentTimeMillis()).nextInt(10000) + 1000000 * (i + 1)
                                        val streamUrl =
                                            "https://download.agora.io/sdk/release/agora_test_video_${
                                                Random.nextInt(
                                                    1,
                                                    5
                                                )
                                            }.mp4"
                                        val streamRegion = "cn"
                                        cloudPlayerService.startCloudPlayer(channelName.toString(),
                                            uid.toString(),
                                            streamUrl,
                                            streamRegion,
                                            success = {
                                                createRoomInner(
                                                    channelName.toString(),
                                                    channelName.toString(),
                                                    "1",
                                                    uid.toString(),
                                                    true,
                                                    success = { letchCount.countDown() },
                                                    error = { letchCount.countDown() }
                                                )
                                            },
                                            failure = { letchCount.countDown() })
                                    }
                                }

                                try {
                                    letchCount.await()
                                } catch (e: Exception) {
                                    ShowLogger.e(TAG, e)
                                }
                            }
                        }
                        val retList = mutableListOf<ShowRoomDetailModel>()
                        roomMap.values.forEach { retList.add(it) }
                        retList.addAll(roomList)
                        roomList.forEach { roomMap[it.roomId] = it.copy() }
                        val sortedBy = retList.sortedBy { it.createdAt }
                        runOnMainThread{ success.invoke(sortedBy) }
                    }
                }

                override fun onFail(exception: SyncManagerException?) {
                    error?.invoke(exception!!) ?: errorHandler.invoke(exception!!)
                }
            })
        }
    }

    override fun createRoom(
        roomId: String,
        roomName: String,
        thumbnailId: String,
        success: (ShowRoomDetailModel) -> Unit,
        error: ((Exception) -> Unit)?
    ) {
        initSync {
            createRoomInner(roomId,roomName,thumbnailId, UserManager.getInstance().user.id.toString(), false, success, error)
        }
    }

    private fun createRoomInner(
        roomId: String,
        roomName: String,
        thumbnailId: String,
        uid: String,
        isFakeData: Boolean,
        success: (ShowRoomDetailModel) -> Unit,
        error: ((Exception) -> Unit)?
    ) {
        val roomDetail = ShowRoomDetailModel(
            roomId,
            roomName,
            0,
            thumbnailId,
            uid,
            UserManager.getInstance().user.headUrl,
            UserManager.getInstance().user.name,
            ShowRoomStatus.activity.value,
            ShowInteractionStatus.idle.value,
            TimeUtils.currentTimeMillis().toDouble(),
            TimeUtils.currentTimeMillis().toDouble(),
            isFakeData
        )

        val scene = Scene().apply {
            id = roomDetail.roomId
            userId = roomDetail.ownerId
            property = roomDetail.toMap()
        }
        Sync.Instance().createScene(
            scene,
            object : Sync.Callback {
                override fun onSuccess() {
                    roomMap[roomDetail.roomId] = roomDetail.copy()
                    success.invoke(roomDetail)
                }

                override fun onFail(exception: SyncManagerException?) {
                    errorHandler.invoke(exception!!)
                    error?.invoke(exception)
                }
            })
    }

    override fun joinRoom(
        roomNo: String,
        success: (ShowRoomDetailModel) -> Unit,
        error: ((Exception) -> Unit)?
    ) {
        if (currRoomNo.isNotEmpty()) {
            error?.invoke(RuntimeException("There is a room joined or joining now!"))
            return
        }
        if (roomMap[roomNo] == null) {
            error?.invoke(RuntimeException("The room has been destroyed!"))
            return
        }
        currRoomNo = roomNo
        val roomInfo = roomMap[roomNo] ?: return
        initSync {
            Sync.Instance().joinScene(
                roomNo, object : Sync.JoinSceneCallback {
                    override fun onSuccess(sceneReference: SceneReference?) {
                        //this@ShowSyncManagerServiceImpl.currSceneReference = sceneReference!!
                        sceneReferenceMap[roomNo] = sceneReference!!
                        innerSubscribeUserChange()
                        innerMayAddLocalUser({
                            success.invoke(roomInfo)
                        }, {
                            error?.invoke(it) ?: errorHandler.invoke(it)
                            currRoomNo = ""
                        })
                        innerSubscribeSeatApplyChanged()
                        innerSubscribeInteractionChanged()
                        innerSubscribePKInvitationChanged(currRoomNo)
                        if (roomInfo.isFakeData) {
                            cloudPlayerService.startHeartBeat(
                                roomNo,
                                UserManager.getInstance().user.id.toString()
                            )
                        }
                    }

                    override fun onFail(exception: SyncManagerException?) {
                        error?.invoke(exception!!) ?: errorHandler.invoke(exception!!)
                        currRoomNo = ""
                    }
                }
            )
        }
    }

    override fun leaveRoom() {
        if (currRoomNo.isEmpty()) {
            return
        }
        val roomDetail = roomMap[currRoomNo]
        val sceneReference = sceneReferenceMap[currRoomNo]
        if (roomDetail == null || sceneReference == null) {
            reset()
            return
        }

        if (roomDetail.isFakeData) {
            cloudPlayerService.stopHeartBeat(currRoomNo)
        }

        // 移除连麦申请
        val targetApply =
            micSeatApplyList.filter { it.userId == UserManager.getInstance().user.id.toString() }
                .getOrNull(0)
        if (targetApply != null) {
            val indexOf = micSeatApplyList.indexOf(targetApply)
            micSeatApplyList.removeAt(indexOf)
            val removedSeatApplyObjId = objIdOfSeatApply.removeAt(indexOf)
            innerRemoveSeatApply(removedSeatApplyObjId, null, null)
        }

        // 移除pk申请
        pKCompetitorInvitationList.forEach {
            val index = pKCompetitorInvitationList.indexOf(it)
            innerRemovePKInvitation(it.roomId, objIdOfPKCompetitorInvitation[index], null, null)
        }
        pKCompetitorInvitationList.clear()

        innerRemoveUser(
            UserManager.getInstance().user.id.toString(),
            {},
            { errorHandler.invoke(it) }
        )

        Log.d(
            TAG,
            "leaveRoom roomNo=${currRoomNo} ownerId=${roomDetail.ownerId} myId=${UserManager.getInstance().user.id.toString()}"
        )
        if (roomDetail.ownerId == UserManager.getInstance().user.id.toString()) {
            Log.d(TAG, "leaveRoom delete room")

            val roomNo = currRoomNo
            sceneReference.delete(object : Sync.Callback {
                override fun onSuccess() {
                    roomMap.remove(roomNo)
                }

                override fun onFail(exception: SyncManagerException?) {
                    errorHandler.invoke(exception!!)
                }
            })
        }

        reset()
    }

    override fun subscribeCurrRoomEvent(onUpdate: (status: ShowServiceProtocol.ShowSubscribeStatus, roomInfo: ShowRoomDetailModel?) -> Unit) {
        currRoomChangeSubscriber = onUpdate
    }

    override fun getAllUserList(success: (List<ShowUser>) -> Unit, error: ((Exception) -> Unit)?) {
        innerGetUserList(
            {
                if (roomMap[currRoomNo]?.ownerId == UserManager.getInstance().user.id.toString()) {
                    innerUpdateRoomUserCount(userList.size, {}, {})
                }
                success.invoke(it)
            },
            {
                error?.invoke(it) ?: errorHandler.invoke(it)
            })
    }

    override fun subscribeUser(onUserChange: (ShowServiceProtocol.ShowSubscribeStatus, ShowUser?) -> Unit) {
        currUserChangeSubscriber = onUserChange
    }

    override fun sendChatMessage(
        message: String,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        val sceneReference = sceneReferenceMap[currRoomNo] ?: return
        sceneReference.collection(kCollectionIdMessage)
            .add(ShowMessage(
                UserManager.getInstance().user.id.toString(),
                UserManager.getInstance().user.name,
                message,
                TimeUtils.currentTimeMillis().toDouble()
            ), object : Sync.DataItemCallback {
                override fun onSuccess(result: IObject?) {
                    success?.invoke()
                }

                override fun onFail(exception: SyncManagerException?) {
                    error?.invoke(exception!!) ?: errorHandler.invoke(exception!!)
                }
            })
    }

    override fun subscribeMessage(onMessageChange: (ShowServiceProtocol.ShowSubscribeStatus, ShowMessage) -> Unit) {
        val sceneReference = sceneReferenceMap[currRoomNo] ?: return
        val listener = object : EventListener {
            override fun onCreated(item: IObject?) {
                // do nothing
            }

            override fun onUpdated(item: IObject?) {
                item ?: return
                onMessageChange.invoke(
                    ShowServiceProtocol.ShowSubscribeStatus.updated,
                    item.toObject(ShowMessage::class.java)
                )
            }

            override fun onDeleted(item: IObject?) {

            }

            override fun onSubscribeError(ex: SyncManagerException?) {
                errorHandler.invoke(ex!!)
            }
        }
        currEventListeners.add(listener)
        sceneReference.collection(kCollectionIdMessage)
            .subscribe(listener)
    }

    override fun getAllMicSeatApplyList(
        success: (List<ShowMicSeatApply>) -> Unit,
        error: ((Exception) -> Unit)?
    ) {
        innerGetSeatApplyList(success, error)
    }

    override fun subscribeMicSeatApply(onMicSeatChange: (ShowServiceProtocol.ShowSubscribeStatus, ShowMicSeatApply?) -> Unit) {
        micSeatApplySubscriber = onMicSeatChange
    }

    override fun createMicSeatApply(success: (() -> Unit)?, error: ((Exception) -> Unit)?) {
        val targetApply =
            micSeatApplyList.filter { it.userId == UserManager.getInstance().user.id.toString() }
                .getOrNull(0)
        if (targetApply != null) {
            error?.invoke(RuntimeException("The seat apply found!"))
            return
        }
        val apply = ShowMicSeatApply(
            UserManager.getInstance().user.id.toString(),
            UserManager.getInstance().user.headUrl,
            UserManager.getInstance().user.name,
            ShowRoomRequestStatus.waitting.value,
            TimeUtils.currentTimeMillis().toDouble()
        )
        innerCreateSeatApply(apply, success, error)
    }

    override fun cancelMicSeatApply(success: (() -> Unit)?, error: ((Exception) -> Unit)?) {
        if (micSeatApplyList.size <= 0) {
            error?.invoke(RuntimeException("The seat apply list is empty!"))
            return
        }
        val targetApply =
            micSeatApplyList.filter { it.userId == UserManager.getInstance().user.id.toString() }
                .getOrNull(0)
        if (targetApply == null) {
            error?.invoke(RuntimeException("The seat apply not found!"))
            return
        }

        val indexOf = micSeatApplyList.indexOf(targetApply)
        micSeatApplyList.removeAt(indexOf)
        val removedSeatApplyObjId = objIdOfSeatApply.removeAt(indexOf)

        innerRemoveSeatApply(removedSeatApplyObjId, success, error)
    }

    override fun acceptMicSeatApply(
        apply: ShowMicSeatApply,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        if (micSeatApplyList.size <= 0) {
            error?.invoke(RuntimeException("The seat apply list is empty!"))
            return
        }
        val targetApply = micSeatApplyList.filter { it.userId == apply.userId }.getOrNull(0)
        if (targetApply == null) {
            error?.invoke(RuntimeException("The seat apply found!"))
            return
        }

        val indexOf = micSeatApplyList.indexOf(targetApply)
        micSeatApplyList.removeAt(indexOf)
        val removedSeatApplyObjId = objIdOfSeatApply.removeAt(indexOf)
        innerRemoveSeatApply(removedSeatApplyObjId, success, error)

        val interaction = ShowInteractionInfo(
            apply.userId,
            apply.userName,
            currRoomNo,
            ShowInteractionStatus.onSeat.value,
            muteAudio = false,
            ownerMuteAudio = false,
            createdAt = apply.createAt
        )
        innerCreateInteraction(interaction, null, null)
    }

    override fun rejectMicSeatApply(
        apply: ShowMicSeatApply,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        if (micSeatApplyList.size <= 0) {
            error?.invoke(RuntimeException("The seat apply list is empty!"))
            return
        }
        val targetApply = micSeatApplyList.filter { it.userId == apply.userId }.getOrNull(0)
        if (targetApply == null) {
            error?.invoke(RuntimeException("The seat apply found!"))
            return
        }

        val seatApply = ShowMicSeatApply(
            targetApply.userId,
            targetApply.avatar,
            targetApply.userName,
            ShowRoomRequestStatus.rejected.value,
            targetApply.createAt
        )
        val indexOf = micSeatApplyList.indexOf(targetApply)
        micSeatApplyList[indexOf] = seatApply
        innerUpdateSeatApply(objIdOfSeatApply[indexOf], seatApply, success, error)
    }

    override fun getAllMicSeatInvitationList(
        success: (List<ShowMicSeatInvitation>) -> Unit,
        error: ((Exception) -> Unit)?
    ) {

    }

    override fun subscribeMicSeatInvitation(onMicSeatInvitationChange: (ShowServiceProtocol.ShowSubscribeStatus, ShowMicSeatInvitation?) -> Unit) {
        micSeatInvitationSubscriber = onMicSeatInvitationChange
    }

    override fun createMicSeatInvitation(
        user: ShowUser,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        val userItem = ShowUser(
            user.userId,
            user.avatar,
            user.userName,
            ShowRoomRequestStatus.waitting.value,
        )
        innerUpdateUserRoomRequestStatus(userItem, {}, {})
    }

    override fun cancelMicSeatInvitation(
        userId: String,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {

    }

    override fun acceptMicSeatInvitation(
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        if (userList.size <= 0) {
            error?.invoke(RuntimeException("The seat invitation list is empty!"))
            return
        }
        val user = userList.filter { it.userId == UserManager.getInstance().user.id.toString() }
            .getOrNull(0)
        if (user == null) {
            error?.invoke(RuntimeException("The seat invitation found!"))
            return
        }
        val userItem = ShowUser(
            user.userId,
            user.avatar,
            user.userName,
            ShowRoomRequestStatus.accepted.value,
        )
        innerUpdateUserRoomRequestStatus(userItem, {}, {})

        val interaction = ShowInteractionInfo(
            user.userId,
            user.userName,
            currRoomNo,
            ShowInteractionStatus.onSeat.value,
            muteAudio = false,
            ownerMuteAudio = false,
            createdAt = 0.0 //TODO
        )
        innerCreateInteraction(interaction, { }, { })
    }

    override fun rejectMicSeatInvitation(
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        if (userList.size <= 0) {
            error?.invoke(RuntimeException("The seat invitation list is empty!"))
            return
        }
        val user = userList.filter { it.userId == UserManager.getInstance().user.id.toString() }
            .getOrNull(0)
        if (user == null) {
            error?.invoke(RuntimeException("The seat invitation found!"))
            return
        }
        val userItem = ShowUser(
            user.userId,
            user.avatar,
            user.userName,
            ShowRoomRequestStatus.idle.value,
        )
        innerUpdateUserRoomRequestStatus(userItem, {}, {})
    }

    override fun getAllPKUserList(
        success: ((List<ShowRoomDetailModel>) -> Unit),
        error: ((Exception) -> Unit)?
    ) {
        Sync.Instance().getScenes(object : Sync.DataListCallback {
            override fun onSuccess(result: MutableList<IObject>?) {
                val roomList = result!!.map {
                    it.toObject(ShowRoomDetailModel::class.java)
                }
                val list =
                    roomList.filter { it.ownerId != UserManager.getInstance().user.id.toString() }
                runOnMainThread { success.invoke(list.sortedBy { it.createdAt }) }
            }

            override fun onFail(exception: SyncManagerException?) {
                error?.invoke(exception!!) ?: errorHandler.invoke(exception!!)
            }
        })
    }

    override fun getAllPKInvitationList(
        isFromUser: Boolean,
        success: (List<ShowPKInvitation>) -> Unit,
        error: ((Exception) -> Unit)?
    ) {
        if (isFromUser) {
            success.invoke(pKCompetitorInvitationList)
        } else {
            innerGetPKInvitationList(null, success, error)
        }
    }

    override fun subscribePKInvitationChanged(onPKInvitationChanged: (ShowServiceProtocol.ShowSubscribeStatus, ShowPKInvitation?) -> Unit) {
        micPKInvitationSubscriber = onPKInvitationChanged
    }

    override fun createPKInvitation(
        room: ShowRoomDetailModel,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        if (interactionInfoList.size > 0) {
            error?.invoke(RuntimeException("InteractionInfoList is not empty, stop interacting first!"))
        }
        innerGetPKInvitationList(room, {
            val invitation = it.filter { it.roomId == room.roomId }.getOrNull(0)
            if (invitation == null) {
                val pkInvitation = ShowPKInvitation(
                    room.ownerId,
                    room.ownerName,
                    room.roomId,
                    UserManager.getInstance().user.id.toString(),
                    UserManager.getInstance().user.name,
                    currRoomNo,
                    ShowRoomRequestStatus.waitting.value,
                    userMuteAudio = false,
                    fromUserMuteAudio = false,
                    createAt = TimeUtils.currentTimeMillis().toDouble()
                )
                innerCreatePKInvitation(pkInvitation, null, null)
            }
        }, null)
    }

    override fun acceptPKInvitation(success: (() -> Unit)?, error: ((Exception) -> Unit)?) {
        if (pKInvitationList.size <= 0) {
            error?.invoke(RuntimeException("The seat invitation list is empty!"))
            return
        }
        val targetInvitation =
            pKInvitationList.filter { it.userId == UserManager.getInstance().user.id.toString() }
                .getOrNull(0)
        if (targetInvitation == null) {
            error?.invoke(RuntimeException("The seat invitation found!"))
            return
        }

        val invitation = ShowPKInvitation(
            targetInvitation.userId,
            targetInvitation.userName,
            currRoomNo,
            targetInvitation.fromUserId,
            targetInvitation.fromName,
            targetInvitation.fromRoomId,
            ShowRoomRequestStatus.accepted.value,
            userMuteAudio = false,
            fromUserMuteAudio = false,
            createAt = targetInvitation.createAt
        )

        val indexOf = pKInvitationList.indexOf(targetInvitation)
        pKInvitationList[indexOf] = invitation
        innerUpdatePKInvitation(
            currRoomNo,
            objIdOfPKInvitation[indexOf],
            invitation,
            success,
            error
        )

        val interaction = ShowInteractionInfo(
            invitation.fromUserId,
            invitation.fromName,
            invitation.fromRoomId,
            ShowInteractionStatus.pking.value,
            muteAudio = false,
            ownerMuteAudio = false,
            createdAt = invitation.createAt
        )
        innerCreateInteraction(interaction, null, null)
    }

    override fun rejectPKInvitation(success: (() -> Unit)?, error: ((Exception) -> Unit)?) {
        if (pKInvitationList.size <= 0) {
            error?.invoke(RuntimeException("The seat invitation list is empty!"))
            return
        }
        val targetInvitation =
            pKInvitationList.filter { it.userId == UserManager.getInstance().user.id.toString() }
                .getOrNull(0)
        if (targetInvitation == null) {
            error?.invoke(RuntimeException("The seat invitation found!"))
            return
        }

        val indexOf = pKInvitationList.indexOf(targetInvitation)
        pKInvitationList.removeAt(indexOf)
        val removedObjId = objIdOfPKInvitation.removeAt(indexOf)
        innerRemovePKInvitation(currRoomNo, removedObjId, success, error)
    }

    override fun getAllInterationList(
        success: ((List<ShowInteractionInfo>) -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        innerGetAllInteractionList(success, error)
    }

    override fun subscribeInteractionChanged(onInteractionChanged: (ShowServiceProtocol.ShowSubscribeStatus, ShowInteractionInfo?) -> Unit) {
        micInteractionInfoSubscriber = onInteractionChanged
    }

    override fun stopInteraction(
        interaction: ShowInteractionInfo,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        if (interactionInfoList.size <= 0) {
            error?.invoke(RuntimeException("The interaction list is empty!"))
            return
        }
        val targetInvitation =
            interactionInfoList.filter { it.userId == interaction.userId }.getOrNull(0)
        if (targetInvitation == null) {
            error?.invoke(RuntimeException("The interaction not found!"))
            return
        }

        innerGetAllInteractionList({
            objIdOfInteractionInfo.forEach { innerRemoveInteraction(it, success, error) }
        }, null)

        val apply = micSeatApplyList.filter { it.userId == interaction.userId }.getOrNull(0)
        if (apply != null) {
            // 停止连麦者 移除连麦申请
            val index = micSeatApplyList.indexOf(apply)
            innerRemoveSeatApply(objIdOfSeatApply[index], {}, {})
        }

        // pk
        if (interaction.interactStatus == ShowInteractionStatus.pking.value) {
            if (pKCompetitorInvitationList.isEmpty()) {
                // pk 对象
                val invitation =
                    pKInvitationList.filter { it.fromUserId == interaction.userId }.getOrNull(0)
                if (invitation != null) {
                    val index = pKInvitationList.indexOf(invitation)
                    pKInvitationList.removeAt(index)
                    val objId = objIdOfPKInvitation.removeAt(index)
                    innerRemovePKInvitation(currRoomNo, objId, null, null)
                }
            } else {
                // pk 发起者
                val invitation =
                    pKCompetitorInvitationList.filter { it.userId == interaction.userId }
                        .getOrNull(0)
                if (invitation != null) {
                    val index = pKCompetitorInvitationList.indexOf(invitation)
                    pKCompetitorInvitationList.removeAt(index)
                    val objId = objIdOfPKCompetitorInvitation.removeAt(index)
                    innerRemovePKInvitation(invitation.roomId, objId, null, null)
                }
            }

        }

        val user = userList.filter { it.userId == interaction.userId }.getOrNull(0)
        if (user != null && user.status != ShowRoomRequestStatus.idle.value) {
            val userItem = ShowUser(
                user.userId,
                user.avatar,
                user.userName,
                ShowRoomRequestStatus.idle.value,
            )
            innerUpdateUserRoomRequestStatus(userItem, {}, {})
        }
    }

    override fun muteAudio(
        mute: Boolean,
        userId: String,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        // 连麦
        val oldInteraction = interactionInfoList.filter { it.userId == userId }.getOrNull(0)
        if (oldInteraction != null) {
            val indexOf = interactionInfoList.indexOf(oldInteraction)
            val objId = objIdOfInteractionInfo[indexOf]

            val interaction = ShowInteractionInfo(
                oldInteraction.userId,
                oldInteraction.userName,
                oldInteraction.roomId,
                oldInteraction.interactStatus,
                mute,
                oldInteraction.ownerMuteAudio,
                oldInteraction.createdAt
            )
            innerUpdateInteraction(objId, interaction, null, null)
        }

        // pk
        if (pKCompetitorInvitationList.isEmpty()) {
            // pk 对象
            val invitation = pKInvitationList.filter { it.userId == userId }.getOrNull(0)
            if (invitation != null) {
                val index = pKInvitationList.indexOf(invitation)
                val invitation = ShowPKInvitation(
                    invitation.userId,
                    invitation.userName,
                    invitation.roomId,
                    invitation.fromUserId,
                    invitation.fromName,
                    invitation.fromRoomId,
                    invitation.status,
                    mute,
                    invitation.fromUserMuteAudio,
                    invitation.createAt
                )
                val objId = objIdOfPKInvitation[index]
                innerUpdatePKInvitation(currRoomNo, objId, invitation, null, null)
            }
        } else {
            // pk 发起者
            val invitation =
                pKCompetitorInvitationList.filter { it.fromUserId == userId }.getOrNull(0)
            if (invitation != null) {
                val index = pKCompetitorInvitationList.indexOf(invitation)
                val objId = objIdOfPKCompetitorInvitation[index]
                val invitation = ShowPKInvitation(
                    invitation.userId,
                    invitation.userName,
                    invitation.roomId,
                    invitation.fromUserId,
                    invitation.fromName,
                    invitation.fromRoomId,
                    invitation.status,
                    invitation.userMuteAudio,
                    mute,
                    invitation.createAt
                )
                innerUpdatePKInvitation(invitation.roomId, objId, invitation, null, null)
            }
        }
    }

    override fun subscribeReConnectEvent(onReconnect: () -> Unit) {
        onReconnectSubscriber = onReconnect
    }

    // =================================== 内部实现 ===================================
    private fun runOnMainThread(r: Runnable) {
        if (Thread.currentThread() == mainHandler.looper.thread) {
            r.run()
        } else {
            mainHandler.post(r)
        }
    }

    private fun initSync(complete: () -> Unit) {
        if (syncInitialized) {
            complete.invoke()
            return
        }
        syncInitialized = true
        Sync.Instance().init(
            context,
            mutableMapOf(Pair("appid", BuildConfig.AGORA_APP_ID), Pair("defaultChannel", kSceneId)),
            object : Sync.Callback {
                override fun onSuccess() {
                    runOnMainThread { complete.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    syncInitialized = false
                    errorHandler.invoke(exception!!)
                }
            }
        )
        Sync.Instance().joinScene(kSceneId, object : Sync.JoinSceneCallback {
            override fun onSuccess(sceneReference: SceneReference?) {
                sceneReference?.subscribe(object : EventListener {
                    override fun onCreated(item: IObject?) {

                    }

                    override fun onUpdated(item: IObject?) {
                        item ?: return
                        val roomInfo = item.toObject(ShowRoomDetailModel::class.java)
                        roomMap[item.id] = roomInfo
                        Log.d(TAG, "Sync Room Update roomNo=${item.id}, roomInfo=${roomInfo}")
                        if (currRoomNo.isNotEmpty()) {
                            runOnMainThread {
                                currRoomChangeSubscriber?.invoke(
                                    ShowServiceProtocol.ShowSubscribeStatus.updated,
                                    roomInfo
                                )
                            }
                        }
                    }

                    override fun onDeleted(item: IObject?) {
                        item ?: return
                        val roomInfo = roomMap.remove(item.id)
                        Log.d(TAG, "Sync Room Delete roomNo=${item.id}")
                        if (currRoomNo.isNotEmpty() && currRoomNo == item.id) {
                            runOnMainThread {
                                currRoomChangeSubscriber?.invoke(
                                    ShowServiceProtocol.ShowSubscribeStatus.deleted,
                                    roomInfo
                                )
                            }
                        }
                    }

                    override fun onSubscribeError(ex: SyncManagerException?) {
                        errorHandler.invoke(ex!!)
                    }
                })
            }

            override fun onFail(exception: SyncManagerException?) {
                errorHandler.invoke(exception!!)
            }

        })
        Sync.Instance().subscribeConnectState {
            Log.d(TAG, "subscribeConnectState state=$it")
            if (it == Sync.ConnectionState.open) {
                runOnMainThread {
                    // 判断当前房间是否还存在
                    val oldRoomInfo = roomMap[currRoomNo]
                    if (oldRoomInfo != null) {
                        getRoomList({
                            val roomInfo = roomMap[currRoomNo]
                            if (roomInfo == null) {
                                runOnMainThread {
                                    currRoomChangeSubscriber?.invoke(
                                        ShowServiceProtocol.ShowSubscribeStatus.deleted,
                                        oldRoomInfo
                                    )
                                }
                            }
                        })
                    }
                    onReconnectSubscriber?.invoke()
                }
            }
        }
    }

    private fun innerUpdateRoomInteractStatus(
        interactStatus: Int,
        success: () -> Unit,
        error: (Exception) -> Unit
    ) {
        val roomInfo = roomMap[currRoomNo] ?: return
        val sceneReference = sceneReferenceMap[currRoomNo] ?: return

        val nRoomInfo = ShowRoomDetailModel(
            roomInfo.roomId,
            roomInfo.roomName,
            roomInfo.roomUserCount,
            roomInfo.thumbnailId,
            roomInfo.ownerId,
            roomInfo.ownerAvatar,
            roomInfo.ownerName,
            roomInfo.roomStatus,
            interactStatus,
            roomInfo.createdAt,
            roomInfo.updatedAt,
            roomInfo.isFakeData
        )
        sceneReference.update(nRoomInfo.toMap(), object : Sync.DataItemCallback {
            override fun onSuccess(result: IObject?) {
                roomMap[currRoomNo] = nRoomInfo
                success.invoke()
            }

            override fun onFail(exception: SyncManagerException?) {
                error.invoke(exception!!)
            }
        })
    }

    private fun innerUpdateRoomUserCount(
        userCount: Int,
        success: () -> Unit,
        error: (Exception) -> Unit
    ) {
        val roomInfo = roomMap[currRoomNo] ?: return
        val sceneReference = sceneReferenceMap[currRoomNo] ?: return

        val nRoomInfo = ShowRoomDetailModel(
            roomInfo.roomId,
            roomInfo.roomName,
            userCount,
            roomInfo.thumbnailId,
            roomInfo.ownerId,
            roomInfo.ownerAvatar,
            roomInfo.ownerName,
            roomInfo.roomStatus,
            roomInfo.interactStatus,
            roomInfo.createdAt,
            roomInfo.updatedAt,
            roomInfo.isFakeData
        )
        sceneReference.update(nRoomInfo.toMap(), object : Sync.DataItemCallback {
            override fun onSuccess(result: IObject?) {
                roomMap[currRoomNo] = nRoomInfo
                success.invoke()
            }

            override fun onFail(exception: SyncManagerException?) {
                error.invoke(exception!!)
            }
        })
    }

    private fun innerMayAddLocalUser(success: () -> Unit, error: (Exception) -> Unit) {
        val userId = UserManager.getInstance().user.id.toString()
        val avatarUrl = UserManager.getInstance().user.headUrl
        innerGetUserList({ list ->
            if (list.none { it.userId == it.toString() }) {
                innerAddUser(ShowUser(userId, avatarUrl, UserManager.getInstance().user.name),
                    {
                        objIdOfUserId[userId] = it
                        innerUpdateRoomUserCount(list.size + 1, {
                            success.invoke()
                        }, { ex ->
                            error.invoke(ex)
                        })
                    },
                    {
                        error.invoke(it)
                    })
            } else {
                success.invoke()
            }
        }, {
            error.invoke(it)
        })
    }

    private fun innerGetUserList(success: (List<ShowUser>) -> Unit, error: (Exception) -> Unit) {
        val sceneReference = sceneReferenceMap[currRoomNo] ?: return
        sceneReference.collection(kCollectionIdUser)
            .get(object : Sync.DataListCallback {
                override fun onSuccess(result: MutableList<IObject>?) {
                    result ?: return
                    val map = result.map { it.toObject(ShowUser::class.java) }
                    userList.clear()

                    val ret = ArrayList<ShowUser>()
                    result.forEach {
                        val obj = it.toObject(ShowUser::class.java)
                        objIdOfUserId[obj.userId] = it.id
                        ret.add(obj)
                    }
                    userList.addAll(ret)
                    runOnMainThread {
                        success.invoke(map)
                    }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error.invoke(exception!!) }
                }
            })
    }

    private fun innerAddUser(
        user: ShowUser,
        success: (String) -> Unit,
        error: (Exception) -> Unit
    ) {
        val sceneReference = sceneReferenceMap[currRoomNo] ?: return
        sceneReference.collection(kCollectionIdUser)
            .add(user, object : Sync.DataItemCallback {
                override fun onSuccess(result: IObject?) {
                    success.invoke(result?.id!!)
                }

                override fun onFail(exception: SyncManagerException?) {
                    error.invoke(exception!!)
                }
            })
    }

    private fun innerRemoveUser(
        userId: String,
        success: () -> Unit,
        error: (Exception) -> Unit
    ) {
        val sceneReference = sceneReferenceMap[currRoomNo] ?: return
        val objectId = objIdOfUserId[userId] ?: return
        sceneReference.collection(kCollectionIdUser)
            .delete(objectId, object : Sync.Callback {
                override fun onSuccess() {
                    success.invoke()
                }

                override fun onFail(exception: SyncManagerException?) {
                    error.invoke(exception!!)
                }
            })
    }

    private fun innerUpdateUserRoomRequestStatus(
        user: ShowUser,
        success: () -> Unit,
        error: (Exception) -> Unit
    ) {
        val objectId = objIdOfUserId[user.userId] ?: return
        sceneReferenceMap[currRoomNo]?.collection(kCollectionIdUser)
            ?.update(objectId, user, object : Sync.Callback {
                override fun onSuccess() {
                    runOnMainThread { success.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error.invoke(exception!!) }
                }
            })
    }

    private fun innerSubscribeUserChange() {
        val sceneReference = sceneReferenceMap[currRoomNo] ?: return
        val listener = object : EventListener {
            override fun onCreated(item: IObject?) {
                // do nothing
            }

            override fun onUpdated(item: IObject?) {
                val user = item?.toObject(ShowUser::class.java) ?: return
                objIdOfUserId[user.userId] = item.id


                val list = micSeatInvitationList.filter { it.userId == user.userId }
                if (list.isEmpty()) {
                    micSeatInvitationList.add(user)
                    objIdOfSeatInvitation.add(item.id)
                } else {
                    val indexOf = micSeatInvitationList.indexOf(list[0])
                    micSeatInvitationList[indexOf] = user
                    objIdOfSeatInvitation[indexOf] = item.id
                }
                runOnMainThread {
                    currUserChangeSubscriber?.invoke(
                        ShowServiceProtocol.ShowSubscribeStatus.updated,
                        user
                    )
                }

            }

            override fun onDeleted(item: IObject?) {
                val userId =
                    objIdOfUserId.filterValues { it == item?.id }.entries.firstOrNull()?.key
                        ?: return
                val userInfo = userList.filter { it.userId == userId }.getOrNull(0) ?: return
                userList.remove(userInfo)
                runOnMainThread {
                    currUserChangeSubscriber?.invoke(
                        ShowServiceProtocol.ShowSubscribeStatus.deleted,
                        userInfo
                    )
                }
            }

            override fun onSubscribeError(ex: SyncManagerException?) {
                errorHandler.invoke(ex!!)
            }
        }
        currEventListeners.add(listener)
        sceneReference.collection(kCollectionIdUser)
            .subscribe(listener)
    }

    // ----------------------------------- 连麦申请 -----------------------------------
    private fun innerGetSeatApplyList(
        success: (List<ShowMicSeatApply>) -> Unit,
        error: ((Exception) -> Unit)?
    ) {
        sceneReferenceMap[currRoomNo]?.collection(kCollectionIdSeatApply)?.get(object :
            Sync.DataListCallback {
            override fun onSuccess(result: MutableList<IObject>?) {
                val ret = ArrayList<ShowMicSeatApply>()
                val retObjId = ArrayList<String>()
                result?.forEach {
                    val obj = it.toObject(ShowMicSeatApply::class.java)
                    ret.add(obj)
                    retObjId.add(it.id)
                }
                micSeatApplyList.clear()
                micSeatApplyList.addAll(ret)
                objIdOfSeatApply.clear()
                objIdOfSeatApply.addAll(retObjId)

                //按照创建时间顺序排序
                //ret.sortBy { it.createdAt }
                runOnMainThread { success.invoke(ret) }
            }

            override fun onFail(exception: SyncManagerException?) {
                runOnMainThread { error?.invoke(exception!!) }
            }
        })
    }

    private fun innerCreateSeatApply(
        seatApply: ShowMicSeatApply,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        sceneReferenceMap[currRoomNo]?.collection(kCollectionIdSeatApply)
            ?.add(seatApply, object : Sync.DataItemCallback {
                override fun onSuccess(result: IObject) {
                    //micSeatApplyList.add(seatApply)
                    runOnMainThread { success?.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerUpdateSeatApply(
        objectId: String,
        seatApply: ShowMicSeatApply,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        sceneReferenceMap[currRoomNo]?.collection(kCollectionIdSeatApply)
            ?.update(objectId, seatApply, object : Sync.Callback {
                override fun onSuccess() {
                    runOnMainThread { success?.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerRemoveSeatApply(
        objectId: String,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        sceneReferenceMap[currRoomNo]?.collection(kCollectionIdSeatApply)
            ?.delete(objectId, object : Sync.Callback {
                override fun onSuccess() {
                    runOnMainThread { success?.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerSubscribeSeatApplyChanged() {
        val sceneReference = sceneReferenceMap[currRoomNo] ?: return
        val listener = object : EventListener {
            override fun onCreated(item: IObject?) {
                // do Nothing
            }

            override fun onUpdated(item: IObject?) {
                val info = item?.toObject(ShowMicSeatApply::class.java) ?: return
                val list = micSeatApplyList.filter { it.userId == info.userId }
                if (list.isEmpty()) {
                    micSeatApplyList.add(info)
                    objIdOfSeatApply.add(item.id)
                } else {
                    val indexOf = micSeatApplyList.indexOf(list[0])
                    micSeatApplyList[indexOf] = info
                    objIdOfSeatApply[indexOf] = item.id
                }
                runOnMainThread {
                    micSeatApplySubscriber?.invoke(
                        ShowServiceProtocol.ShowSubscribeStatus.updated,
                        info
                    )
                }
            }

            override fun onDeleted(item: IObject?) {
                val info = item?.toObject(ShowMicSeatApply::class.java)
                if (info != null) {
                    val list = micSeatApplyList.filter { it.userId == info.userId }
                    if (list.isNotEmpty()) {
                        val indexOf = micSeatApplyList.indexOf(list[0])
                        micSeatApplyList.removeAt(indexOf)
                        objIdOfSeatApply.removeAt(indexOf)
                    }
                }
                runOnMainThread {
                    micSeatApplySubscriber?.invoke(
                        ShowServiceProtocol.ShowSubscribeStatus.deleted,
                        null
                    )
                }
            }

            override fun onSubscribeError(ex: SyncManagerException?) {
            }
        }
        currEventListeners.add(listener)
        sceneReference.collection(kCollectionIdSeatApply)
            .subscribe(listener)
    }

    // ----------------------------------- pk邀请 -----------------------------------
    private fun innerGetPKInvitationList(
        room: ShowRoomDetailModel?,
        success: (List<ShowPKInvitation>) -> Unit,
        error: ((Exception) -> Unit)?
    ) {
        if (room != null) {
            if (room.roomId == "") return
            val roomId = room.roomId
            if (roomId != currRoomNo) {
                initSync {
                    Sync.Instance().joinScene(
                        roomId, object : Sync.JoinSceneCallback {
                            override fun onSuccess(sceneReference: SceneReference?) {
                                sceneReferenceMap[roomId] = sceneReference!!
                                sceneReferenceMap[roomId]?.collection(kCollectionIdPKInvitation)
                                    ?.get(object : Sync.DataListCallback {
                                        override fun onSuccess(result: MutableList<IObject>?) {
                                            val ret = ArrayList<ShowPKInvitation>()
                                            val retObjId = ArrayList<String>()
                                            result?.forEach {
                                                val obj = it.toObject(ShowPKInvitation::class.java)
                                                ret.add(obj)
                                                retObjId.add(it.id)
                                            }
                                            innerSubscribeCompetitorPKInvitationChanged(roomId)
                                            runOnMainThread { success.invoke(ret) }
                                        }

                                        override fun onFail(exception: SyncManagerException?) {
                                            runOnMainThread { error?.invoke(exception!!) }
                                        }
                                    })
                            }

                            override fun onFail(exception: SyncManagerException?) {
                                error?.invoke(exception!!) ?: errorHandler.invoke(exception!!)
                            }
                        }
                    )
                }
                return
            }
        }

        sceneReferenceMap[currRoomNo]?.collection(kCollectionIdPKInvitation)
            ?.get(object : Sync.DataListCallback {
                override fun onSuccess(result: MutableList<IObject>?) {
                    val ret = ArrayList<ShowPKInvitation>()
                    val retObjId = ArrayList<String>()
                    result?.forEach {
                        val obj = it.toObject(ShowPKInvitation::class.java)
                        ret.add(obj)
                        retObjId.add(it.id)
                    }
                    pKInvitationList.clear()
                    pKInvitationList.addAll(ret)
                    objIdOfPKInvitation.clear()
                    objIdOfPKInvitation.addAll(retObjId)

                    runOnMainThread { success.invoke(ret) }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerCreatePKInvitation(
        pkInvitation: ShowPKInvitation,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        sceneReferenceMap[pkInvitation.roomId]?.collection(kCollectionIdPKInvitation)
            ?.add(pkInvitation, object : Sync.DataItemCallback {
                override fun onSuccess(result: IObject) {
                    runOnMainThread { success?.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerUpdatePKInvitation(
        roomId: String,
        objectId: String,
        pkInvitation: ShowPKInvitation,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        sceneReferenceMap[roomId]?.collection(kCollectionIdPKInvitation)
            ?.update(objectId, pkInvitation, object : Sync.Callback {
                override fun onSuccess() {
                    runOnMainThread { success?.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerRemovePKInvitation(
        roomId: String,
        objectId: String,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        sceneReferenceMap[roomId]?.collection(kCollectionIdPKInvitation)
            ?.delete(objectId, object : Sync.Callback {
                override fun onSuccess() {
                    runOnMainThread { success?.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerSubscribePKInvitationChanged(roomId: String) {
        val sceneReference = sceneReferenceMap[roomId] ?: return
        val listener = object : EventListener {
            override fun onCreated(item: IObject?) {
                // do Nothing
            }

            override fun onUpdated(item: IObject?) {
                val info = item?.toObject(ShowPKInvitation::class.java) ?: return
                // pk对象
                val list = pKInvitationList.filter { it.userId == info.userId }
                if (list.isEmpty()) {
                    pKInvitationList.add(info)
                    objIdOfPKInvitation.add(item.id)
                } else {
                    val indexOf = pKInvitationList.indexOf(list[0])
                    pKInvitationList[indexOf] = info
                    objIdOfPKInvitation[indexOf] = item.id
                }

                if (interactionInfoList.isNotEmpty()) {
                    val oldInteraction =
                        interactionInfoList.filter { it.userId == info.fromUserId }.getOrNull(0)
                    if (oldInteraction != null) {
                        val indexOf = interactionInfoList.indexOf(oldInteraction)
                        val objId = objIdOfInteractionInfo[indexOf]

                        val interaction = ShowInteractionInfo(
                            oldInteraction.userId,
                            oldInteraction.userName,
                            oldInteraction.roomId,
                            oldInteraction.interactStatus,
                            info.fromUserMuteAudio,
                            info.userMuteAudio,
                            oldInteraction.createdAt
                        )
                        innerUpdateInteraction(objId, interaction, null, null)
                    }
                }

                runOnMainThread {
                    micPKInvitationSubscriber?.invoke(
                        ShowServiceProtocol.ShowSubscribeStatus.updated,
                        info
                    )
                }
            }

            override fun onDeleted(item: IObject?) {
                val objId = item!!.id
                val index = objIdOfPKInvitation.indexOf(objId)
                objIdOfPKInvitation.removeAt(index)
                val invitation = pKInvitationList.removeAt(index)

                runOnMainThread {
                    micPKInvitationSubscriber?.invoke(
                        ShowServiceProtocol.ShowSubscribeStatus.deleted,
                        invitation
                    )
                }
            }

            override fun onSubscribeError(ex: SyncManagerException?) {
            }
        }
        currEventListeners.add(listener)
        sceneReference.collection(kCollectionIdPKInvitation).subscribe(listener)
    }

    private var isInteractionCreated = false // TODO workaround
    private fun innerSubscribeCompetitorPKInvitationChanged(roomId: String) {
        val sceneReference = sceneReferenceMap[roomId] ?: return
        val listener = object : EventListener {
            override fun onCreated(item: IObject?) {
                // do Nothing
            }

            override fun onUpdated(item: IObject?) {
                val info = item?.toObject(ShowPKInvitation::class.java) ?: return

                val acceptItem =
                    pKCompetitorInvitationList.filter { it.status == ShowRoomRequestStatus.accepted.value }
                        .getOrNull(0)
                if (acceptItem != null && acceptItem.userId != info.userId && info.status == ShowRoomRequestStatus.accepted.value) {
                    // 已有其他主播接受， 删除PK邀请
                    innerRemovePKInvitation(info.roomId, item.id, null, null)
                    return
                }

                val list = pKCompetitorInvitationList.filter { it.userId == info.userId }
                if (list.isEmpty()) {
                    pKCompetitorInvitationList.add(info)
                    objIdOfPKCompetitorInvitation.add(item.id)
                } else {
                    val indexOf = pKCompetitorInvitationList.indexOf(list[0])
                    pKCompetitorInvitationList[indexOf] = info
                    objIdOfPKCompetitorInvitation[indexOf] = item.id
                }

                if (interactionInfoList.isEmpty() && info.status == ShowRoomRequestStatus.accepted.value && !isInteractionCreated) {
                    isInteractionCreated = true
                    val interaction = ShowInteractionInfo(
                        info.userId,
                        info.userName,
                        info.roomId,
                        ShowInteractionStatus.pking.value,
                        muteAudio = false,
                        ownerMuteAudio = false,
                        createdAt = info.createAt
                    )
                    innerCreateInteraction(interaction, null, null)
                } else {
                    val oldInteraction =
                        interactionInfoList.filter { it.userId == info.userId }.getOrNull(0)
                    if (oldInteraction != null) {
                        val indexOf = interactionInfoList.indexOf(oldInteraction)
                        val objId = objIdOfInteractionInfo[indexOf]

                        val interaction = ShowInteractionInfo(
                            oldInteraction.userId,
                            oldInteraction.userName,
                            oldInteraction.roomId,
                            oldInteraction.interactStatus,
                            info.userMuteAudio,
                            info.fromUserMuteAudio,
                            oldInteraction.createdAt
                        )
                        innerUpdateInteraction(objId, interaction, null, null)
                    }
                }

                runOnMainThread {
                    micPKInvitationSubscriber?.invoke(
                        ShowServiceProtocol.ShowSubscribeStatus.updated,
                        info
                    )
                }
            }

            override fun onDeleted(item: IObject?) {
                val objId = item!!.id
                val index = objIdOfPKCompetitorInvitation.indexOf(objId)
                val invitation = pKCompetitorInvitationList[index]

                val sceneReference = sceneReferenceMap[invitation.roomId] ?: return
                val event = pkCompetitorEventListenerMap[invitation.roomId] ?: return;
                sceneReference.unsubscribe(event)

                sceneReferenceMap.remove(invitation.roomId)
                objIdOfPKCompetitorInvitation.removeAt(index)
                pKCompetitorInvitationList.removeAt(index)
                runOnMainThread {
                    micPKInvitationSubscriber?.invoke(
                        ShowServiceProtocol.ShowSubscribeStatus.deleted,
                        null
                    )
                }
            }

            override fun onSubscribeError(ex: SyncManagerException?) {
            }
        }
        pkCompetitorEventListenerMap[roomId] = listener
        sceneReference.collection(kCollectionIdPKInvitation).subscribe(listener)
    }

    // ----------------------------------- 互动状态 -----------------------------------
    private fun innerGetAllInteractionList(
        success: ((List<ShowInteractionInfo>) -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        sceneReferenceMap[currRoomNo]?.collection(kCollectionIdInteractionInfo)
            ?.get(object : Sync.DataListCallback {
                override fun onSuccess(result: MutableList<IObject>?) {
                    val ret = ArrayList<ShowInteractionInfo>()
                    val retObjId = ArrayList<String>()
                    result?.forEach {
                        val obj = it.toObject(ShowInteractionInfo::class.java)
                        ret.add(obj)
                        retObjId.add(it.id)
                    }
                    interactionInfoList.clear()
                    interactionInfoList.addAll(ret)
                    objIdOfInteractionInfo.clear()
                    objIdOfInteractionInfo.addAll(retObjId)

                    runOnMainThread { success?.invoke(ret) }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerCreateInteraction(
        info: ShowInteractionInfo,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        Log.d(TAG, "innerCreateInteraction called")
        sceneReferenceMap[currRoomNo]?.collection(kCollectionIdInteractionInfo)
            ?.add(info, object : Sync.DataItemCallback {
                override fun onSuccess(result: IObject) {
                    Log.d(TAG, "innerCreateInteraction success")
                    runOnMainThread { success?.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    Log.d(TAG, "innerCreateInteraction failed")
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerUpdateInteraction(
        objectId: String,
        info: ShowInteractionInfo,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        sceneReferenceMap[currRoomNo]?.collection(kCollectionIdInteractionInfo)
            ?.update(objectId, info, object : Sync.Callback {
                override fun onSuccess() {
                    runOnMainThread { success?.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerRemoveInteraction(
        objectId: String,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        sceneReferenceMap[currRoomNo]?.collection(kCollectionIdInteractionInfo)
            ?.delete(objectId, object : Sync.Callback {
                override fun onSuccess() {
                    innerUpdateRoomInteractStatus(ShowInteractionStatus.idle.value, {}, {})
                    runOnMainThread { success?.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerSubscribeInteractionChanged() {
        Log.d(TAG, "innerSubscribeInteractionChanged called")
        val sceneReference = sceneReferenceMap[currRoomNo] ?: return
        val listener = object : EventListener {
            override fun onCreated(item: IObject?) {
                Log.d(TAG, "innerSubscribeInteractionChanged onCreated")
                // do Nothing
            }

            override fun onUpdated(item: IObject?) {
                Log.d(TAG, "innerSubscribeInteractionChanged onUpdated")
                val info = item?.toObject(ShowInteractionInfo::class.java) ?: return

                val interactionInfo = interactionInfoList.getOrNull(0)
                if (interactionInfo != null && interactionInfo.userId != info.userId) {
                    stopInteraction(info)
                    val userItem = userList.filter { it.userId == info.userId }.getOrNull(0)
                    if (userItem != null) {
                        val userItem = ShowUser(
                            userItem.userId,
                            userItem.avatar,
                            userItem.userName,
                            ShowRoomRequestStatus.idle.value,
                        )
                        innerUpdateUserRoomRequestStatus(userItem, {}, {})
                    }
                    return
                }

                val list = interactionInfoList.filter { it.userId == info.userId }
                if (list.isEmpty()) {
                    interactionInfoList.add(info)
                    objIdOfInteractionInfo.add(item.id)
                } else {
                    val indexOf = interactionInfoList.indexOf(list[0])
                    interactionInfoList[indexOf] = info
                    objIdOfInteractionInfo[indexOf] = item.id
                }
                innerUpdateRoomInteractStatus(info.interactStatus, {}, {})

                runOnMainThread {
                    micInteractionInfoSubscriber?.invoke(
                        ShowServiceProtocol.ShowSubscribeStatus.updated,
                        info
                    )
                }

            }

            override fun onDeleted(item: IObject?) {
                Log.d(TAG, "innerSubscribeInteractionChanged onDeleted")
                isInteractionCreated = false
                val objId = item!!.id
                val index = objIdOfInteractionInfo.indexOf(objId)
                if (index < 0) return
                objIdOfInteractionInfo.removeAt(index)
                interactionInfoList.removeAt(index)
                innerUpdateRoomInteractStatus(ShowInteractionStatus.idle.value, {}, {})

                runOnMainThread {
                    micInteractionInfoSubscriber?.invoke(
                        ShowServiceProtocol.ShowSubscribeStatus.deleted,
                        null
                    )
                }
            }

            override fun onSubscribeError(ex: SyncManagerException?) {
                Log.d(TAG, "innerSubscribeInteractionChanged onSubscribeError: $ex")
            }
        }
        currEventListeners.add(listener)
        sceneReference.collection(kCollectionIdInteractionInfo).subscribe(listener)
    }
}