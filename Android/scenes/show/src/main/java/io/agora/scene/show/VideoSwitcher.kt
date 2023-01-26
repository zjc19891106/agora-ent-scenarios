package io.agora.scene.show

import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcConnection

interface VideoSwitcher {

    class IChannelEventListener(
        var onChannelJoined: (()->Unit)? = null,
        var onUserJoined: ((uid: Int) -> Unit)? = null,
        var onUserOffline: ((uid: Int) -> Unit)? = null,
        var onLocalVideoStateChanged: ((state: Int) -> Unit)? = null,
        var onRemoteVideoStateChanged: ((uid: Int, state: Int) -> Unit)? = null,
        var onRtcStats: ((stats: IRtcEngineEventHandler.RtcStats) -> Unit)? = null,
        var onLocalVideoStats: ((stats: IRtcEngineEventHandler.LocalVideoStats) -> Unit)? = null,
        var onRemoteVideoStats: ((stats: IRtcEngineEventHandler.RemoteVideoStats) -> Unit)? = null,
        var onLocalAudioStats: ((stats: IRtcEngineEventHandler.LocalAudioStats) -> Unit)? = null,
        var onRemoteAudioStats: ((stats: IRtcEngineEventHandler.RemoteAudioStats) -> Unit)? = null,
        var onUplinkNetworkInfoUpdated: ((info: IRtcEngineEventHandler.UplinkNetworkInfo) -> Unit)? = null,
        var onDownlinkNetworkInfoUpdated: ((info: IRtcEngineEventHandler.DownlinkNetworkInfo) -> Unit)? = null,
        var onContentInspectResult: ((result: Int) -> Unit)? = null,
    )

    /**
     * 设置最大预加载的连接数
     */
    fun setPreloadCount(count: Int)

    /**
     * 设置预加载的连接列表
     */
    fun preloadConnections(connections: List<RtcConnection>)

    /**
     * 离开所有已加入的频道连接
     */
    fun unloadConnections()

    /**
     * 加入频道并预先加入预加载连接列表里在该connection上下不超过最大预加载连接数的频道
     */
    fun joinChannel(
        connection: RtcConnection,
        mediaOptions: ChannelMediaOptions,
        eventListener: IChannelEventListener
    )

    /**
     * 离开频道，如果在已预加载的频道则只取消订阅音视频流
     */
    fun leaveChannel(connection: RtcConnection): Boolean

}