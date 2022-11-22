package io.agora.scene.voice.general.repositories

import android.text.TextUtils
import androidx.lifecycle.LiveData
import io.agora.scene.voice.service.*
import io.agora.voice.baseui.general.callback.ResultCallBack
import io.agora.voice.baseui.general.net.Resource

/**
 * @author create by zhangwei03
 */
class VoiceRoomRepository : BaseRepository() {

    /**
     * voice chat protocol
     */
    private val voiceServiceProtocol = VoiceServiceProtocol.getImplInstance()

    /**
     * 获取房间列表
     * @param page 第几页，暂未用到
     * @param roomType 房间类型，暂未用到
     */
    fun fetchRoomList(page: Int, roomType: Int): LiveData<Resource<List<VoiceRoomModel>>> {
        val resource = object : NetworkOnlyResource<List<VoiceRoomModel>>() {
            override fun createCall(callBack: ResultCallBack<LiveData<List<VoiceRoomModel>>>) {
                voiceServiceProtocol.fetchRoomList(page, roomType, completion = { error, result ->
                    if (error == VoiceServiceProtocol.ERR_OK) {
                        callBack.onSuccess(createLiveData(result))
                    } else {
                        callBack.onError(error, "")
                    }
                })
            }
        }
        return resource.asLiveData()
    }

    /**
     * 私密房间密码校验，本地模拟验证
     * @param roomId 房间id
     * @param password 房间密码
     * @param userInput 用户输入
     */
    fun checkPassword(roomId: String, password: String, userInput: String): LiveData<Resource<Boolean>> {
        val resource = object : NetworkOnlyResource<Boolean>() {
            override fun createCall(callBack: ResultCallBack<LiveData<Boolean>>) {
                if (TextUtils.equals(password, userInput)) {
                    callBack.onSuccess(createLiveData(true))
                } else {
                    callBack.onError(VoiceServiceProtocol.ERR_FAILED)
                }
            }
        }
        return resource.asLiveData()
    }

    /**
     * @param roomName 房间名
     * @param soundEffect 房间音效类型
     * @param roomType 房间类型 0 普通房间，1 3d 房间
     * @param password  私有房间，有秘密
     */
    fun createRoom(
        roomName: String,
        soundEffect: Int = 0,
        roomType: Int = 0,
        password: String? = null
    ): LiveData<Resource<VoiceRoomModel>> {
        val resource = object : NetworkOnlyResource<VoiceRoomModel>() {
            override fun createCall(callBack: ResultCallBack<LiveData<VoiceRoomModel>>) {
                val voiceCreateRoomModel = VoiceCreateRoomModel(
                    roomName = roomName,
                    soundEffect = soundEffect,
                    isPrivate = !TextUtils.isEmpty(password),
                    password = password ?: "",
                    roomType = roomType
                )
                voiceServiceProtocol.createRoom(voiceCreateRoomModel, completion = { error, result ->
                    if (error == VoiceServiceProtocol.ERR_OK) {
                        callBack.onSuccess(createLiveData(result))
                    } else {
                        callBack.onError(error, "")
                    }
                })
            }
        }
        return resource.asLiveData()
    }

    /**
     * 加入房间
     * @param roomId 房间id
     * @param password 密码
     * @param needConvertConfig false
     */
    fun joinRoom(
        roomId: String,
        password: String? = null,
        needConvertConfig: Boolean = false,
    ): LiveData<Resource<Boolean>> {
        val resource = object : NetworkOnlyResource<Boolean>() {
            override fun createCall(callBack: ResultCallBack<LiveData<Boolean>>) {

                voiceServiceProtocol.joinRoom(roomId, password ?: "", needConvertConfig, completion = { error, result ->
                    if (error == VoiceServiceProtocol.ERR_OK) {
                        callBack.onSuccess(createLiveData(result))
                    } else {
                        callBack.onError(error, "")
                    }
                })
            }
        }
        return resource.asLiveData()
    }
}