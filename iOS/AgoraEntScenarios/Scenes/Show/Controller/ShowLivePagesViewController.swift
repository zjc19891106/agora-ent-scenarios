//
//  ShowLivePagesViewController.swift
//  AgoraEntScenarios
//
//  Created by wushengtao on 2023/1/13.
//

import Foundation
import UIKit

class ShowLivePagesViewController: ViewController {
    var roomList: [ShowRoomListModel]?
    // 观众端预设类型
    var audiencePresetType: ShowPresetType?
    var selectedResolution = 1
    
    var focusIndex: Int = 0
    
    lazy var agoraKitManager: ShowAgoraKitManager = {
        let manager = ShowAgoraKitManager()
        manager.defaultSetting()
        return manager
    }()
    
    
    fileprivate var roomVCMap: [String: ShowLiveViewController] = [:]
    
    private lazy var collectionView: UICollectionView = {
        let layout = UICollectionViewFlowLayout()
        layout.minimumLineSpacing = 0
        layout.minimumInteritemSpacing = 0
        layout.sectionInset = .zero
        layout.itemSize = self.view.bounds.size
        let collectionView = UICollectionView(frame: self.view.bounds, collectionViewLayout: layout)
        collectionView.register(UICollectionViewCell.self, forCellWithReuseIdentifier: NSStringFromClass(UICollectionViewCell.self))
        collectionView.delegate = self
        collectionView.dataSource = self
        collectionView.isPagingEnabled = true
        collectionView.contentInsetAdjustmentBehavior = .never
        collectionView.bounces = false
        collectionView.showsVerticalScrollIndicator = false
        return collectionView
    }()
    
    deinit {
        showLogger.info("deinit-- ShowLivePagesViewController")
        self.roomVCMap.forEach { (key: String, value: ShowLiveViewController) in
            value.leaveRoom()
            AppContext.unloadShowServiceImp(key)
        }
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        self.navigationController?.setNavigationBarHidden(true, animated: true)
        self.view.addSubview(collectionView)
        collectionView.isScrollEnabled = roomList?.count ?? 0 > 1 ? true : false
        scroll(to: fakeCellIndex(with: focusIndex))
    }
}


private let kPageCacheHalfCount = 5
//MARK: private
extension ShowLivePagesViewController {
    fileprivate func fakeCellCount() -> Int {
        guard let count = roomList?.count else {
            return 0
        }
        return count > 2 ? count + kPageCacheHalfCount * 2 : count
    }
    
    fileprivate func realCellIndex(with fakeIndex: Int) -> Int {
        if fakeCellCount() < 3 {
            return fakeIndex
        }
        
        guard let realCount = roomList?.count else {
            showLogger.error("realCellIndex roomList?.count == nil", context: kShowLogBaseContext)
            return 0
        }
        let offset = kPageCacheHalfCount
        var realIndex = fakeIndex + realCount * max(1 + offset / realCount, 2) - offset
        realIndex = realIndex % realCount
        
        return realIndex
    }
    
    fileprivate func fakeCellIndex(with realIndex: Int) -> Int {
        if fakeCellCount() < 3 {
            return realIndex
        }
        
        guard let _ = roomList?.count else {
            showLogger.error("fakeCellIndex roomList?.count == nil", context: kShowLogBaseContext)
            return 0
        }
        let offset = kPageCacheHalfCount
        let fakeIndex = realIndex + offset
        
        return fakeIndex
    }
    
    private func scroll(to index: Int) {
        collectionView.scrollToItem(at: IndexPath(row: index, section: 0), at: .centeredVertically, animated: false)
    }
}

//MARK: live vc cache
extension ShowLiveViewController {
    
}

let kShowLiveRoomViewTag = 12345
//MARK: UICollectionViewDelegate & UICollectionViewDataSource
extension ShowLivePagesViewController: UICollectionViewDelegate, UICollectionViewDataSource {
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell: UICollectionViewCell = collectionView.dequeueReusableCell(withReuseIdentifier: NSStringFromClass(UICollectionViewCell.self),
                                                                            for: indexPath)
        let idx = realCellIndex(with: indexPath.row)
        defer {
            showLogger.info("collectionView cellForItemAt: \(idx)/\(indexPath.row)  cache vc count: \(self.roomVCMap.count)")
        }
        
        guard let room = self.roomList?[idx], let roomId = room.roomId else {
            return cell
        }
        
        let origVC = cell.contentView.viewWithTag(kShowLiveRoomViewTag)?.next as? ShowLiveViewController
        
        var vc = self.roomVCMap[roomId]
        if let _ = vc {
            if origVC == vc {
                return cell
            }
            
            vc?.view.removeFromSuperview()
        } else {
            vc = ShowLiveViewController(agoraKitManager: self.agoraKitManager)
            vc?.audiencePresetType = self.audiencePresetType
            vc?.selectedResolution = self.selectedResolution
            vc?.room = room
            vc?.loadingType = .preload
        }
        
        guard let vc = vc else {
            return cell
        }
        if let origVC = origVC {
            origVC.view.removeFromSuperview()
            origVC.removeFromParent()
            origVC.loadingType = .idle
            AppContext.unloadShowServiceImp(origVC.room?.roomId ?? "")
            self.roomVCMap[origVC.room?.roomId ?? ""] = nil
            showLogger.info("remove cache vc: \(origVC.room?.roomId ?? "") cache vc count:\(self.roomVCMap.count)")
        }
        
        vc.view.frame = self.view.bounds
        vc.view.tag = kShowLiveRoomViewTag
        cell.contentView.addSubview(vc.view)
        self.addChild(vc)
        self.roomVCMap[roomId] = vc
        
        return cell
    }
    
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return fakeCellCount()
    }
    
    func collectionView(_ collectionView: UICollectionView, willDisplay cell: UICollectionViewCell, forItemAt indexPath: IndexPath) {
        let idx = realCellIndex(with: indexPath.row)
        showLogger.info("collectionView willDisplay: \(idx)/\(indexPath.row)  cache vc count: \(self.roomVCMap.count)")
        guard let room = self.roomList?[idx], let roomId = room.roomId, let vc = self.roomVCMap[roomId] else {
//            assert(false, "room at index \(idx) not found")
            return
        }
        vc.loadingType = .loading
    }
    
    func collectionView(_ collectionView: UICollectionView, didEndDisplaying cell: UICollectionViewCell, forItemAt indexPath: IndexPath) {
        let idx = realCellIndex(with: indexPath.row)
        showLogger.info("collectionView didEndDisplaying: \(idx)/\(indexPath.row)  cache vc count: \(self.roomVCMap.count)")
        guard let room = self.roomList?[idx], let roomId = room.roomId, let vc = self.roomVCMap[roomId] else {
//            assert(false, "room at index \(idx) not found")
            return
        }
        vc.loadingType = .preload
    }
    
    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        let currentIndex = Int(scrollView.contentOffset.y / scrollView.height)
        if currentIndex > 0, currentIndex < fakeCellCount() - 1 {return}
        let realIndex = realCellIndex(with: currentIndex)
        let toIndex = fakeCellIndex(with: realIndex)
        showLogger.info("collectionView scrollViewDidEndDecelerating: from: \(currentIndex) to: \(toIndex) real: \(realIndex)")
        
        scroll(to: toIndex)
    }
}
