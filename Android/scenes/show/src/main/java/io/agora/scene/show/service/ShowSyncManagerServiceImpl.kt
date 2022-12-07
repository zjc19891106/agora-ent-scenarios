package io.agora.scene.show.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.agora.scene.base.BuildConfig
import io.agora.scene.base.manager.UserManager
import io.agora.syncmanager.rtm.*
import io.agora.syncmanager.rtm.Sync.EventListener

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

    // global cache data
    private val roomMap = mutableMapOf<String, ShowRoomDetailModel>()
    private val objIdOfUserId = mutableMapOf<String, String>() // key: userId, value: objectId

    private val micSeatApplyList = ArrayList<ShowMicSeatApply>()
    private val micSeatInvitationList = ArrayList<ShowMicSeatInvitation>()
    private val pKInvitationList = ArrayList<ShowPKInvitation>()
    private val interactionInfoList = ArrayList<ShowInteractionInfo>()

    // cache objectId
    private val objIdOfSeatApply = ArrayList<String>() // objectId of seat Apply
    private val objIdOfSeatInvitation = ArrayList<String>() // objectId of seat Invitation
    private val objIdOfPKInvitation = ArrayList<String>() // objectId of pk Invitation
    private val objIdOfInteractionInfo = ArrayList<String>() // objectId of pk Invitation


    // current room cache data
    private var currRoomNo: String = ""
    private var currSceneReference: SceneReference? = null
    private val currEventListeners = mutableListOf<EventListener>()

    private var currUserChangeSubscriber: ((ShowServiceProtocol.ShowSubscribeStatus, ShowUser?) -> Unit)? =
        null
    private var micSeatApplySubscriber: ((ShowServiceProtocol.ShowSubscribeStatus, ShowMicSeatApply?) -> Unit)? =
        null
    private var micSeatInvitationSubscriber: ((ShowServiceProtocol.ShowSubscribeStatus, ShowMicSeatInvitation?) -> Unit)? =
        null
    private var micPKInvitationSubscriber: ((ShowServiceProtocol.ShowSubscribeStatus, ShowPKInvitation?) -> Unit)? = null
    private var micInteractionInfoSubscriber: ((ShowServiceProtocol.ShowSubscribeStatus, ShowInteractionInfo?) -> Unit)? = null


    override fun getRoomList(
        success: (List<ShowRoomDetailModel>) -> Unit,
        error: ((Exception) -> Unit)?
    ) {
        initSync {
            Sync.Instance().getScenes(object : Sync.DataListCallback {
                override fun onSuccess(result: MutableList<IObject>?) {
                    val roomList = result!!.map {
                        it.toObject(ShowRoomDetailModel::class.java)
                    }
                    roomMap.clear()
                    roomList.forEach { roomMap[it.roomId] = it.copy() }
                    success.invoke(roomList.sortedBy { it.createdAt })
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
            val roomDetail = ShowRoomDetailModel(
                roomId,
                roomName,
                0,
                thumbnailId,
                UserManager.getInstance().user.id.toString(),
                UserManager.getInstance().user.headUrl,
                ShowRoomStatus.activity.value,
                ShowInteractionStatus.idle.value,
                System.currentTimeMillis().toDouble(),
                System.currentTimeMillis().toDouble()
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
                        error?.invoke(exception!!)
                    }
                })
        }
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
        initSync {
            Sync.Instance().joinScene(
                roomNo, object : Sync.JoinSceneCallback {
                    override fun onSuccess(sceneReference: SceneReference?) {
                        this@ShowSyncManagerServiceImpl.currSceneReference = sceneReference!!
                        innerMayAddLocalUser({
                            innerSubscribeUserChange()
                            success.invoke(roomMap[roomNo]!!)
                        }, {
                            error?.invoke(it) ?: errorHandler.invoke(it)
                            currRoomNo = ""
                        })
                        innerSubscribeSeatApplyChanged()
                        innerSubscribeInteractionChanged()
                        innerSubscribeSeatInvitationChanged()
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
        val roomDetail = roomMap[currRoomNo] ?: return
        val sceneReference = currSceneReference ?: return

        currEventListeners.forEach {
            sceneReference.unsubscribe(it)
        }
        currEventListeners.clear()

        if (roomDetail.ownerId == UserManager.getInstance().user.id.toString()) {
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

        innerRemoveUser(
            UserManager.getInstance().user.id.toString(),
            {},
            { errorHandler.invoke(it) }
        )

        currRoomNo = ""
        currSceneReference = null
    }

    override fun getAllUserList(success: (List<ShowUser>) -> Unit, error: ((Exception) -> Unit)?) {
        innerGetUserList(success) {
            error?.invoke(it) ?: errorHandler.invoke(it)
        }
    }

    override fun subscribeUser(onUserChange: (ShowServiceProtocol.ShowSubscribeStatus, ShowUser?) -> Unit) {
        currUserChangeSubscriber = onUserChange;
    }

    override fun sendChatMessage(
        message: String,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        val sceneReference = currSceneReference ?: return
        sceneReference.collection(kCollectionIdMessage)
            .add(ShowMessage(
                UserManager.getInstance().user.id.toString(),
                UserManager.getInstance().user.name,
                message,
                System.currentTimeMillis().toDouble()
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
        val sceneReference = currSceneReference ?: return
        val listener = object: EventListener{
            override fun onCreated(item: IObject?) {
                // do nothing
            }

            override fun onUpdated(item: IObject?) {
                item?: return
                onMessageChange.invoke(ShowServiceProtocol.ShowSubscribeStatus.updated, item.toObject(ShowMessage::class.java))
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
        micSeatApplySubscriber = onMicSeatChange;
    }

    override fun createMicSeatApply(success: (() -> Unit)?, error: ((Exception) -> Unit)?) {
        val apply = ShowMicSeatApply(
            UserManager.getInstance().user.id.toString(),
            UserManager.getInstance().user.headUrl,
            UserManager.getInstance().user.name,
            ShowRoomRequestStatus.waitting,
            System.currentTimeMillis().toDouble()
        )
        innerCreateSeatApply(apply, success, error)
    }

    override fun cancelMicSeatApply(success: (() -> Unit)?, error: ((Exception) -> Unit)?) {
        if (micSeatApplyList.size <= 0) {
            error?.invoke(RuntimeException("The seat apply list is empty!"))
            return
        }
        val targetApply = micSeatApplyList.filter { it.userId == UserManager.getInstance().user.id.toString() }.getOrNull(0)
        if (targetApply == null) {
            error?.invoke(RuntimeException("The seat apply found!"))
            return
        }

        val indexOf = micSeatApplyList.indexOf(targetApply);
        micSeatApplyList.removeAt(indexOf);
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

        val seatApply = ShowMicSeatApply(
            targetApply.userId,
            targetApply.userAvatar,
            targetApply.userName,
            ShowRoomRequestStatus.accepted,
            targetApply.createAt
        )

        val indexOf = micSeatApplyList.indexOf(targetApply);
        micSeatApplyList[indexOf] = seatApply
        innerUpdateSeatApply(objIdOfSeatApply[indexOf], seatApply, success, error)

        val interaction = ShowInteractionInfo(
            apply.userId,
            apply.userName,
            currRoomNo,
            ShowInteractionStatus.onSeat,
            false,
            false,
            apply.createAt
        )
        innerCreateInteration(interaction, null, null)
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
            targetApply.userAvatar,
            targetApply.userName,
            ShowRoomRequestStatus.rejected,
            targetApply.createAt
        )
        val indexOf = micSeatApplyList.indexOf(targetApply);
        micSeatApplyList[indexOf] = seatApply
        innerUpdateSeatApply(objIdOfSeatApply[indexOf], seatApply, success, error)
    }

    override fun getAllMicSeatInvitationList(
        success: (List<ShowMicSeatInvitation>) -> Unit,
        error: ((Exception) -> Unit)?
    ) {
        innerGetSeatInvitationList(success, error)
    }

    override fun subscribeMicSeatInvitation(onMicSeatInvitationChange: (ShowServiceProtocol.ShowSubscribeStatus, ShowMicSeatInvitation?) -> Unit) {
        micSeatInvitationSubscriber = onMicSeatInvitationChange
    }

    override fun createMicSeatInvitation(
        user: ShowUser,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        val invatation = ShowMicSeatInvitation(
            user.userId,
            user.avatar,
            user.userName,
            ShowRoomRequestStatus.waitting
        )
        innerCreateSeatInvitation(invatation, success, error)
    }

    override fun cancelMicSeatInvitation(
        userId: String,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        if (micSeatInvitationList.size <= 0) {
            error?.invoke(RuntimeException("The seat invitation list is empty!"))
            return
        }
        val targetInvitation = micSeatInvitationList.filter { it.userId == userId }.getOrNull(0)
        if (targetInvitation == null) {
            error?.invoke(RuntimeException("The seat invitation found!"))
            return
        }

        val indexOf = micSeatInvitationList.indexOf(targetInvitation);
        micSeatInvitationList.removeAt(indexOf);
        val removedSeatInvitationObjId = objIdOfSeatInvitation.removeAt(indexOf)

        innerRemoveSeatInvitation(removedSeatInvitationObjId, success, error)
    }

    override fun acceptMicSeatInvitation(
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        if (micSeatInvitationList.size <= 0) {
            error?.invoke(RuntimeException("The seat invitation list is empty!"))
            return
        }
        val targetInvitation = micSeatInvitationList.filter { it.userId == UserManager.getInstance().user.id.toString() }.getOrNull(0)
        if (targetInvitation == null) {
            error?.invoke(RuntimeException("The seat invitation found!"))
            return
        }

        val invitation = ShowMicSeatInvitation(
            targetInvitation.userId,
            targetInvitation.userAvatar,
            targetInvitation.userName,
            ShowRoomRequestStatus.accepted,
        )
        val indexOf = micSeatInvitationList.indexOf(targetInvitation);
        micSeatInvitationList[indexOf] = invitation;
        innerUpdateSeatInvitation(objIdOfSeatInvitation[indexOf], invitation, success, error)

        val interaction = ShowInteractionInfo(
            invitation.userId,
            invitation.userName,
            currRoomNo,
            ShowInteractionStatus.onSeat,
            false,
            false,
            0.0 //TODO
        )
        innerCreateInteration(interaction, {  }, {  })
    }

    override fun rejectMicSeatInvitation(
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        if (micSeatInvitationList.size <= 0) {
            error?.invoke(RuntimeException("The seat invitation list is empty!"))
            return
        }
        val targetInvitation = micSeatInvitationList.filter { it.userId == UserManager.getInstance().user.id.toString() }.getOrNull(0)
        if (targetInvitation == null) {
            error?.invoke(RuntimeException("The seat invitation found!"))
            return
        }

        val invitation = ShowMicSeatInvitation(
            targetInvitation.userId,
            targetInvitation.userAvatar,
            targetInvitation.userName,
            ShowRoomRequestStatus.rejected,
        )
        val indexOf = micSeatInvitationList.indexOf(targetInvitation);
        micSeatInvitationList[indexOf] = invitation;
        innerUpdateSeatInvitation(objIdOfSeatInvitation[indexOf], invitation, success, error)
    }

    override fun getAllPKInvitationList(
        success: (List<ShowPKInvitation>) -> Unit,
        error: ((Exception) -> Unit)?
    ) {
        innerGetPKInvitationList(success, error)
    }

    override fun subscribePKInvitationChanged(onPKInvitationChanged: (ShowServiceProtocol.ShowSubscribeStatus, ShowPKInvitation?) -> Unit) {
        micPKInvitationSubscriber = onPKInvitationChanged
    }

    override fun createPKInvitation(
        room: ShowRoomListModel,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        TODO("Not yet implemented")
    }

    override fun acceptPKInvitation(success: (() -> Unit)?, error: ((Exception) -> Unit)?) {
        if (pKInvitationList.size <= 0) {
            error?.invoke(RuntimeException("The seat invitation list is empty!"))
            return
        }
        val targetInvitation = pKInvitationList.filter { it.userId == UserManager.getInstance().user.id.toString() }.getOrNull(0)
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
            ShowRoomRequestStatus.rejected,
            false,
            false,
            targetInvitation.createAt
        )

        val indexOf = pKInvitationList.indexOf(targetInvitation);
        pKInvitationList[indexOf] = invitation;
        innerUpdatePKInvitation(objIdOfPKInvitation[indexOf], invitation, success, error)
    }

    override fun rejectPKInvitation(success: (() -> Unit)?, error: ((Exception) -> Unit)?) {
        if (pKInvitationList.size <= 0) {
            error?.invoke(RuntimeException("The seat invitation list is empty!"))
            return
        }
        val targetInvitation = pKInvitationList.filter { it.userId == UserManager.getInstance().user.id.toString() }.getOrNull(0)
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
            ShowRoomRequestStatus.rejected,
            false,
            false,
            targetInvitation.createAt
        )

        val indexOf = pKInvitationList.indexOf(targetInvitation);
        pKInvitationList[indexOf] = invitation;
        innerUpdatePKInvitation(objIdOfPKInvitation[indexOf], invitation, success, error)
    }

    override fun getAllInterationList(
        success: ((List<ShowInteractionInfo>) -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        innerGetAllInterationList(success, error)
    }

    override fun subscribeInteractionChanged(onInteractionChanged: (ShowServiceProtocol.ShowSubscribeStatus, ShowInteractionInfo?) -> Unit) {
        micInteractionInfoSubscriber = onInteractionChanged;
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
        val targetInvitation = interactionInfoList.filter { it.userId == interaction.userId }.getOrNull(0)
        if (targetInvitation == null) {
            error?.invoke(RuntimeException("The interaction not found!"))
            return
        }
        innerRemoveInteration(objIdOfInteractionInfo[0], success, error)
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
                    Handler(Looper.getMainLooper()).post { complete.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    syncInitialized = false
                    errorHandler.invoke(exception!!)
                }
            }
        )
    }

    private fun innerUpdateRoomUserCount(
        userCount: Int,
        success: () -> Unit,
        error: (Exception) -> Unit
    ) {
        val roomInfo = roomMap[currRoomNo] ?: return
        val sceneReference = currSceneReference ?: return

        val nRoomInfo = ShowRoomDetailModel(
            roomInfo.roomId,
            roomInfo.roomName,
            userCount,
            roomInfo.thumbnailId,
            roomInfo.ownerId,
            roomInfo.ownerAvater,
            roomInfo.roomStatus,
            roomInfo.interactStatus,
            roomInfo.createdAt,
            roomInfo.updatedAt
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
        innerGetUserList({ list ->
            if (list.none { it.userId == it.toString() }) {
                innerAddUser(ShowUser(userId, "1", UserManager.getInstance().user.name),
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
        val sceneReference = currSceneReference ?: return
        sceneReference.collection(kCollectionIdUser)
            .get(object : Sync.DataListCallback {
                override fun onSuccess(result: MutableList<IObject>?) {
                    result ?: return
                    val map = result.map { it.toObject(ShowUser::class.java) }
                    runOnMainThread { success.invoke(map) }
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
        val sceneReference = currSceneReference ?: return
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
        val sceneReference = currSceneReference ?: return
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

    private fun innerSubscribeUserChange() {
        val sceneReference = currSceneReference ?: return
        val listener = object : Sync.EventListener {
            override fun onCreated(item: IObject?) {
                // do nothing
            }

            override fun onUpdated(item: IObject?) {
                val user = item?.toObject(ShowUser::class.java) ?: return
                objIdOfUserId[user.userId] = item.id
                currUserChangeSubscriber?.invoke(
                    ShowServiceProtocol.ShowSubscribeStatus.updated,
                    user
                )
            }

            override fun onDeleted(item: IObject?) {
                currUserChangeSubscriber?.invoke(
                    ShowServiceProtocol.ShowSubscribeStatus.deleted,
                    null
                )
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
        currSceneReference?.collection(kCollectionIdSeatApply)?.get(object :
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
        currSceneReference?.collection(kCollectionIdSeatApply)
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
        currSceneReference?.collection(kCollectionIdSeatApply)
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
        currSceneReference?.collection(kCollectionIdSeatApply)
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
        val sceneReference = currSceneReference ?: return
        val listener = object : EventListener {
            override fun onCreated(item: IObject?) {
                // do Nothing
            }

            override fun onUpdated(item: IObject?) {
                val info = item?.toObject(ShowMicSeatApply::class.java) ?: return
                runOnMainThread {
                    micSeatApplySubscriber?.invoke(
                        ShowServiceProtocol.ShowSubscribeStatus.updated,
                        info
                    )
                }
            }

            override fun onDeleted(item: IObject?) {
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

    // ----------------------------------- 连麦邀请 -----------------------------------
    private fun innerGetSeatInvitationList(
        success: (List<ShowMicSeatInvitation>) -> Unit,
        error: ((Exception) -> Unit)?
    ) {
        currSceneReference?.collection(kCollectionIdSeatInvitation)?.get(object : Sync.DataListCallback {
            override fun onSuccess(result: MutableList<IObject>?) {
                val ret = ArrayList<ShowMicSeatInvitation>()
                val retObjId = ArrayList<String>()
                result?.forEach {
                    val obj = it.toObject(ShowMicSeatInvitation::class.java)
                    ret.add(obj)
                    retObjId.add(it.id)
                }
                micSeatInvitationList.clear()
                micSeatInvitationList.addAll(ret)
                objIdOfSeatInvitation.clear()
                objIdOfSeatInvitation.addAll(retObjId)

                //按照创建时间顺序排序
                //ret.sortBy { it.createdAt }
                runOnMainThread { success.invoke(ret) }
            }

            override fun onFail(exception: SyncManagerException?) {
                runOnMainThread { error?.invoke(exception!!) }
            }
        })
    }

    private fun innerCreateSeatInvitation(
        seatInvitation: ShowMicSeatInvitation,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        currSceneReference?.collection(kCollectionIdSeatInvitation)
            ?.add(seatInvitation, object : Sync.DataItemCallback {
                override fun onSuccess(result: IObject) {
                    getAllMicSeatInvitationList({ })
                    runOnMainThread { success?.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerUpdateSeatInvitation(
        objectId: String,
        seatInvitation: ShowMicSeatInvitation,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        currSceneReference?.collection(kCollectionIdSeatInvitation)
            ?.update(objectId, seatInvitation, object : Sync.Callback {
                override fun onSuccess() {
                    runOnMainThread { success?.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerRemoveSeatInvitation(
        objectId: String,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        currSceneReference?.collection(kCollectionIdSeatInvitation)
            ?.delete(objectId, object : Sync.Callback {
                override fun onSuccess() {
                    runOnMainThread { success?.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerSubscribeSeatInvitationChanged() {
        val sceneReference = currSceneReference ?: return
        val listener = object : EventListener {
            override fun onCreated(item: IObject?) {
                // do Nothing
            }

            override fun onUpdated(item: IObject?) {
                val info = item?.toObject(ShowMicSeatInvitation::class.java) ?: return
                val list = micSeatInvitationList.filter { it.userId == info.userId }
                if (list.isEmpty()) {
                    micSeatInvitationList.add(info)
                    objIdOfSeatInvitation.add(item.id)
                } else {
                    val indexOf = micSeatInvitationList.indexOf(list[0])
                    micSeatInvitationList[indexOf] = info
                    objIdOfSeatInvitation[indexOf] = item.id
                }
                runOnMainThread {
                    micSeatInvitationSubscriber?.invoke(
                        ShowServiceProtocol.ShowSubscribeStatus.updated,
                        info
                    )
                }
            }

            override fun onDeleted(item: IObject?) {
                val info = item?.toObject(ShowMicSeatInvitation::class.java) ?: return
                val list = micSeatInvitationList.filter { it.userId == info.userId }
                if (!list.isEmpty()) {
                    val indexOf = micSeatInvitationList.indexOf(list[0])
                    micSeatInvitationList.removeAt(indexOf)
                    objIdOfSeatInvitation.removeAt(indexOf)
                }
                runOnMainThread {
                    micSeatInvitationSubscriber?.invoke(
                        ShowServiceProtocol.ShowSubscribeStatus.deleted,
                        null
                    )
                }

            }

            override fun onSubscribeError(ex: SyncManagerException?) {
            }
        }
        currEventListeners.add(listener)
        sceneReference.collection(kCollectionIdSeatInvitation)
            .subscribe(listener)
    }

    // ----------------------------------- pk邀请 -----------------------------------
    private fun innerGetPKInvitationList(
        success: (List<ShowPKInvitation>) -> Unit,
        error: ((Exception) -> Unit)?
    ) {
        currSceneReference?.collection(kCollectionIdPKInvitation)?.get(object : Sync.DataListCallback {
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

                //按照创建时间顺序排序
                //ret.sortBy { it.createdAt }
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
        currSceneReference?.collection(kCollectionIdPKInvitation)
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
        objectId: String,
        pkInvitation: ShowPKInvitation,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        currSceneReference?.collection(kCollectionIdPKInvitation)
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
        objectId: String,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        currSceneReference?.collection(kCollectionIdPKInvitation)
            ?.delete(objectId, object : Sync.Callback {
                override fun onSuccess() {
                    runOnMainThread { success?.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerSubscribePKInvitationChanged() {
        val sceneReference = currSceneReference ?: return
        val listener = object : EventListener {
            override fun onCreated(item: IObject?) {
                // do Nothing
            }

            override fun onUpdated(item: IObject?) {
                val info = item?.toObject(ShowPKInvitation::class.java) ?: return
                runOnMainThread {
                    micPKInvitationSubscriber?.invoke(
                        ShowServiceProtocol.ShowSubscribeStatus.updated,
                        info
                    )
                }
            }

            override fun onDeleted(item: IObject?) {
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
        currEventListeners.add(listener)
        sceneReference.collection(kCollectionIdPKInvitation).subscribe(listener)
    }

    // ----------------------------------- 互动状态 -----------------------------------
    private fun innerGetAllInterationList(
        success: ((List<ShowInteractionInfo>) -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        currSceneReference?.collection(kCollectionIdInteractionInfo)?.get(object : Sync.DataListCallback {
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

                //按照创建时间顺序排序
                ret.sortBy { it.createdAt }
                runOnMainThread { success?.invoke(ret) }
            }

            override fun onFail(exception: SyncManagerException?) {
                runOnMainThread { error?.invoke(exception!!) }
            }
        })
    }

    private fun innerCreateInteration(
        info: ShowInteractionInfo,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        Log.d(TAG, "innerCreateInteration called")
        currSceneReference?.collection(kCollectionIdInteractionInfo)
            ?.add(info, object : Sync.DataItemCallback {
                override fun onSuccess(result: IObject) {
                    Log.d(TAG, "innerCreateInteration success")
                    runOnMainThread { success?.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    Log.d(TAG, "innerCreateInteration failed")
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerUpdateInteration(
        objectId: String,
        info: ShowInteractionInfo,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        currSceneReference?.collection(kCollectionIdInteractionInfo)
            ?.update(objectId, info, object : Sync.Callback {
                override fun onSuccess() {
                    runOnMainThread { success?.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerRemoveInteration(
        objectId: String,
        success: (() -> Unit)?,
        error: ((Exception) -> Unit)?
    ) {
        currSceneReference?.collection(kCollectionIdInteractionInfo)
            ?.delete(objectId, object : Sync.Callback {
                override fun onSuccess() {
                    runOnMainThread { success?.invoke() }
                }

                override fun onFail(exception: SyncManagerException?) {
                    runOnMainThread { error?.invoke(exception!!) }
                }
            })
    }

    private fun innerSubscribeInteractionChanged() {
        Log.d(TAG, "innerSubscribeInteractionChanged called")
        val sceneReference = currSceneReference ?: return
        val listener = object : EventListener {
            override fun onCreated(item: IObject?) {
                Log.d(TAG, "innerSubscribeInteractionChanged onCreated")
                // do Nothing
            }

            override fun onUpdated(item: IObject?) {
                Log.d(TAG, "innerSubscribeInteractionChanged onUpdated")
                val info = item?.toObject(ShowInteractionInfo::class.java) ?: return
                val list = interactionInfoList.filter { it.userId == info.userId }
                if (list.isEmpty()) {
                    interactionInfoList.add(info)
                    objIdOfInteractionInfo.add(item.id)
                } else {
                    val indexOf = interactionInfoList.indexOf(list[0])
                    interactionInfoList[indexOf] = info
                    objIdOfInteractionInfo[indexOf] = item.id
                }

                runOnMainThread {
                    micInteractionInfoSubscriber?.invoke(
                        ShowServiceProtocol.ShowSubscribeStatus.updated,
                        info
                    )
                }

            }

            override fun onDeleted(item: IObject?) {
                Log.d(TAG, "innerSubscribeInteractionChanged onDeleted")
                cancelMicSeatApply()
//                val info = item?.toObject(ShowInteractionInfo::class.java) ?: return
//                val list = interactionInfoList.filter { it.userId == info.userId }
//                if (!list.isEmpty()) {
//                    val indexOf = interactionInfoList.indexOf(list[0])
//                    interactionInfoList.removeAt(indexOf)
//                    objIdOfInteractionInfo.removeAt(indexOf)
//                }
                runOnMainThread {
                    micInteractionInfoSubscriber?.invoke(
                        ShowServiceProtocol.ShowSubscribeStatus.deleted,
                        null
                    )
                }
            }

            override fun onSubscribeError(ex: SyncManagerException?) {
                Log.d(TAG, "innerSubscribeInteractionChanged onSubscribeError: " + ex)
            }
        }
        currEventListeners.add(listener)
        sceneReference.collection(kCollectionIdInteractionInfo).subscribe(listener)
    }
}