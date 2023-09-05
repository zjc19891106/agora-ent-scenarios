//
//  ShowAgoraKitManager.swift
//  AgoraEntScenarios
//
//  Created by FanPengpeng on 2022/11/22.
//

import Foundation
import AgoraRtcKit
import UIKit
import YYCategories
import VideoLoaderAPI

class ShowAgoraKitManager: NSObject {
    
    static let shared = ShowAgoraKitManager()
    
    private var videoLoader: IVideoLoaderApi?
    
    // 是否开启绿幕功能
    static var isOpenGreen: Bool = false
    static var isBlur: Bool = false
    
    public let rtcParam = ShowRTCParams()
    public var deviceLevel: DeviceLevel = .medium
    public var deviceScore: Int = 100
    public var netCondition: NetCondition = .good
    public var performanceMode: PerformanceMode = .smooth
    
    private var broadcasterConnection: AgoraRtcConnection?
    
    var exposureRangeX: Int?
    var exposureRangeY: Int?
    var matrixCoefficientsExt: Int?
    var videoFullrangeExt: Int?
    
    let encoderConfig = AgoraVideoEncoderConfiguration()
    
    public lazy var captureConfig: AgoraCameraCapturerConfiguration = {
        let config = AgoraCameraCapturerConfiguration()
        config.followEncodeDimensionRatio = true
        config.cameraDirection = .front
        config.frameRate = 15
        return config
    }()
    
    public var engine: AgoraRtcEngineKit?
    
    private var player: AgoraRtcMediaPlayerProtocol?
    func mediaPlayer() -> AgoraRtcMediaPlayerProtocol? {
        if let p = player {
            return p
        } else {
            player = engine?.createMediaPlayer(with: self)
            player?.setLoopCount(-1)
            return player
        }
    }
    
    func prepareEngine() {
        let engine = AgoraRtcEngineKit.sharedEngine(with: engineConfig(), delegate: nil)
        self.engine = engine
        
        let loader = VideoLoaderApiImpl()
        loader.addListener(listener: self)
        let config = VideoLoaderConfig()
        config.rtcEngine = engine
        config.userId = UInt(VLUserCenter.user.id)!
        loader.setup(config: config)
        videoLoader = loader
        
        showLogger.info("load AgoraRtcEngineKit, sdk version: \(AgoraRtcEngineKit.getSdkVersion())", context: kShowLogBaseContext)
    }
    
    func destoryEngine() {
        AgoraRtcEngineKit.destroy()
        showLogger.info("deinit-- ShowAgoraKitManager")
    }
    // 退出已加入的频道和子频道
    func leaveAllRoom() {
        cleanTimestampMap()
        videoLoader?.cleanCache()
        if let p = player {
            engine?.destroyMediaPlayer(p)
            player = nil
        }
    }
    
    //MARK: private
    private func engineConfig() -> AgoraRtcEngineConfig {
        let config = AgoraRtcEngineConfig()
         config.appId = KeyCenter.AppId
         config.channelProfile = .liveBroadcasting
         config.areaCode = .global
         return config
    }
    
    private func setupContentInspectConfig(_ enable: Bool, connection: AgoraRtcConnection) {
        let config = AgoraContentInspectConfig()
        let dic: [String: String] = [
            "id": VLUserCenter.user.id,
            "sceneName": "show",
            "userNo": VLUserCenter.user.userNo
        ]
        
        guard let jsonData = try? JSONSerialization.data(withJSONObject: dic, options: .prettyPrinted) else {
            showLogger.error("setupContentInspectConfig fail")
            return
        }
        let jsonStr = String(data: jsonData, encoding: .utf8)
        config.extraInfo = jsonStr
        let module = AgoraContentInspectModule()
        module.interval = 30
        module.type = .imageModeration
        config.modules = [module]
        let ret = engine?.enableContentInspectEx(enable, config: config, connection: connection)
        showLogger.info("setupContentInspectConfig: \(ret ?? -1)")
    }
    
    /// 语音审核
    private func moderationAudio(channelName: String, role: AgoraClientRole) {
        guard role == .broadcaster else { return }
        let userInfo = ["id": VLUserCenter.user.id,
                        "sceneName": "show",
                        "userNo": VLUserCenter.user.userNo,
                        "userName": VLUserCenter.user.name]
        let parasm: [String: Any] = ["appId": KeyCenter.AppId,
                                     "channelName": channelName,
                                     "channelType": engineConfig().channelProfile.rawValue,
                                     "traceId": NSString.withUUID().md5(),
                                     "src": "iOS",
                                     "payload": JSONObject.toJsonString(dict: userInfo) ?? ""]
        NetworkManager.shared.postRequest(urlString: "https://service.agora.io/toolbox/v1/moderation/audio",
                                          params: parasm) { response in
            showLogger.info("response === \(response)")
        } failure: { errr in
            showLogger.error(errr)
        }
    }
    
    private func _joinChannelEx(currentChannelId: String,
                                targetChannelId: String,
                                ownerId: UInt,
                                token: String,
                                options:AgoraRtcChannelMediaOptions,
                                role: AgoraClientRole) {
        if role == .audience {
            let roomInfo = _getRoomInfo(channelId: targetChannelId, uid: ownerId)
            let newState: RoomStatus = broadcasterConnection == nil ? .prejoined : .joined
            videoLoader?.switchRoomState(newState: newState, roomInfo: roomInfo, tagId: currentChannelId)
            return
        }
        
        guard let engine = engine else {
            assert(true, "rtc engine not initlized")
            return
        }

        if let _ = broadcasterConnection {
            return
        }
        
        let mediaOptions = AgoraRtcChannelMediaOptions()
        mediaOptions.publishCameraTrack = true
        mediaOptions.publishMicrophoneTrack = true
        mediaOptions.autoSubscribeAudio = true
        mediaOptions.autoSubscribeVideo = true
        mediaOptions.clientRoleType = .broadcaster

        updateVideoEncoderConfigurationForConnenction(currentChannelId: currentChannelId)

        let connection = AgoraRtcConnection()
        connection.channelId = targetChannelId
        connection.localUid = UInt(VLUserCenter.user.id) ?? 0

        let proxy = videoLoader?.getRTCListener(roomId: currentChannelId)
        let date = Date()
        showLogger.info("try to join room[\(connection.channelId)] ex uid: \(connection.localUid)", context: kShowLogBaseContext)
        let ret =
        engine.joinChannelEx(byToken: token,
                               connection: connection,
                               delegate: proxy,
                               mediaOptions: mediaOptions) {[weak self] channelName, uid, elapsed in
            let cost = Int(-date.timeIntervalSinceNow * 1000)
            showLogger.info("join room[\(channelName)] ex success uid: \(uid) cost \(cost) ms", context: kShowLogBaseContext)
            self?.setupContentInspectConfig(true, connection: connection)
//            self?.moderationAudio(channelName: targetChannelId, role: role)
            self?.applySimulcastStream(connection: connection)
        }
        engine.updateChannelEx(with: mediaOptions, connection: connection)
        broadcasterConnection = connection

        if ret == 0 {
            showLogger.info("join room ex: channelId: \(targetChannelId) ownerId: \(ownerId)",
                            context: "AgoraKitManager")
        }else{
            showLogger.error("join room ex fail: channelId: \(targetChannelId) ownerId: \(ownerId) token = \(token), \(ret)",
                             context: kShowLogBaseContext)
        }
    }
    
    func updateVideoEncoderConfigurationForConnenction(currentChannelId: String) {
        guard let engine = engine else {
            assert(true, "rtc engine not initlized")
            return
        }
        let connection = AgoraRtcConnection()
        connection.channelId = currentChannelId
        connection.localUid = UInt(VLUserCenter.user.id) ?? 0
        let encoderRet = engine.setVideoEncoderConfigurationEx(encoderConfig, connection: connection)
        showLogger.info("setVideoEncoderConfigurationEx  dimensions = \(encoderConfig.dimensions), bitrate = \(encoderConfig.bitrate), fps = \(encoderConfig.frameRate),  encoderRet = \(encoderRet)", context: kShowLogBaseContext)
    }
    
    //MARK: public method
    func addRtcDelegate(delegate: AgoraRtcEngineDelegate, roomId: String) {
        videoLoader?.addRTCListener(roomId: roomId, listener: delegate)
    }
    
    func removeRtcDelegate(delegate: AgoraRtcEngineDelegate, roomId: String) {
        videoLoader?.removeRTCListener(roomId: roomId, listener: delegate)
    }
    
    func renewToken(channelId: String) {
        showLogger.info("renewToken with channelId: \(channelId)",
                        context: kShowLogBaseContext)
        NetworkManager.shared.generateToken(channelName: "",
                                            uid: UserInfo.userId,
                                            tokenType: .token007,
                                            type: .rtc) {[weak self] token in
            guard let token = token else {
                showLogger.error("renewToken fail: token is empty")
                return
            }
            let option = AgoraRtcChannelMediaOptions()
            option.token = token
            AppContext.shared.rtcToken = token
            self?.updateChannelEx(channelId: channelId, options: option)
        }
    }
    
    // 耗时计算
    private var savedTimestampMap: [String: Date] = [String: Date]()
    
    func callTimestampStart(clean: Bool, roomId: String?) {
        guard let roomId = roomId else {return}
        showLogger.info("callTimeStampsSaved  : start")
        if (clean) {
            savedTimestampMap[roomId] = nil
        }
        if savedTimestampMap[roomId] == nil {
            showLogger.info("callTimeStampsSaved  : saved")
            savedTimestampMap[roomId] = Date()
        }
    }
    
    func callTimestampEnd(_ roomId: String?) -> TimeInterval? {
        guard let roomId = roomId else {return nil}
        showLogger.info("callTimeStampsSaved  : end called")
        guard let saved = savedTimestampMap[roomId] else {
            return nil
        }
        showLogger.info("callTimeStampsSaved  : end value")
        savedTimestampMap[roomId] = nil
        return -saved.timeIntervalSinceNow * 1000
    }
    
    private func cleanTimestampMap(){
        savedTimestampMap.removeAll()
    }
    
    //MARK: public sdk method
    /// 初始化并预览
    /// - Parameter canvasView: 画布
    func startPreview(canvasView: UIView) {
        guard let engine = engine else {
            assert(true, "rtc engine not initlized")
            return
        }
        engine.setClientRole(.broadcaster)
        engine.setVideoEncoderConfiguration(encoderConfig)
        engine.setCameraCapturerConfiguration(captureConfig)
        BeautyManager.shareManager.beautyAPI.setupLocalVideo(canvasView, renderMode: .hidden)
        engine.enableVideo()
        engine.startPreview()
    }
    
    /// 切换摄像头
    func switchCamera(_ channelId: String? = nil) {
        BeautyManager.shareManager.beautyAPI.switchCamera()
    }
    
    /// 开启虚化背景
    func enableVirtualBackground(isOn: Bool, greenCapacity: Float = 0) {
        guard let engine = engine else {
            assert(true, "rtc engine not initlized")
            return
        }
        let source = AgoraVirtualBackgroundSource()
        source.backgroundSourceType = .blur
        source.blurDegree = .high
        var seg: AgoraSegmentationProperty?
        if ShowAgoraKitManager.isOpenGreen {
            seg = AgoraSegmentationProperty()
            seg?.modelType = .agoraGreen
            seg?.greenCapacity = greenCapacity
        }
        let ret = engine.enableVirtualBackground(isOn, backData: source, segData: seg)
        showLogger.info("isOn = \(isOn), enableVirtualBackground ret = \(ret)")
    }
    
    /// 设置虚拟背景
    func seVirtualtBackgoundImage(imagePath: String?, isOn: Bool, greenCapacity: Float = 0) {
        guard let bundlePath = Bundle.main.path(forResource: "showResource", ofType: "bundle"),
              let bundle = Bundle(path: bundlePath) else { return }
        let imgPath = bundle.path(forResource: imagePath, ofType: "jpg")
        let source = AgoraVirtualBackgroundSource()
        source.backgroundSourceType = .img
        source.source = imgPath
        var seg: AgoraSegmentationProperty?
        if ShowAgoraKitManager.isOpenGreen {
            seg = AgoraSegmentationProperty()
            seg?.modelType = .agoraGreen
            seg?.greenCapacity = greenCapacity
        }
        guard let engine = engine else {
            assert(true, "rtc engine not initlized")
            return
        }
        engine.enableVirtualBackground(isOn, backData: source, segData: seg)
    }
    
    
    /// 预加载
    /// - Parameter preloadRoomList: <#preloadRoomList description#>
    public func preloadRoom(preloadRoomList: [RoomInfo]) {
        videoLoader?.preloadRoom(preloadRoomList: preloadRoomList)
    }
    
    func updateChannelEx(channelId: String, options: AgoraRtcChannelMediaOptions) {
        guard let engine = engine,
              let connection = (broadcasterConnection?.channelId == channelId ? broadcasterConnection : nil) ?? videoLoader?.getConnectionMap()[channelId] else {
            showLogger.error("updateChannelEx fail: connection is empty")
            return
        }
        showLogger.info("updateChannelEx[\(channelId)]: \(options.publishMicrophoneTrack) \(options.publishCameraTrack)")
        engine.updateChannelEx(with: options, connection: connection)
    }
    
    /// 切换连麦角色
    func switchRole(role: AgoraClientRole,
                    channelId: String,
                    options:AgoraRtcChannelMediaOptions,
                    uid: String?,
                    canvasView: UIView?) {
        guard let uid = UInt(uid ?? ""), let canvasView = canvasView else {
            showLogger.error("switchRole fatel")
            return
        }
        options.clientRoleType = role
        options.audienceLatencyLevel = role == .audience ? .lowLatency : .ultraLowLatency
        updateChannelEx(channelId:channelId, options: options)
        if "\(uid)" == VLUserCenter.user.id {
            videoLoader?.leaveChannelWithout(roomId: channelId)
            setupLocalVideo(uid: uid, canvasView: canvasView)
        } else {
            setupRemoteVideo(channelId: channelId, uid: uid, canvasView: canvasView)
        }
    }
    
    func updateMediaOptions(publishCamera: Bool) {
        let mediaOptions = AgoraRtcChannelMediaOptions()
        mediaOptions.publishCameraTrack = publishCamera
        mediaOptions.publishMicrophoneTrack = false
        mediaOptions.clientRoleType = publishCamera ? .broadcaster : .audience
        engine?.updateChannel(with: mediaOptions)
    }
    func updateMediaOptionsEx(channelId: String, publishCamera: Bool, publishMic: Bool = false) {
        let mediaOptions = AgoraRtcChannelMediaOptions()
        mediaOptions.publishCameraTrack = publishCamera
        mediaOptions.publishMicrophoneTrack = publishMic
        mediaOptions.autoSubscribeAudio = publishMic
        mediaOptions.autoSubscribeVideo = publishCamera
        mediaOptions.clientRoleType = publishCamera ? .broadcaster : .audience
        let uid = Int(VLUserCenter.user.id) ?? 0
        let connection = AgoraRtcConnection(channelId: channelId, localUid: uid)
        engine?.updateChannelEx(with: mediaOptions, connection: connection)
    }
    
    /// 设置编码分辨率
    /// - Parameter size: 分辨率
    func setVideoDimensions(_ size: CGSize){
        guard let engine = engine else {
            assert(true, "rtc engine not initlized")
            return
        }
        encoderConfig.dimensions = CGSize(width: size.width, height: size.height)
        engine.setVideoEncoderConfiguration(encoderConfig)
    }
    
    func cleanCapture() {
        guard let engine = engine else {
            assert(true, "rtc engine not initlized")
            return
        }
//        ByteBeautyManager.shareManager.destroy()
//        setupContentInspectConfig(false)
        engine.stopPreview()
        engine.setVideoFrameDelegate(nil)
    }
    
    func leaveChannelEx(roomId: String, channelId: String) {
        if let connection = broadcasterConnection, connection.channelId == channelId {
            engine?.leaveChannelEx(connection)
            broadcasterConnection = nil
            return
        }
        let roomInfo = _getRoomInfo(channelId: channelId)
        videoLoader?.switchRoomState(newState: .idle, roomInfo: roomInfo, tagId: roomId)
    }
    
    func joinChannelEx(currentChannelId: String,
                       targetChannelId: String,
                       ownerId: UInt,
                       options:AgoraRtcChannelMediaOptions,
                       role: AgoraClientRole,
                       completion: (()->())?) {
        if let rtcToken = AppContext.shared.rtcToken {
            _joinChannelEx(currentChannelId: currentChannelId,
                           targetChannelId: targetChannelId,
                           ownerId: ownerId,
                           token: rtcToken,
                           options: options,
                           role: role)
            completion?()
            return
        }
        
        NetworkManager.shared.generateToken(channelName: "",
                                            uid: VLUserCenter.user.id,
                                            tokenType: .token007,
                                            type: .rtc) {[weak self] token in
            defer {
                completion?()
            }
            
            guard let token = token else {
                showLogger.error("joinChannelEx fail: token is empty")
                return
            }
            AppContext.shared.rtcToken = token
            self?._joinChannelEx(currentChannelId: currentChannelId,
                                 targetChannelId: targetChannelId,
                                 ownerId: ownerId,
                                 token: token,
                                 options: options,
                                 role: role)
        }
    }
    
    func setupLocalVideo(uid: UInt, canvasView: UIView) {
        guard let engine = engine else {
            assert(true, "rtc engine not initlized")
            return
        }
        let canvas = AgoraRtcVideoCanvas()
        canvas.view = canvasView
        canvas.uid = uid
        canvas.mirrorMode = .disabled
        engine.setupLocalVideo(canvas)
        engine.startPreview()
        engine.setDefaultAudioRouteToSpeakerphone(true)
        engine.enableAudio()
        engine.enableVideo()
        showLogger.info("setupLocalVideo target uid:\(uid), user uid\(UserInfo.userId)", context: kShowLogBaseContext)
    }
    
    func setupRemoteVideo(channelId: String, uid: UInt, canvasView: UIView) {
        if let connection = broadcasterConnection, broadcasterConnection?.channelId == channelId {
            let videoCanvas = AgoraRtcVideoCanvas()
            videoCanvas.uid = uid
            videoCanvas.view = canvasView
            videoCanvas.renderMode = .hidden
            let ret = engine?.setupRemoteVideoEx(videoCanvas, connection: connection)
                    
            showLogger.info("setupRemoteVideoEx ret = \(ret ?? -1), uid:\(uid) localuid: \(UserInfo.userId) channelId: \(channelId)", context: kShowLogBaseContext)
            return
        }
        let roomInfo = _getRoomInfo(channelId: channelId, uid: uid)
        let container = VideoCanvasContainer()
        container.uid = uid
        container.container = canvasView
        videoLoader?.renderVideo(roomInfo: roomInfo, container: container)
    }
    
    func updateLoadingType(roomId: String, channelId: String, playState: RoomStatus) {
        if broadcasterConnection?.channelId == channelId {return}
        let roomInfo = _getRoomInfo(channelId: channelId)
        videoLoader?.switchRoomState(newState: playState, roomInfo: roomInfo, tagId: roomId)
    }
    
    func cleanChannel(without roomIds: [String]) {
        guard let videoLoader = videoLoader else {return}
        for (key, _) in videoLoader.getConnectionMap() {
            if roomIds.contains(key) {continue}
            let roomInfo = RoomInfo()
            roomInfo.channelName = key
            videoLoader.switchRoomState(newState: .idle, roomInfo: roomInfo, tagId: key)
        }
    }
}

//MARK: private param
extension ShowAgoraKitManager {
    
    func initBroadcasterConfig() {
        engine?.setParameters("{\"rtc.enable_crypto_access\":false}")
        engine?.setParameters("{\"rtc.use_global_location_priority_domain\":true}")
        engine?.setParameters("{\"che.video.has_intra_request\":false}")
        engine?.setParameters("{\"che.hardware_encoding\":1}")
        engine?.setParameters("{\"engine.video.enable_hw_encoder\":true}")
        engine?.setParameters("{\"che.video.keyFrameInterval\":2}")
        engine?.setParameters("{\"che.video.hw265_enc_enable\":1}")
        engine?.setParameters("{\"che.video.enable_first_frame_sw_decode\":true}")
        engine?.setParameters("{\"rtc.asyncCreateMediaEngine\":true}")
    }
    
    func initAudienceConfig() {
        engine?.setParameters("{\"rtc.enable_crypto_access\":false}")
        engine?.setParameters("{\"rtc.use_global_location_priority_domain\":true}")
        engine?.setParameters("{\"che.hardware_decoding\":0}")
        engine?.setParameters("{\"rtc.enable_nasa2\": false}")
        engine?.setParameters("{\"rtc.asyncCreateMediaEngine\":true}")
        engine?.setParameters("{\"che.video.enable_first_frame_sw_decode\":true}")
    }
    
    func initH265Config() {
        engine?.setParameters("{\"che.video.videoCodecIndex\":2}") // 265
    }
    
    func initH264Config() {
        engine?.setParameters("{\"che.video.videoCodecIndex\":1}") //264
        engine?.setParameters("{\"che.video.minQP\":10}")
        engine?.setParameters("{\"che.video.maxQP\":35}")
    }
    
}

extension ShowAgoraKitManager {
    private func _getRoomInfo(channelId: String, uid: UInt? = nil)->RoomInfo {
        let roomInfo = RoomInfo()
        roomInfo.channelName = channelId
        roomInfo.uid = uid ?? (UInt(VLUserCenter.user.id) ?? 0)
        roomInfo.token = AppContext.shared.rtcToken ?? ""
        
        return roomInfo
    }
    
    func setOffMediaOptionsVideo(roomid: String) {
        guard let connection = videoLoader?.getConnectionMap()[roomid] else {
            showLogger.info("setOffMediaOptionsVideo  connection 不存在 \(roomid)")
            return
        }
        showLogger.info("setOffMediaOptionsVideo with roomid = \(roomid)")
        let mediaOptions = AgoraRtcChannelMediaOptions()
        mediaOptions.autoSubscribeVideo = false
        engine?.updateChannelEx(with: mediaOptions, connection: connection)
    }
    
    func setOffMediaOptionsAudio() {
        videoLoader?.getConnectionMap().forEach { _, connention in
            let mediaOptions = AgoraRtcChannelMediaOptions()
            mediaOptions.autoSubscribeAudio = false
            engine?.updateChannelEx(with: mediaOptions, connection: connention)
        }
    }
    
}
// MARK: - IVideoLoaderApiListener
extension ShowAgoraKitManager: IVideoLoaderApiListener {
    public func debugInfo(_ message: String) {
        showLogger.info(message, context: "VideoLoaderApi")
    }
    public func debugWarning(_ message: String) {
        showLogger.warning(message, context: "VideoLoaderApi")
    }
    public func debugError(_ message: String) {
        showLogger.error(message, context: "VideoLoaderApi")
    }
}
// MARK: - AgoraRtcMediaPlayerDelegate
extension ShowAgoraKitManager: AgoraRtcMediaPlayerDelegate {
    
    func AgoraRtcMediaPlayer(_ playerKit: AgoraRtcMediaPlayerProtocol, didChangedTo state: AgoraMediaPlayerState, error: AgoraMediaPlayerError) {
        if state == .openCompleted {
            playerKit.play()
        }
    }
}
