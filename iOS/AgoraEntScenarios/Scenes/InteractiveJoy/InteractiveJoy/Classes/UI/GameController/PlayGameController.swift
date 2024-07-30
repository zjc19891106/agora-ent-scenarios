//
//  PlayGameController.swift
//  InteractiveJoy
//
//  Created by qinhui on 2024/7/26.
//

import UIKit
import AgoraRtcKit
import AgoraCommon

class PlayGameController: UIViewController {
    static let SUDMGP_APP_ID = ""
    static let SUDMGP_APP_KEY = ""
    
    private lazy var navigationBar: GameNavigationBar = {
        let bar = GameNavigationBar()
        return bar
    }()
    
    private var userInfo: InteractiveJoyUserInfo? {
        didSet {
            navigationBar.roomInfoView.startTime(Int64(Date().timeIntervalSince1970 * 1000))
        }
    }
    private var service: JoyServiceProtocol!
    private var roomInfo: InteractiveJoyRoomInfo!
    private lazy var rtcEngine: AgoraRtcEngineKit = _createRtcEngine()

    lazy var gameView: UIView = {
        let view = UIView()
        view.backgroundColor = .purple
        return view
    }()
    
    private func _createRtcEngine() -> AgoraRtcEngineKit {
        let config = AgoraRtcEngineConfig()
        config.appId = joyAppId
        config.channelProfile = .liveBroadcasting
        config.audioScenario = .gameStreaming
        config.areaCode = .global
        let logConfig = AgoraLogConfig()
        logConfig.filePath = AgoraEntLog.sdkLogPath()
        config.logConfig = logConfig
        let engine = AgoraRtcEngineKit.sharedEngine(with: config,
                                                    delegate: self)
        engine.disableVideo()
        engine.enableAudio()
        engine.setVideoEncoderConfiguration(AgoraVideoEncoderConfiguration(size: CGSize(width: 320, height: 240),
                                                                           frameRate: .fps15,
                                                                             bitrate: AgoraVideoBitrateStandard,
                                                                           orientationMode: .fixedPortrait,
                                                                             mirrorMode: .auto))
        
        // set audio profile
        engine.setAudioProfile(.default)
        
        // Set audio route to speaker
        engine.setDefaultAudioRouteToSpeakerphone(true)
        
        // enable volume indicator
        engine.enableAudioVolumeIndication(200, smooth: 3, reportVad: true)
        return engine
    }
    
    let gameHandler: GameEventHandler = GameEventHandler()
    let gameManager: SudGameManager = SudGameManager()
    
    required init(userInfo: InteractiveJoyUserInfo, service: JoyServiceProtocol, roomInfo: InteractiveJoyRoomInfo) {
        super.init(nibName: nil, bundle: nil)
        self.userInfo = userInfo
        self.service = service
        self.roomInfo = roomInfo
        self.service.subscribeListener(listener: self)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.white
        self.view.addSubview(gameView)
        self.view.addSubview(navigationBar)
        navigationBar.moreActionCallback = {
            
        }
        
        navigationBar.closeActionCallback = { [weak self] in
            self?.showEndGameAlert()
        }
        
        gameView.snp.makeConstraints { make in
            make.top.left.right.bottom.equalTo(0)
        }
        
        navigationBar.snp.makeConstraints { make in
            make.left.right.equalTo(0)
            make.height.equalTo(40)
            make.top.equalTo(UIDevice.current.aui_SafeDistanceTop)
        }
        
        gameManager.registerGameEventHandler(gameHandler)
        
        if roomInfo.gameId > 0 {
            loadGame(gameId: roomInfo.gameId)
        }
        
        navigationBar.roomInfoView.setRoomInfo(avatar: userInfo?.avatar, name: roomInfo.roomName, id: roomInfo.roomId)
        renewRTMTokens { [weak self] token in
            guard let self = self else {return}
            
            let option = AgoraRtcChannelMediaOptions()
            option.publishCameraTrack = true
            option.publishMicrophoneTrack = true
            option.clientRoleType = self.roomInfo.ownerId == userInfo!.userId ? .broadcaster : .audience
            let result = self.rtcEngine.joinChannel(byToken: token, channelId: roomInfo.roomId, uid: self.userInfo?.userId ?? 0, mediaOptions: option)
            if result != 0 {
                JoyLogger.error("join channel fail")
            }
        }
    }
    
    private func renewRTMTokens(completion: ((String?)->Void)?) {
        guard let userInfo = userInfo else {
            assert(false, "userInfo == nil")
            JoyLogger.error("renewTokens fail,userInfo == nil")
            completion?(nil)
            return
        }
        JoyLogger.info("renewRTMTokens")
        NetworkManager.shared.generateToken(channelName: "",
                                            uid: "\(userInfo.userId)",
                                            tokenTypes: [.rtc]) {[weak self] token in
            guard let self = self else {return}
            guard let rtmToken = token else {
                JoyLogger.warn("renewRTMTokens fail")
                completion?(nil)
                return
            }
            JoyLogger.info("renewRTMTokens success")
            completion?(rtmToken)
        }
    }
    
    private func loadGame(gameId: Int64) {
        let sudGameConfigModel = SudGameLoadConfigModel()
        sudGameConfigModel.appId = PlayGameController.SUDMGP_APP_ID
        sudGameConfigModel.appKey = PlayGameController.SUDMGP_APP_KEY
        sudGameConfigModel.isTestEnv = true
        sudGameConfigModel.gameId = gameId
        sudGameConfigModel.roomId = roomInfo.roomId
        sudGameConfigModel.language = "zh-CN"
        sudGameConfigModel.gameView = gameView
        sudGameConfigModel.userId = "\(userInfo?.userId)"
     
        gameManager.loadGame(sudGameConfigModel)
    }
    
    private func showEndGameAlert() {
        let alertController = UIAlertController(
            title: "结束玩法",
            message: "退出房间后将关闭",
            preferredStyle: .alert
        )
        
        let confirmAction = UIAlertAction(title: "取消", style: .cancel) { _ in
            
        }
        
        let cancelAction = UIAlertAction(title: "确认", style: .default) { _ in
            self.prepareClose()
        }
        
        alertController.addAction(cancelAction)
        alertController.addAction(confirmAction)

        self.present(alertController, animated: true, completion: nil)
    }
    
    private func prepareClose() {
        handleExitGame()
        service.leaveRoom(roomInfo: roomInfo) { error in
            if let error = error  {
                JoyLogger.info("leave room error:\(error)")
                return
            }
            
            DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(500)) {
                self.gameManager.destroyGame()
                self.disableRtcEngine()
                self.navigationController?.popViewController(animated: true)
            }
        }
    }
    
    private func handleExitGame() {
        guard let userId = userInfo?.userId else {return}
        let currentUserId = "\(userId)"
        if gameHandler.sudFSMMGDecorator.isPlayer(in: currentUserId) {
            // 用户正在游戏中，先退出本局游戏，再退出游戏
            // The user is in the game, first quit the game, and then quit the game
            if gameHandler.sudFSMMGDecorator.isPlayerIsPlaying(currentUserId) {
                gameHandler.sudFSTAPPDecorator.notifyAppComonSelfPlaying(false, reportGameInfoExtras: "")
            }
        } else if gameHandler.sudFSMMGDecorator.isPlayerIsReady(currentUserId) {
            // 准备时，先退出准备
            // When preparing, exit preparation first
            gameHandler.sudFSTAPPDecorator.notifyAppCommonSelfReady(false)
        }
        
        gameHandler.sudFSTAPPDecorator.notifyAppComonSelf(in: false, seatIndex: -1, isSeatRandom: true, teamId: 1)
    }
    
    private func disableRtcEngine() {
        rtcEngine.disableAudio()
        rtcEngine.disableVideo()
        rtcEngine.stopPreview()
        rtcEngine.leaveChannel { (stats) -> Void in
            JoyLogger.info("left channel, duration: \(stats.duration)")
        }
    }
    
}

extension PlayGameController: AgoraRtcEngineDelegate {
    func rtcEngine(_ engine: AgoraRtcEngineKit, tokenPrivilegeWillExpire token: String) {
        guard let userInfo = userInfo else {
            assert(false, "userInfo == nil")
            return
        }
        JoyLogger.info("tokenPrivilegeWillExpire")
    }
}

extension PlayGameController: JoyServiceListenerProtocol {
    func onNetworkStatusChanged(status: JoyServiceNetworkStatus) {}
    
    func onUserListDidChanged(userList: [InteractiveJoyUserInfo]) {}
    
    func onRoomDidDestroy(roomInfo: InteractiveJoyRoomInfo) {
        let alertController = UIAlertController(
            title: "游戏结束",
            message: "房主已解散房间，请确认离开房间",
            preferredStyle: .alert
        )
        
        let confirmAction = UIAlertAction(title: "我知道了", style: .default) { _ in
            self.prepareClose()
        }
        
        alertController.addAction(confirmAction)

        self.present(alertController, animated: true, completion: nil)
    }
}
