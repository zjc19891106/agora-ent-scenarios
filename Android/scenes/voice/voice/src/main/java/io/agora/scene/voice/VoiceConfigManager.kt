package io.agora.scene.voice

import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGASoundManager
import com.opensource.svgaplayer.utils.log.SVGALogger
import io.agora.scene.voice.general.interfaceOrImplement.UserActivityLifecycleCallbacks
import io.agora.scene.voice.service.VoiceBuddyFactory
import io.agora.voice.imkit.manager.ChatroomConfigManager
import io.agora.voice.network.http.toolbox.VoiceToolboxRequestApi

/**
 * @author create by zhangwei03
 */
object VoiceConfigManager {

    private val mLifecycleCallbacks = UserActivityLifecycleCallbacks()

    @JvmStatic
    fun initMain() {
        ChatroomConfigManager.getInstance()
            .initRoomConfig(
                VoiceBuddyFactory.get().getVoiceBuddy().application(),
                VoiceBuddyFactory.get().getVoiceBuddy().chatAppKey()
            )
        VoiceToolboxRequestApi.get().setBaseUrl(VoiceBuddyFactory.get().getVoiceBuddy().toolboxServiceUrl())
        VoiceBuddyFactory.get().getVoiceBuddy().application().registerActivityLifecycleCallbacks(mLifecycleCallbacks)
        SVGAParser.shareParser().init( VoiceBuddyFactory.get().getVoiceBuddy().application())
        SVGALogger.setLogEnabled(true)
        SVGASoundManager.init()
//        CrashReport.initCrashReport(VoiceBuddyFactory.get().getVoiceBuddy().application(), "baed12f146", false)
    }

    @JvmStatic
    fun getLifecycleCallbacks(): UserActivityLifecycleCallbacks {
        return mLifecycleCallbacks
    }
}