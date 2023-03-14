//
//  KTVApi”“.swift
//  AgoraEntScenarios
//
//  Created by CP on 2023/3/6.
//

import Foundation
import AgoraRtcKit
import AgoraLyricsScore

@objc public enum KTVSingRole: Int, Codable {
    case soloSinger = 0
    case coSinger
    case leadSinger
    case audience
}

private enum KTVLoadSongState: Int, Codable {
    case ok
    case inProgress
    case noLyricUrl
    case preloadFail
}

@objc public enum KTVLoadSongFailReason: Int, Codable {
    case noLyricUrl
    case musicPreloadFail
    case musicPreloadFailAndJoinchannelFail
}

@objc public enum KTVPlayerTrackMode: Int, Codable {
    case original
    case acc
}

@objc public enum SwitchRoleState: Int, Codable {
    case success
    case fail
}

@objc public enum SwitchRoleFailReason: Int, Codable {
    case none
    case joinChannelFail
    case musicPreloadFail
    case musicPreloadFailAndJoinchannelFail
    case noPermission
}

@objc public enum KTVJoinChorusFailReason: Int, Codable {
    case musicPreloadFail = 0
    case musicOpenFailed
    case joinChannelFail
    case musicPreloadFailAndJoinchannelFail
}


@objc public protocol KTVApiDelegate: NSObjectProtocol {
    
    //音乐加载成功
    @objc func onMusicLoadSuccess(
        songCode: NSInteger, lyricUrl: String)
    
    //音乐加载失败
    @objc func onMusicLoadFail(
        songCode: NSInteger, lyricUrl: String, reason: KTVLoadSongFailReason)
    
    //歌曲播放状态
    @objc func onMusicPlayerStateChanged(state: AgoraMediaPlayerState, error: AgoraMediaPlayerError, isLocal: Bool)
    
    //歌曲分数回调
    @objc func onSingingScoreResult(score: Int)
    
    //角色切换回调
    @objc func onSingerRoleChanged(oldRole: KTVSingRole, newRole: KTVSingRole)
    
    //跳过前奏
    @objc func didSkipViewShowPreludeEndPosition() -> Void
    
    //跳过尾奏
    @objc func didSkipViewShowEndDuration() -> Void
    
    @objc  func didlrcViewDidScrolled(
        with cumulativeScore: Int,
        totalScore: Int
    ) -> Void
    
    @objc func didlrcViewDidScrollFinished(
        with cumulativeScore: Int,
        totalScore: Int,
        lineScore: Int
    ) -> Void
}

@objc public class KTVSongConfiguration: NSObject {
    @objc var role: KTVSingRole = .audience
    @objc var songCode: NSInteger = 0
    @objc var mainSingerUid: Int = 0
}

@objc public class KTVApiConfig: NSObject {
    @objc public var appId: String
    @objc public var rtmToken: String
    @objc public var engine: AgoraRtcEngineKit
    @objc public var channelName: String
    @objc public var streamId: Int
    /**
     * 当前复用同一个 uid，加入两个 channel，如果不清楚 localUid，子 channel 不清楚用什么 uid
     */
    @objc public var localUid: Int
    @objc public weak var delegate: KTVApiDelegate?
    @objc public var defaultMediaPlayerVolume: Int32 = 50
    @objc public var defaultChorusRemoteUserVolume: Int32 = 15
    @objc init(appId: String, rtmToken: String, engine: AgoraRtcEngineKit, channelName: String, streamId: Int, localUid: Int, delegate: KTVApiDelegate) {
        self.appId = appId
        self.rtmToken = rtmToken
        self.engine = engine
        self.channelName = channelName
        self.streamId = streamId
        self.localUid = localUid
        self.delegate = delegate
    }
}

@objc class KTVApi: NSObject {
    private var apiConfig: KTVApiConfig?
    
    private var songConfig: KTVSongConfiguration?
    private var subChorusConnection: AgoraRtcConnection?
    
    private var loadSongMap = Dictionary<String, KTVLoadSongState>()
    private var lyricUrlMap = Dictionary<String, String>()
    private var loadDict = Dictionary<String, KTVLoadSongState>()
    private var lyricCallbacks = Dictionary<String, LyricCallback>()
    private var musicCallbacks = Dictionary<String, LoadMusicCallback>()
    
    private var lrcView: KaraokeView?
    
    private var localPlayerPosition: TimeInterval = 0
    private var remotePlayerPosition: TimeInterval = 0
    private var remotePlayerDuration: TimeInterval = 0
    private var localPlayerSystemTime: TimeInterval = 0
    private var lastAudienceUpTime: TimeInterval = 0
    private var playerDuration: TimeInterval = 0
    private var isPause: Bool = false
    
    // 合唱校准
    private var audioPlayoutDelay = 0
    private var remoteVolume: Int32 = 15 // 远端音频

    // 音高
    private var pitch = 0.0

    // 是否在麦上
    @objc public var isNowMicMuted = false
    
    private var isJoinSuccess = false
    private var isLoadMusicSuccess = false
    
    private var hasSendPreludeEndPosition = false
    private var hasSendEndPosition = false
    private var mainSingerHasJoinChannelEx: Bool = false
    private var totalLines = 0
    private var totalScore = 0
    private var totalCount = 0
    private var currentLoadUrl = ""
    private var downloadManager: AgoraDownLoadManager = AgoraDownLoadManager()
    
    private var lyricModel: LyricModel?
    private var playerState: AgoraMediaPlayerState = .idle
    
    private typealias LyricCallback = ((String?) -> Void)
    private typealias LoadMusicCallback = ((AgoraMusicContentCenterPreloadStatus) -> Void)
    public typealias OnSwitchRoleStateCallBack = (SwitchRoleState, SwitchRoleFailReason) -> Void
    public typealias MusicChartCallBacks = ([AgoraMusicChartInfo]?) -> Void
    public typealias MusicResultCallBacks = ((AgoraMusicContentCenterStatusCode, AgoraMusicCollection) -> Void)
    public typealias JoinExChannelCallBack = ((Bool, KTVJoinChorusFailReason?)-> Void)
    
    private var musicChartDict: [String: MusicChartCallBacks] = [:]
    private var musicSearchDict: Dictionary<String, MusicResultCallBacks> = Dictionary<String, MusicResultCallBacks>()
    private var onJoinExChannelCallBack : JoinExChannelCallBack?
    
    private var musicPlayer: AgoraMusicPlayerProtocol?
    private var mcc: AgoraMusicContentCenter = AgoraMusicContentCenter()
    private weak var delegate: KTVApiDelegate?
    
    private var timer: Timer?
    //初始化ktvapi
    @objc init(with config: KTVApiConfig) {
        super.init()

        apiConfig = config
        delegate = apiConfig?.delegate
        // ------------------ 初始化内容中心 ------------------
        let contentCenterConfiguration = AgoraMusicContentCenterConfig()
        contentCenterConfiguration.appId = config.appId
        contentCenterConfiguration.mccUid = config.localUid
        contentCenterConfiguration.token = config.rtmToken
        contentCenterConfiguration.rtcEngine = config.engine
 
        mcc = AgoraMusicContentCenter.sharedContentCenter(config: contentCenterConfiguration)

        // ------------------ 初始化音乐播放器实例 ------------------
        musicPlayer = mcc.createMusicPlayer(delegate: self)
        
        // 音量最佳实践调整
        musicPlayer?.adjustPlayoutVolume(config.defaultMediaPlayerVolume)
        musicPlayer?.adjustPublishSignalVolume(config.defaultMediaPlayerVolume)
        remoteVolume = config.defaultChorusRemoteUserVolume
        
        downloadManager.delegate = self
        
        initTimer()
        
    }
    
    private func initTimer() {
        if timer != nil {
            return
        }
        
        guard let role = songConfig?.role else {return}
        timer = Timer.scheduledTimer(withTimeInterval: 0.02, block: {[weak self] timer in
            let current = self?.getPlayerCurrentTime()
            if role == .audience && (Date().milListamp - (self?.lastAudienceUpTime ?? 0)) > 1000 {
                return
            }
            self?.setProgress(with: Int(current ?? 0))
            guard let lyricModel = self?.lyricModel else {
                assert(false)
                return
            }
            guard let delegate = self?.delegate else {return}
            if role == .soloSinger || role == .leadSinger {
                if (Int(current ?? 0) + 500) >= lyricModel.preludeEndPosition && self?.hasSendPreludeEndPosition == false {
                    self?.hasSendPreludeEndPosition = true
                    delegate.didSkipViewShowPreludeEndPosition()
                } else if Int(current ?? 0) >= lyricModel.duration && self?.hasSendEndPosition == false {
                    self?.hasSendEndPosition = true
                    delegate.didSkipViewShowEndDuration()
                }
            }
        }, repeats: true)
    }
    
    //释放ktvapi
    @objc func clean() {
        freeTimer()
        downloadManager.delegate = nil
        apiConfig?.engine.setAudioFrameDelegate(nil)
        lyricCallbacks.removeAll()
        musicCallbacks.removeAll()
        self.delegate = nil
    }
    
    /**
     * 加载歌曲
     */
    @objc public func loadSong(autoPlay: Bool,config: KTVSongConfiguration) {
        songConfig = config
        let role = config.role
        let songCode = config.songCode
        
        guard let delegate = self.delegate else {return}
        
        if (loadDict.keys.contains(String(songCode))) {
            let loadState = loadDict[String(songCode)]
            if loadState == .ok {
                if let url = lyricUrlMap[String(songCode)] {
                    delegate.onMusicLoadSuccess(songCode: songCode, lyricUrl: url)
                    return
                }
            }
        }
        
        loadDict.updateValue(.inProgress, forKey: String(songCode))
        var state: KTVLoadSongState = .inProgress
            
        let KTVGroup = DispatchGroup()
        let KTVQueue = DispatchQueue(label: "com.agora.ktv.www")

        if role != .audience  {
            KTVGroup.enter()
            KTVQueue.async { [weak self] in
                self?.loadLyric(with: songCode, callBack: { url in
                    if let urlPath = url, urlPath.count > 0 {
                        self?.lyricUrlMap.updateValue(urlPath, forKey: String(songCode))
                        self?.setLyric(with: urlPath, callBack: { lyricUrl in
                            KTVGroup.leave()
                        })
                    } else {
                        self?.loadDict.removeValue(forKey: String(songCode))
                        state = .noLyricUrl
                        KTVGroup.leave()
                    }
                })
            }
            
            KTVGroup.enter()
            KTVQueue.async {[weak self] in
                self?.loadMusic(with: songCode, callBaclk: { status in
                    if status != .OK {
                        self?.loadDict.removeValue(forKey: String(songCode))
                        state = .preloadFail
                    }
                    KTVGroup.leave()
                })
                
            }
        } else {
            KTVGroup.enter()
            KTVQueue.async { [weak self] in
                self?.loadLyric(with: songCode, callBack: { url in
                    if let urlPath = url, urlPath.count > 0 {
                        self?.lyricUrlMap.updateValue(urlPath, forKey: String(songCode))
                        self?.setLyric(with: urlPath, callBack: { lyricUrl in
                            KTVGroup.leave()
                        })
                    } else {
                        self?.loadDict.removeValue(forKey: String(songCode))
                        state = .noLyricUrl
                        KTVGroup.leave()
                    }
                    
                })
            }
        }

        KTVGroup.notify(queue: .main) { [weak self] in
            if state == .inProgress {
                self?.loadDict.updateValue(.ok, forKey: String(songCode))
                state = .ok
            }
            
            if let url = self?.lyricUrlMap[String(songCode)] {
                if role == .soloSinger && autoPlay == true {
                    self?.startSing()
                }
                delegate.onMusicLoadSuccess(songCode: songCode, lyricUrl: url)
            }
            
        }
        
    }
    
    private func loadLyric(with songCode: NSInteger, callBack:@escaping LyricCallback) {
        let requestId: String = self.mcc.getLyric(songCode: songCode, lyricType: 0)
//        if requestId.count == 0 {
//            callBack(nil)
//            return
//        }
        self.lyricCallbacks.updateValue(callBack, forKey: requestId)
    }
    
    private func loadMusic(with songCode: NSInteger, callBaclk:@escaping LoadMusicCallback) {
        var err = self.mcc.isPreloaded(songCode: songCode)
        if err == 0 {
            musicCallbacks.removeValue(forKey: String(songCode))
            callBaclk(.OK)
            return
        }
        
        err = self.mcc.preload(songCode: songCode, jsonOption: nil)
        if err != 0 {
            musicCallbacks.removeValue(forKey: String(songCode))
            callBaclk(.error)
            return
        }
        musicCallbacks.updateValue(callBaclk, forKey: String(songCode))
    }
    
    private func setLyric(with url: String, callBack:@escaping LyricCallback) {
        if self.lyricCallbacks.keys.contains(url) {
            self.lyricCallbacks.updateValue(callBack, forKey: url)
        }
        
        hasSendPreludeEndPosition = false
        hasSendEndPosition = false
        totalLines = 0
        totalScore = 0
        currentLoadUrl = url
        
        let isLocal = url.hasSuffix(".zip")
        downloadManager.downloadLrcFile(urlString: url) {[weak self] lrcurl in
            var path: String? = nil
            defer{
                callBack(path)
            }
            
            guard let lrcurl = lrcurl else {return}
            let curStr: String = url.components(separatedBy: "/").last ?? ""
            let loadStr: String = lrcurl.components(separatedBy: "/").last ?? ""
            let curSongStr: String = curStr.components(separatedBy: ".").first ?? ""
            let loadSongStr: String = loadStr.components(separatedBy: ".").first ?? ""
            if curSongStr != loadSongStr {
                return
            }
            
            var musicUrl: URL
            if isLocal {
                let loadUrl: URL = URL(fileURLWithPath: lrcurl)
                musicUrl = loadUrl
            } else {
                guard let loadUrl: URL = URL(string: lrcurl) else {return}
                musicUrl = loadUrl
            }

            guard let data = try? Data(contentsOf: musicUrl) else {return}
            guard let model: LyricModel = KaraokeView.parseLyricData(data: data) else {return}
            self?.lyricModel = model
            
            self?.lrcView?.setLyricData(data: model)
            self?.totalCount = model.lines.count
            path = lrcurl
            
        } failure: {
            callBack(nil)
            print("歌词解析失败")
        }

    }
    
    @objc public func startSing() {
        let role = songConfig?.role
        print("playSong called: $singerRole")
        if role == .soloSinger {
            apiConfig?.engine.adjustPlaybackSignalVolume(Int(remoteVolume))
            musicPlayer?.openMedia(songCode: songConfig?.songCode ?? 0, startPos: 0)
        } else {
            print("Wrong role playSong, you are not mainSinger right now!")
        }
    }
    
    /**
     * 停止播放歌曲
     */
    @objc public func stopSing() {
        mainSingerHasJoinChannelEx = false
        
        let mediaOption = AgoraRtcChannelMediaOptions()
        mediaOption.autoSubscribeAudio = true
        mediaOption.autoSubscribeVideo = true
        mediaOption.publishMediaPlayerAudioTrack = false
        apiConfig?.engine.updateChannel(with: mediaOption)
        
        if musicPlayer?.getPlayerState() != .stopped {
            musicPlayer?.stop()
        }
        leaveChorus2ndChannel()
        apiConfig?.engine.setAudioScenario(.gameStreaming)
    }
    
    
    /**
     * 恢复播放
     */
    @objc public func resumeSing() {
        musicPlayer?.resume()
    }
    
    /**
     * 暂停播放
     */
    @objc public func pauseSing() {
        musicPlayer?.pause()
    }
    
    /**
     * 调整进度
     */
    @objc public func seekSing(time: NSInteger) {
        musicPlayer?.seek(toPosition: time)
    }
    
    /**
     * 设置歌词组件，在任意时机设置都可以生效
     */
    @objc public func setLycView(view: KaraokeView) {
        lrcView = view
        lrcView?.delegate = self
    }
    
    /**
     * 选择音轨，原唱、伴唱
     */
    @objc public func selectPlayerTrackMode(mode: KTVPlayerTrackMode) {
        apiConfig?.engine.selectAudioTrack(mode == .original ? 0 : 1)
    }
    
    /**
     * 设置听到播放的所有音频的音量
     */
    @objc public func adjustRemoteVolume(volume: Int32) {
        remoteVolume = volume
        if musicPlayer?.getPlayerState() != .playing {
            apiConfig?.engine.adjustPlaybackSignalVolume(Int(volume))
        }
    }

    /**
     * 设置当前mic开关状态
     */
    @objc public func setMicStatus(isOnMicOpen: Bool) {
        self.isNowMicMuted = !isOnMicOpen
    }

    /**
     * 获取mpk实例
     */
    @objc public func getMusicPlayer() -> AgoraMusicPlayerProtocol? {
        return musicPlayer
    }
    
    @objc public func getMusicCenter() -> AgoraMusicContentCenter {
        return mcc
    }
    
    @objc public func skip() {
        guard let preludeEndPosition: Int = lyricModel?.preludeEndPosition else {return}
        guard let EndPosition: Int = lyricModel?.duration else {return}
        guard let currentPosition: Int = musicPlayer?.getPosition() else {return}
        if currentPosition < (preludeEndPosition  - 500) {
            musicPlayer?.seek(toPosition: (preludeEndPosition - 500))
        } else if currentPosition > (EndPosition - 500) {
            musicPlayer?.seek(toPosition: EndPosition - 500)
        }
    }
    
    @objc public func setProgress(with pos: NSInteger) {
        lrcView?.setPitch(pitch: pitch, progress: pos)
    }
    
    private func setPlayerState(with state: AgoraMediaPlayerState) {
        playerState = state
        updateRemotePlayBackVolumeIfNeed()
        updateTimer(with: state)
    }
    
    private func updateRemotePlayBackVolumeIfNeed() {
        let role = songConfig?.role
        if role == .audience {
            apiConfig?.engine.adjustPlaybackSignalVolume(100)
            return
        }
        
        let vol = self.playerState == .playing ? remoteVolume : 100
        apiConfig?.engine.adjustPlaybackSignalVolume(Int(vol))
    }
    
    private func updateTimer(with state: AgoraMediaPlayerState) {
        DispatchQueue.main.async {
            if state == .paused || state == .stopped {
                self.pauseTimer()
            } else if state == .playing {
                self.startTimer()
            }
        }
    }
    
    //timer method
    private func startTimer() {
        guard let timer = self.timer else {return}
        if isPause == false {
            RunLoop.current.add(timer, forMode: .common)
            self.timer?.fire()
        } else {
            resumeTimer()
        }
    }
    
    private func resumeTimer() {
        if isPause == false {return}
        isPause = false
        timer?.fireDate = Date()
    }
    
    private func pauseTimer() {
        if isPause == true {return}
        isPause = true
        timer?.fireDate = Date.distantFuture
    }
    
    private func freeTimer() {
        guard let _ = self.timer else {return}
        self.timer?.invalidate()
        self.timer = nil
    }
    
    private func getPlayerCurrentTime() -> TimeInterval {
        let role = songConfig?.role
        if role == .soloSinger || role == .coSinger || role == .leadSinger{
            let time = Date().milListamp - localPlayerPosition
            return time
        }
        return Date().milListamp - remotePlayerPosition
    }
    
    private func updateCosingerPlayerStatusIfNeed() {
        let role = songConfig?.role
        if role == .coSinger {
            if playerState == .stopped {
                stopSong()
            } else if playerState == .paused {
                pausePlay()
            } else if playerState == .playing {
                resumePlay()
            }
        }
    }
    
    private func pausePlay() {
        musicPlayer?.pause()
    }
    
    @objc public func stopSong() {
        musicPlayer?.stop()
        leaveChorus2ndChannel()
        
        lrcView?.reset()
        self.songConfig = nil
        apiConfig?.engine.setAudioScenario(.gameStreaming)
    }
    
    @objc public func resumePlay() {
        if musicPlayer?.getPlayerState() == .paused {
            musicPlayer?.resume()
        } else {
            musicPlayer?.play()
        }
    }
    
    /**
     * 获取歌曲类型
     */
    @objc public func fetchMusicChart(callBack:@escaping MusicChartCallBacks) {
        let requestId = mcc.getMusicCharts()
        musicChartDict[requestId] = callBack
    }
    

    /**
     * 搜索歌曲
     */
    @objc public func searchMusic(keyword: String, page: Int, pageSize: Int, jsonOption: String) -> String {
        return mcc.searchMusic(keyWord: keyword, page: page, pageSize: pageSize, jsonOption: jsonOption)
    }
    
    @objc public func getAvgSongScore() -> Int {
        if self.totalLines <= 0 {
            return 0
        } else {
            return self.totalScore / self.totalLines
        }
        
    }
}

extension KTVApi: AgoraRtcEngineDelegate, AgoraAudioFrameDelegate {
    func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinChannel channel: String, withUid uid: UInt, elapsed: Int) {
        if songConfig?.role == .leadSinger {
            mainSingerHasJoinChannelEx = true
            onJoinExChannelCallBack?(true, nil)
        }
        if songConfig?.role == .coSinger {
            didCoSingerLoadMusic(with: songConfig?.songCode ?? 0) { status in
                if status == .OK {
                    self.onJoinExChannelCallBack?(true, nil)
                } else {
                    self.onJoinExChannelCallBack?(false, .musicPreloadFail)
                }
            }
        }
        
    }
    
    func rtcEngine(_ engine: AgoraRtcEngineKit, didLeaveChannelWith stats: AgoraChannelStats) {
        engine.setAudioScenario(.gameStreaming)
        if songConfig?.role == .leadSinger {
            mainSingerHasJoinChannelEx = false
            onJoinExChannelCallBack?(false, .joinChannelFail)
        }
        
        if songConfig?.role == .coSinger {
            didCoSingerLoadMusic(with: songConfig?.songCode ?? 0) { status in
                if status == .OK {
                    self.onJoinExChannelCallBack?(false, .joinChannelFail)
                } else {
                    self.onJoinExChannelCallBack?(false, .musicPreloadFailAndJoinchannelFail)
                }
            }
        }
    }
    
    func onRecordAudioFrame(_ frame: AgoraAudioFrame, channelId: String) -> Bool {
        if mainSingerHasJoinChannelEx == true {
            guard let buffer = frame.buffer else {return false}
            apiConfig?.engine.pushDirectAudioFrameRawData(buffer, samples: frame.channels*frame.samplesPerChannel, sampleRate: frame.samplesPerSec, channels: frame.channels)
        }
        return true
    }
}

extension KTVApi {
    @objc public func didKTVAPIReceiveStreamMessageFrom( uid: NSInteger, streamId: NSInteger, data: Data){
        let songCode: Int = songConfig?.songCode ?? 0
        let role = songConfig?.role
        guard let state: KTVLoadSongState = loadDict[String(songCode)] else {return}
        if state != .ok {return}
        
        guard let dict = dataToDictionary(data: data) else {return}
        if isMainSinger() {return}
        
        guard let delegate = self.delegate else {return}
        if dict.keys.contains("cmd") {
            if dict["cmd"] == "setLrcTime" {
                let position = Double(dict["time"] ?? "0") ?? 0
                let duration = Double(dict["duration"] ?? "0") ?? 0
                let remoteNtp = Int(dict["ntp"] ?? "0") ?? 0
                let voicePitch = Double(dict["pitch"] ?? "0") ?? 0
            
                let mainSingerState: Int = Int(dict["state"] ?? "0") ?? 0
                let state = AgoraMediaPlayerState(rawValue: mainSingerState) ?? .stopped
                if (self.playerState != state) {
                    print("recv state with setLrcTime : \(state.rawValue)")
                    self.playerState = state
                    updateCosingerPlayerStatusIfNeed()
                    delegate.onMusicPlayerStateChanged(state: state, error: .none, isLocal: false)
                }
                self.remotePlayerPosition = Date().milListamp - position
                self.remotePlayerDuration = duration
                if (role == .audience) {
                    self.lastAudienceUpTime = Date().milListamp
                }
                
                if role == .coSinger {
                    if musicPlayer?.getPlayerState() == .playing {
                        let localNtpTime = getNtpTimeInMs()
                        let localPosition = Date().milListamp - self.localPlayerPosition
                        let expectPosition = Int(position) + localNtpTime - remoteNtp + self.audioPlayoutDelay
                        let threshold = expectPosition - Int(localPosition)
                        if(abs(threshold) > 40) {
                            musicPlayer?.seek(toPosition: expectPosition)
                            print("progress: setthreshold: \(threshold) expectPosition: \(expectPosition) position: \(position), localNtpTime: \(localNtpTime), remoteNtp: \(remoteNtp), audioPlayoutDelay: \(self.audioPlayoutDelay), localPosition: \(localPosition)")
                        }
                    }
                } else if role == .audience {
                    self.pitch = voicePitch
                }
                
            } else if dict["cmd"] == "PlayerState" {
                let mainSingerState: Int = Int(dict["state"] ?? "0") ?? 0
                self.playerState = AgoraMediaPlayerState(rawValue: mainSingerState) ?? .idle
                
                updateCosingerPlayerStatusIfNeed()
                delegate.onMusicPlayerStateChanged(state: self.playerState, error: .none, isLocal: false)
            } else if dict["cmd"] == "setVoicePitch" {
                if role == .coSinger {return}
                //同步新的pitch策略 todo
            }
        }
    }
    
    @objc public func didKTVAPIReceiveAudioVolumeIndication(with speakers: [AgoraRtcAudioVolumeInfo], totalVolume: NSInteger) {
        if playerState != .playing {return}
        if songConfig?.role == .audience {return}
        
        let pitch = isNowMicMuted ? 0 : speakers.first?.voicePitch
        self.pitch = pitch ?? 0
        lrcView?.setPitch(pitch: self.pitch, progress: Int(getPlayerCurrentTime()))
        
        if isMainSinger()  {return}
    }
    
    @objc public func didKTVAPILocalAudioStats(stats: AgoraRtcLocalAudioStats) {
        audioPlayoutDelay = Int(stats.audioPlayoutDelay)
    }
    
    private func dataToDictionary(data: Data) -> Dictionary<String, String>? {
        let json = try? JSONSerialization.jsonObject(with: data, options: .mutableContainers)
        let dict = json as? Dictionary<String, String>
        return dict
    }
    
    private func compactDictionaryToData(_ dict: NSDictionary) -> Data? {
        guard JSONSerialization.isValidJSONObject(dict) else { return nil }
        guard let jsonData = try? JSONSerialization.data(withJSONObject: dict, options: []) else { return nil }
        return jsonData
    }
    
    private func getNtpTimeInMs() -> Int {
        var localNtpTime: Int = apiConfig?.engine.getNtpTimeInMs() ?? 0
        
        if localNtpTime != 0 {
            localNtpTime -= 2208988800 * 1000
        } else {
            localNtpTime = Int(round(Date().timeIntervalSince1970 * 1000.0))
        }
        
        return localNtpTime
    }
    
    private func syncPlayState(state: AgoraMediaPlayerState) {
        let dict: [String: Any] = ["cmd": "PlayerState", "userId": apiConfig?.localUid as Any, "state": "\(state.rawValue)"]
        sendStreamMessageWithDict(dict, success: nil)
    }
    
    private func sendStreamMessageWithDict(_ dict: [String: Any], success: ((_ success: Bool) -> Void)?) {
        let messageData = compactDictionaryToData(dict as NSDictionary)
        let code = apiConfig?.engine.sendStreamMessage(apiConfig?.streamId ?? 0, data: messageData ?? Data())
        if code == 0 && success != nil { success!(true) }
        if code != 0 {
            print("sendStreamMessage fail: %d\n",code as Any)
        }
    }
    
    private func syncPlayState(_ state: AgoraMediaPlayerState) {
        let dict: [String: Any] = [ "cmd": "PlayerState", "userId": apiConfig?.localUid as Any, "state": "\(state.rawValue)" ]
        sendStreamMessageWithDict(dict, success: nil)
    }
}

extension KTVApi: AgoraRtcMediaPlayerDelegate {
    
    func agoraRtcMediaPlayer(_ playerKit: AgoraRtcMediaPlayerProtocol, didChangedToPosition position: Int) {
        
        self.localPlayerPosition = Date().milListamp - Double(position)
        if isMainSinger() && position > self.audioPlayoutDelay {
            if isMainSinger()  { //if i am main singer
                let dict: [String: Any] = [ "cmd": "setLrcTime",
                                            "duration": self.playerDuration,
                                            "time": position - self.audioPlayoutDelay,
                                            //不同机型delay不同，需要发送同步的时候减去发送机型的delay，在接收同步加上接收机型的delay
                                            "ntp": self.getNtpTimeInMs(),
                                            "pitch": self.pitch,
                                            "playerState": self.playerState.rawValue
                ]
                sendStreamMessageWithDict(dict, success: nil)
            }
//            guard let delegate = self.delegate else {return}
//            delegate.onSyncMusicPosition(position: position, pitch: Float(pitch))
        }
    }
    
    func agoraRtcMediaPlayer(_ playerKit: AgoraRtcMediaPlayerProtocol, didChangedTo state: AgoraMediaPlayerState, error: AgoraMediaPlayerError) {
        guard let delegate = self.delegate else {return}
        let role = songConfig?.role
        if state == .openCompleted {
            print("loadSong play completed \(String(describing: songConfig?.songCode))")
            self.localPlayerPosition = Date().milListamp
            self.playerDuration = 0
            if isMainSinger() { //主唱播放，通过同步消息“setLrcTime”通知伴唱play
                playerKit.play()
            }
        } else if state == .stopped {
            self.localPlayerPosition = Date().milListamp
            self.playerDuration = 0
            self.remotePlayerPosition = Date().milListamp - 0
        } else if state == .playing {
            self.localPlayerPosition = Date().milListamp - Double(musicPlayer?.getPosition() ?? 0)
            self.playerDuration = 0
        } else if state == .paused {
            self.remotePlayerPosition = Date().milListamp
        } else if state == .playing {
            self.localPlayerPosition = Date().milListamp - Double(musicPlayer?.getPosition() ?? 0)
            self.playerDuration = 0
            self.remotePlayerPosition = Date().milListamp
        }
        
        if isMainSinger() {
            syncPlayState(state: state)
        }
        self.playerState = state
        print("recv state with player callback : \(state.rawValue)")
        delegate.onMusicPlayerStateChanged(state: state, error: .none, isLocal: true)
    }
    
    private func isMainSinger() -> Bool {
        return songConfig?.role == .soloSinger || songConfig?.role == .leadSinger
    }
}

extension KTVApi: AgoraMusicContentCenterEventDelegate {
    func onMusicChartsResult(_ requestId: String, status: AgoraMusicContentCenterStatusCode, result: [AgoraMusicChartInfo]) {
        guard let callback = musicChartDict[requestId] else {return}
        callback(result)
        musicChartDict.removeValue(forKey: requestId)
        
    }
    
    func onMusicCollectionResult(_ requestId: String, status: AgoraMusicContentCenterStatusCode, result: AgoraMusicCollection) {
        guard let callback = musicSearchDict[requestId] else {return}
        callback(status, result)
        musicSearchDict.removeValue(forKey: requestId)
        
    }
    
    func onLyricResult(_ requestId: String, lyricUrl: String) {
        let callback = self.lyricCallbacks[requestId]
        guard let lyricCallback = callback else { return }
        self.lyricCallbacks.removeValue(forKey: requestId)
        if lyricUrl.isEmpty {
            lyricCallback(nil)
            return
        }
        lyricCallback(lyricUrl)
    }
    
    func onPreLoadEvent(_ songCode: Int, percent: Int, status: AgoraMusicContentCenterPreloadStatus, msg: String, lyricUrl: String) {
        if (status == .preloading) { return }
        let SongCode = "\(songCode)"
        guard let block = self.musicCallbacks[SongCode] else { return }
        self.musicCallbacks.removeValue(forKey: SongCode)
        block(status)
    }
}

extension KTVApi: KaraokeDelegate {
    func onKaraokeView(view: KaraokeView, didDragTo position: Int) {
        musicPlayer?.seek(toPosition: position)
        totalScore = view.scoringView.getCumulativeScore()
        //将分数传到vc
        guard let delegate = self.delegate else {return}
        delegate.didlrcViewDidScrolled(with: self.totalScore, totalScore: self.totalCount * 100)
    }
    
    func onKaraokeView(view: KaraokeView, didFinishLineWith model: LyricLineModel, score: Int, cumulativeScore: Int, lineIndex: Int, lineCount: Int) {
        self.totalLines = lineCount
        self.totalScore = cumulativeScore
        //将分数传到vc
        guard let delegate = self.delegate else {return}
        delegate.didlrcViewDidScrollFinished(with: self.totalScore, totalScore: lineCount * 100, lineScore: score)
    }
}

extension KTVApi: AgoraLrcDownloadDelegate {
    
    func downloadLrcFinished(url: String) {
        print("download lrc finished \(url)")
        guard let callback = self.lyricCallbacks[url] else { return }
        self.lyricCallbacks.removeValue(forKey: url)
        callback(url)
    }
    
    func downloadLrcError(url: String, error: Error?) {
        print("download lrc fail \(url): \(String(describing: error))")
        guard let callback = self.lyricCallbacks[url] else { return }
        self.lyricCallbacks.removeValue(forKey: url)
        callback(nil)
    }
}

extension KTVApi {
    @objc public func switchSingerRole(newRole: KTVSingRole, token: String, stateCallBack:@escaping OnSwitchRoleStateCallBack) { print("switchSingerRole oldRole: (singerRole), newRole: (newRole)")
        let oldRole = songConfig?.role
        guard let delegate = apiConfig?.delegate else {return}
        if oldRole == .audience && newRole == .soloSinger {
            // 1、KTVSingRoleAudience -》KTVSingRoleMainSinger
            songConfig?.role = newRole
            becomeSoloSinger()
            delegate.onSingerRoleChanged(oldRole: .audience, newRole: .soloSinger)
            stateCallBack(.success, .none)
        } else if oldRole == .audience && newRole == .leadSinger {
            becomeSoloSinger()
            joinChorus(token: apiConfig?.rtmToken ?? "", joinExChannelCallBack: { flag, status in
                if flag == true {
                    self.songConfig?.role = newRole
                    stateCallBack(.success, .none)
                } else {
                    self.leaveChorus()
                    if status == .musicPreloadFail {
                        stateCallBack(.fail, .musicPreloadFail)
                    } else if status == .joinChannelFail {
                        stateCallBack(.fail, .joinChannelFail)
                    } else if status == .musicPreloadFailAndJoinchannelFail {
                        stateCallBack(.fail, .musicPreloadFailAndJoinchannelFail)
                    }
                }
            })
           
        } else if oldRole == .soloSinger && newRole == .audience {
            stopSing()
            songConfig?.role = newRole
            delegate.onSingerRoleChanged(oldRole: .soloSinger, newRole: .audience)
            stateCallBack(.success, .none)
        } else if oldRole == .audience && newRole == .coSinger {
            joinChorus(token: apiConfig?.rtmToken ?? "", joinExChannelCallBack: { flag, status in
                if flag == true {
                    self.songConfig?.role = newRole
                    stateCallBack(.success, .none)
                } else {
                    self.leaveChorus()
                    if status == .musicPreloadFail {
                        stateCallBack(.fail, .musicPreloadFail)
                    } else if status == .joinChannelFail {
                        stateCallBack(.fail, .joinChannelFail)
                    } else if status == .musicPreloadFailAndJoinchannelFail {
                        stateCallBack(.fail, .musicPreloadFailAndJoinchannelFail)
                    }
                }
            })
        } else if oldRole == .coSinger && newRole == .audience {
            leaveChorus()
            songConfig?.role = newRole
            delegate.onSingerRoleChanged(oldRole: .coSinger, newRole: .audience)
            stateCallBack(.success, .none)
        } else if oldRole == .soloSinger && newRole == .leadSinger {
            joinChorus(token: apiConfig?.rtmToken ?? "", joinExChannelCallBack: { flag, status in
                if flag == true {
                    self.songConfig?.role = newRole
                    stateCallBack(.success, .none)
                } else {
                    self.leaveChorus()
                    if status == .musicPreloadFail {
                        stateCallBack(.fail, .musicPreloadFail)
                    } else if status == .joinChannelFail {
                        stateCallBack(.fail, .joinChannelFail)
                    } else if status == .musicPreloadFailAndJoinchannelFail {
                        stateCallBack(.fail, .musicPreloadFailAndJoinchannelFail)
                    }
                }
            })
        } else if oldRole == .leadSinger && newRole == .soloSinger {
            leaveChorus()
            songConfig?.role = newRole
            delegate.onSingerRoleChanged(oldRole: .leadSinger, newRole: .soloSinger)
            stateCallBack(.success, .none)
        } else if oldRole == .leadSinger && newRole == .audience {
            stopSing()
            songConfig?.role = newRole
            delegate.onSingerRoleChanged(oldRole: .leadSinger, newRole: .audience)
            stateCallBack(.success, .none)
        } else {
            stateCallBack(.fail, .noPermission)
            print("Error！You can not switch role from $singerRole to $newRole!")
        }
        
    }
    
    private func becomeSoloSinger() {
        let mediaOption = AgoraRtcChannelMediaOptions()
        mediaOption.autoSubscribeAudio = true
        mediaOption.autoSubscribeVideo = true
        mediaOption.publishMediaPlayerId = Int(musicPlayer?.getMediaPlayerId() ?? 0)
        mediaOption.publishMediaPlayerAudioTrack = true
        apiConfig?.engine.updateChannel(with: mediaOption)
        apiConfig?.engine.setDirectExternalAudioSource(true)
        apiConfig?.engine.setRecordingAudioFrameParametersWithSampleRate(48000, channel: 2, mode: .readOnly, samplesPerCall: 960)
        apiConfig?.engine.setAudioFrameDelegate(self)
    }
    
    /**
     * 加入合唱
     */
    private func joinChorus(token: String, joinExChannelCallBack: @escaping JoinExChannelCallBack) {
        guard let oldConfig = songConfig else { return}

        let role = oldConfig.role
        let songCode = oldConfig.songCode
        self.onJoinExChannelCallBack = joinExChannelCallBack
        if role == .leadSinger {
            print("joinChorus: KTVSingRoleMainSinger")
            joinChorus2ndChannel(token: token)
        } else if role == .coSinger {
            let mediaOption = AgoraRtcChannelMediaOptions()
            mediaOption.autoSubscribeAudio = true
            mediaOption.autoSubscribeVideo = true
            mediaOption.publishMediaPlayerAudioTrack = false
            apiConfig?.engine.updateChannel(with: mediaOption)
            joinChorus2ndChannel(token: token)
        }
//        else if role == .followSinger {
//            print("joinChorus: KTVSingRoleCoSinger")
//            let mediaOption = AgoraRtcChannelMediaOptions()
//            mediaOption.autoSubscribeAudio = true
//            mediaOption.autoSubscribeVideo = true
//            mediaOption.publishMediaPlayerAudioTrack = false
//            apiConfig?.engine.updateChannel(with: mediaOption)
//            joinChorus2ndChannel(token: token)
//
//            //todo 加个歌曲下载
//            loadMusic(with: songCode) { status in
//                if status == .OK {
//                    self.apiConfig?.engine.adjustPlaybackSignalVolume(Int(self.remoteVolume))
//                    self.musicPlayer?.openMedia(songCode: songCode, startPos: 0)
//                    self.isLoadMusicSuccess = true
//                }
//            }
//
//            // 音量最佳实践调整
//
//            apiConfig?.engine.adjustPlaybackSignalVolume(Int(remoteVolume))
//            apiConfig?.engine.muteLocalAudioStream(true)
//
//            musicPlayer?.openMedia(songCode: songCode, startPos: 0)
//            if mainSingerHasJoinChannelEx == true && isLoadMusicSuccess {
//                delegate?.onJoinChorusState(reason: .success)
//            }
//        }
        else if role == .audience {
            print("i am a audience")
        }
    }
    
    private func didCoSingerLoadMusic(with songCode: NSInteger, callBaclk:@escaping LoadMusicCallback) {
        loadMusic(with: songCode) { status in
            callBaclk(status)
            if status == .OK {
                self.apiConfig?.engine.adjustPlaybackSignalVolume(Int(self.remoteVolume))
                self.musicPlayer?.openMedia(songCode: songCode, startPos: 0)
            }
        }

        // 音量最佳实践调整
        apiConfig?.engine.adjustPlaybackSignalVolume(Int(remoteVolume))

        musicPlayer?.openMedia(songCode: songCode, startPos: 0)
    }
    
    private func joinChorus2ndChannel(token: String) {
        
        guard let config = songConfig else {return}
        let role = config.role
        if role == .soloSinger || role != .audience {
            print("joinChorus2ndChannel with wrong role")
            return
        }
        
       apiConfig?.engine.setAudioScenario(.chorus)

       
       let mediaOption = AgoraRtcChannelMediaOptions()
       // main singer do not subscribe 2nd channel
       // co singer auto sub
        mediaOption.autoSubscribeAudio = role != .leadSinger
        mediaOption.autoSubscribeVideo = false
        mediaOption.publishMicrophoneTrack = false
        mediaOption.enableAudioRecordingOrPlayout = role != .leadSinger
        mediaOption.clientRoleType = .broadcaster
        mediaOption.publishDirectCustomAudioTrack = role == .leadSinger

       let rtcConnection = AgoraRtcConnection()
        rtcConnection.channelId = (apiConfig?.channelName ?? "") + "_ex"
        rtcConnection.localUid = UInt(apiConfig?.localUid ?? 0)
       subChorusConnection = rtcConnection
        
        apiConfig?.engine.joinChannelEx(byToken: token, connection: rtcConnection, delegate: self, mediaOptions: mediaOption, joinSuccess: nil)

        if config.role == .coSinger {
            apiConfig?.engine.muteRemoteAudioStream(UInt(songConfig?.mainSingerUid ?? 0), mute: true)
       }
    }
    
    private func leaveChorus2ndChannel() {
        guard let config = songConfig else {return}
        let role = config.role
        guard let subConn = subChorusConnection else {return}
        if (role == .leadSinger) {
            let mediaOption = AgoraRtcChannelMediaOptions()
            mediaOption.publishDirectCustomAudioTrack = false
            apiConfig?.engine.updateChannelEx(with: mediaOption, connection: subConn)
            apiConfig?.engine.leaveChannelEx(subConn)
        } else if (role == .coSinger) {
            apiConfig?.engine.leaveChannelEx(subConn)
            apiConfig?.engine.muteRemoteAudioStream(UInt(config.mainSingerUid), mute: false)
        }
    }
    
    /**
     * 离开合唱
     */
    private func leaveChorus() {
        guard let config = songConfig else {return}
        let role = config.role
        if role == .leadSinger {
            mainSingerHasJoinChannelEx = false
            leaveChorus2ndChannel()
        } else if role == .coSinger {
            musicPlayer?.stop()
            let mediaOption = AgoraRtcChannelMediaOptions()
            mediaOption.autoSubscribeAudio = true
            mediaOption.autoSubscribeVideo = false
            mediaOption.publishMediaPlayerAudioTrack = false
            apiConfig?.engine.updateChannel(with: mediaOption)
            leaveChorus2ndChannel()

            apiConfig?.engine.setAudioScenario(.gameStreaming)
        
        }
//        else if role == .followSinger {
//            apiConfig?.engine.muteLocalAudioStream(false)
//
//            musicPlayer?.stop()
//            let mediaOption = AgoraRtcChannelMediaOptions()
//            mediaOption.autoSubscribeAudio = true
//            mediaOption.autoSubscribeVideo = false
//            mediaOption.publishMediaPlayerAudioTrack = false
//            apiConfig?.engine.updateChannel(with: mediaOption)
//            leaveChorus2ndChannel()
//
//            apiConfig?.engine.setAudioScenario(.gameStreaming)
//            songConfig?.role = .audience
//            songConfig?.songCode = 0
//        }
        else if role == .audience {
            print("joinChorus: KTVSingRoleAudience does not need to leaveChorus!")
        }
    }
}

extension Date {
    /// 获取当前 秒级 时间戳 - 10位
    ///
    var timeStamp : TimeInterval {
        let timeInterval: TimeInterval = self.timeIntervalSince1970
        return timeInterval
    }
    /// 获取当前 毫秒级 时间戳 - 13位
    var milListamp : TimeInterval {
        let timeInterval: TimeInterval = self.timeIntervalSince1970
        let millisecond = CLongLong(round(timeInterval*1000))
        return TimeInterval(millisecond)
    }
}




