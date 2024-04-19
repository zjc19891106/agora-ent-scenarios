package io.agora.scene.pure1v1.callapi.extension

import io.agora.scene.pure1v1.callapi.CallConfig
import io.agora.scene.pure1v1.callapi.PrepareConfig


fun Long.getCostMilliseconds(): Long {
    return System.currentTimeMillis() - this
}

fun PrepareConfig.cloneConfig(): PrepareConfig {
    return PrepareConfig(roomId, rtcToken, rtmToken, localView, remoteView, autoJoinRTC, callTimeoutMillisecond, userExtension)
}

fun CallConfig.cloneConfig(): CallConfig {
    return CallConfig(appId, userId, rtcEngine, signalClient)
}