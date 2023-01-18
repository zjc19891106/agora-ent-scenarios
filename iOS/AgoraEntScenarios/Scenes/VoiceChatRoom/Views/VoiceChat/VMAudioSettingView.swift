//
//  VMAudioSettingView.swift
//  AgoraScene_iOS
//
//  Created by CP on 2022/9/7.
//

import SnapKit
import UIKit
import ZSwiftBaseLib
import CoreAudio

public enum AUDIO_SETTING_TYPE {
    case effect
    case Noise
    case Spatial
    case AIAEC
    case AGC
}


class VMAudioSettingView: UIView {
    lazy var cover: UIView = {
        UIView(frame: CGRect(x: 0, y: 0, width: ScreenWidth, height: 56)).backgroundColor(.clear).setGradient([UIColor(red: 0.929, green: 0.906, blue: 1, alpha: 1), UIColor(red: 1, green: 1, blue: 1, alpha: 0.3)], [CGPoint(x: 0, y: 0), CGPoint(x: 0, y: 1)])
    }()

    private var screenWidth: CGFloat = UIScreen.main.bounds.size.width
    private var lineImgView: UIImageView = .init()
    private var titleLabel: UILabel = .init()
    private var tableView: UITableView = .init()
    public var isAudience: Bool = false
    public var isPrivate: Bool = false

    private let swIdentifier = "switch"
    private let slIdentifier = "slider"
    private let nIdentifier = "normal"

//    private var settingName: [String] = ["\(LanguageManager.localValue(key: "blue")) & \(LanguageManager.localValue(key: "red"))", LanguageManager.localValue(key: "Robot Volume"), LanguageManager.localValue(key: "Best Sound"), "AINS", "Spatial Audio"]
//    private var settingImage: [String] = ["icons／set／jiqi", "icons／set／laba", "icons／set／zuijia", "icons／set／AINS", "icons／set／3D"]
    
    private var settingName: [String] = [LanguageManager.localValue(key: "AINS"),LanguageManager.localValue(key: "AIAEC"),LanguageManager.localValue(key: "AGC"),LanguageManager.localValue(key: "Agora Blue & Red Bot"), LanguageManager.localValue(key: "Robot Volume"), LanguageManager.localValue(key: "Best Agora Sound"), "Spatial Audio"]
    
    
    
    private var settingImage: [String] = ["icons／set／jiqi", "icons／set／laba", "icons／set／zuijia", "icons／set／AINS", "icons／set／3D", "icons／set／zuijia", "icons／set／AINS", "icons／set／3D"]


    private var soundTitle: [String] = []
    private var ainsTitle: [String] = []

    public var roomInfo: VRRoomInfo?
    public var ains_state: AINS_STATE = .mid {
        didSet {
            tableView.reloadData()
        }
    }

    var resBlock: ((AUDIO_SETTING_TYPE) -> Void)?
    var useRobotBlock: ((Bool) -> Void)?
    var volBlock: ((Int) -> Void)?
    override func draw(_ rect: CGRect) {
        super.draw(rect)
        backgroundColor = .white
        layoutUI()
    }

    private func layoutUI() {
        let path = UIBezierPath(roundedRect: bounds, byRoundingCorners: [.topLeft, .topRight], cornerRadii: CGSize(width: 20.0, height: 20.0))
        let layer = CAShapeLayer()
        layer.path = path.cgPath
        self.layer.mask = layer

        addSubview(cover)

        lineImgView.frame = CGRect(x: ScreenWidth / 2.0 - 20, y: 8, width: 40, height: 4)
        lineImgView.image = UIImage("pop_indicator")
        addSubview(lineImgView)

        titleLabel.frame = CGRect(x: ScreenWidth / 2.0 - 60, y: 30, width: 120, height: 30)
        titleLabel.textAlignment = .center
        titleLabel.text = LanguageManager.localValue(key: "Audio Settings")
        titleLabel.textColor = UIColor(red: 0.016, green: 0.035, blue: 0.145, alpha: 1)
        titleLabel.font = UIFont.systemFont(ofSize: 16, weight: .bold)
        addSubview(titleLabel)

        tableView.frame = CGRect(x: 0, y: 70, width: ScreenWidth, height: 280)
        tableView.registerCell(VMSwitchTableViewCell.self, forCellReuseIdentifier: swIdentifier)
        tableView.registerCell(VMSliderTableViewCell.self, forCellReuseIdentifier: slIdentifier)
        tableView.registerCell(VMNorSetTableViewCell.self, forCellReuseIdentifier: nIdentifier)
        tableView.dataSource = self
        tableView.delegate = self
        addSubview(tableView)
        tableView.tableFooterView = UIView()

        tableView.separatorColor = UIColor.HexColor(hex: 0xF6F6F6, alpha: 1)

        if #available(iOS 15.0, *) {
            tableView.sectionHeaderTopPadding = 0
        } else {
            // Fallback on earlier versions
        }
    }
}

extension VMAudioSettingView: UITableViewDelegate, UITableViewDataSource {
    func numberOfSections(in tableView: UITableView) -> Int {
        return 3
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        return 54
    }

    func tableView(_ tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat {
        return 32
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        if section == 0 {
            return 3
        }  else if section == 1 {
            return 2
        } else {
            return 1
        }
//        if section == 0 {
//            return 2
//        } else {
//            return roomInfo?.room?.type == 1 ? 3 : 2
//        }
    }

    func tableView(_ tableView: UITableView, viewForHeaderInSection section: Int) -> UIView? {
        if section == 0 {
            let headerView: UIView = .init(frame: CGRect(x: 0, y: 0, width: screenWidth, height: 32))
            headerView.backgroundColor = UIColor(red: 247 / 255.0, green: 248 / 255.0, blue: 251 / 255.0, alpha: 1)
            let titleLabel: UILabel = .init(frame: CGRect(x: 20, y: 2, width: 300, height: 30))
            titleLabel.text = LanguageManager.localValue(key: "Personal audio Settings")
            titleLabel.font = UIFont.systemFont(ofSize: 13)
            titleLabel.textColor = UIColor(red: 108 / 255.0, green: 113 / 255.0, blue: 146 / 255.0, alpha: 1)
            headerView.addSubview(titleLabel)
            return headerView
        } else  if section == 1{
            let width = textAutoWidth(height: 300, font: UIFont.systemFont(ofSize: 13), text: LanguageManager.localValue(key: "Bot Settings"))
            let headerView: UIView = .init(frame: CGRect(x: 0, y: 0, width: screenWidth, height: 32))
            headerView.backgroundColor = UIColor(red: 247 / 255.0, green: 248 / 255.0, blue: 251 / 255.0, alpha: 1)
            let titleLabel: UILabel = .init(frame: CGRect(x: 20, y: 2, width: width, height: 30))
            titleLabel.font = UIFont.systemFont(ofSize: 13)
            titleLabel.textColor = UIColor(red: 108 / 255.0, green: 113 / 255.0, blue: 146 / 255.0, alpha: 1)
            titleLabel.text = LanguageManager.localValue(key: "Bot Settings")
            headerView.addSubview(titleLabel)

            let imgView: UIImageView = .init(frame: CGRect(x: width + 30, y: 6, width: 30, height: 20))
            imgView.image = UIImage("new")
            headerView.addSubview(imgView)

            return headerView
        } else if section == 2 {
            let width = textAutoWidth(height: 300, font: UIFont.systemFont(ofSize: 13), text: LanguageManager.localValue(key: "Room Audio Settings"))
            let headerView: UIView = .init(frame: CGRect(x: 0, y: 0, width: screenWidth, height: 32))
            headerView.backgroundColor = UIColor(red: 247 / 255.0, green: 248 / 255.0, blue: 251 / 255.0, alpha: 1)
            let titleLabel: UILabel = .init(frame: CGRect(x: 20, y: 2, width: width, height: 30))
            titleLabel.font = UIFont.systemFont(ofSize: 13)
            titleLabel.textColor = UIColor(red: 108 / 255.0, green: 113 / 255.0, blue: 146 / 255.0, alpha: 1)
            titleLabel.text = LanguageManager.localValue(key: "Room Audio Settings")
            headerView.addSubview(titleLabel)

            let imgView: UIImageView = .init(frame: CGRect(x: width + 30, y: 6, width: 30, height: 20))
            imgView.image = UIImage("new")
            headerView.addSubview(imgView)

            return headerView
        } else {
            return nil
        }
        /*
        if section == 0 {
            let headerView: UIView = .init(frame: CGRect(x: 0, y: 0, width: screenWidth, height: 32))
            headerView.backgroundColor = UIColor(red: 247 / 255.0, green: 248 / 255.0, blue: 251 / 255.0, alpha: 1)
            let titleLabel: UILabel = .init(frame: CGRect(x: 20, y: 2, width: 300, height: 30))
            titleLabel.text = LanguageManager.localValue(key: "Bot Settings")
            titleLabel.font = UIFont.systemFont(ofSize: 13)
            titleLabel.textColor = UIColor(red: 108 / 255.0, green: 113 / 255.0, blue: 146 / 255.0, alpha: 1)
            headerView.addSubview(titleLabel)
            return headerView
        } else {
            let width = textAutoWidth(height: 300, font: UIFont.systemFont(ofSize: 13), text: LanguageManager.localValue(key: "ACEQ"))
            let headerView: UIView = .init(frame: CGRect(x: 0, y: 0, width: screenWidth, height: 32))
            headerView.backgroundColor = UIColor(red: 247 / 255.0, green: 248 / 255.0, blue: 251 / 255.0, alpha: 1)
            let titleLabel: UILabel = .init(frame: CGRect(x: 20, y: 2, width: width, height: 30))
            titleLabel.font = UIFont.systemFont(ofSize: 13)
            titleLabel.textColor = UIColor(red: 108 / 255.0, green: 113 / 255.0, blue: 146 / 255.0, alpha: 1)
            titleLabel.text = LanguageManager.localValue(key: "ACEQ")
            headerView.addSubview(titleLabel)

            let imgView: UIImageView = .init(frame: CGRect(x: width + 30, y: 6, width: 30, height: 20))
            imgView.image = UIImage("new")
            headerView.addSubview(imgView)

            return headerView
        }
         
         */
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        if indexPath.section == 0 {
            let cell: VMNorSetTableViewCell = tableView.dequeueReusableCell(withIdentifier: nIdentifier) as! VMNorSetTableViewCell
            cell.iconView.image = UIImage(settingImage[0 + indexPath.row])
            cell.titleLabel.text = settingName[0 + indexPath.row]
            if indexPath.row == 0 {
                cell.contentLabel.text = getSoundType(with: roomInfo?.room?.sound_effect ?? 1)
            } else {
                cell.contentLabel.text = "Off".localized()

            }
//            else if indexPath.row == 1 {
//                switch ains_state {
//                case .high:
//                    cell.contentLabel.text = "High".localized()
//                case .mid:
//                    cell.contentLabel.text = "Middle".localized()
//                case .off:
//                    cell.contentLabel.text = "Off".localized()
//                }
//            }
            return cell
            /*
            if indexPath.row == 0 {
                let cell: VMNorSetTableViewCell = tableView.dequeueReusableCell(withIdentifier: nIdentifier) as! VMNorSetTableViewCell
                cell.iconView.image = UIImage(settingImage[0])
                cell.titleLabel.text = settingName[0]
                if indexPath.row == 0 {
                    cell.contentLabel.text = getSoundType(with: roomInfo?.room?.sound_effect ?? 1)
                } else if indexPath.row == 1 {
                    switch ains_state {
                    case .high:
                        cell.contentLabel.text = "High".localized()
                    case .mid:
                        cell.contentLabel.text = "Middle".localized()
                    case .off:
                        cell.contentLabel.text = "Off".localized()
                    }
                }
                return cell
                
            } else if indexPath.row == 1 {
                let cell: VMSwitchTableViewCell = tableView.dequeueReusableCell(withIdentifier: swIdentifier) as! VMSwitchTableViewCell
                cell.iconView.image = UIImage(settingImage[1])
                cell.titleLabel.text = settingName[1]
                cell.isAudience = isAudience
                cell.selectionStyle = .none
                cell.swith.isOn = roomInfo?.room?.use_robot ?? false
                cell.useRobotBlock = { [weak self] flag in
                    guard let useRobotBlock = self?.useRobotBlock else { return }
                    useRobotBlock(flag)
                }
                return cell
            }
            else {
                let cell: VMSwitchTableViewCell = tableView.dequeueReusableCell(withIdentifier: swIdentifier) as! VMSwitchTableViewCell
                cell.iconView.image = UIImage(settingImage[2])
                cell.titleLabel.text = settingName[2]
                cell.isAudience = isAudience
                cell.selectionStyle = .none
                cell.swith.isOn = roomInfo?.room?.use_robot ?? false
                cell.useRobotBlock = { [weak self] flag in
                    guard let useRobotBlock = self?.useRobotBlock else { return }
                    useRobotBlock(flag)
                }
                return cell
            }
             */
        } else if indexPath.section == 1 {
            if indexPath.row == 0 {
                let cell: VMSwitchTableViewCell = tableView.dequeueReusableCell(withIdentifier: swIdentifier) as! VMSwitchTableViewCell
                cell.iconView.image = UIImage(settingImage[3])
                cell.titleLabel.text = settingName[3]
                cell.isAudience = isAudience
                cell.selectionStyle = .none
                cell.swith.isOn = roomInfo?.room?.use_robot ?? false
                cell.useRobotBlock = { [weak self] flag in
                    guard let useRobotBlock = self?.useRobotBlock else { return }
                    useRobotBlock(flag)
                }
                return cell
            } else if indexPath.row == 1 {
                let cell: VMSliderTableViewCell = tableView.dequeueReusableCell(withIdentifier: slIdentifier) as! VMSliderTableViewCell
                cell.iconView.image = UIImage(settingImage[4])
                cell.titleLabel.text = settingName[4]
                cell.isAudience = isAudience
                cell.selectionStyle = .none
                cell.volBlock = { [weak self] vol in
                    guard let volBlock = self?.volBlock else { return }
                    volBlock(vol)
                }
                
                let volume = roomInfo?.room?.robot_volume ?? 50
                cell.slider.value = Float(volume) / 100.0
                cell.countLabel.text = "\(volume)"
                return cell
            }
        } else if indexPath.section == 2 {
            
            let cell: VMNorSetTableViewCell = tableView.dequeueReusableCell(withIdentifier: nIdentifier) as! VMNorSetTableViewCell
            cell.iconView.image = UIImage(settingImage[5])
            cell.titleLabel.text = settingName[5]
            if indexPath.row == 0 {
                cell.contentLabel.text = getSoundType(with: roomInfo?.room?.sound_effect ?? 1)
            } else if indexPath.row == 1 {
                switch ains_state {
                case .high:
                    cell.contentLabel.text = "High".localized()
                case .mid:
                    cell.contentLabel.text = "Middle".localized()
                case .off:
                    cell.contentLabel.text = "Off".localized()
                }
            }
            return cell
            
//            if indexPath.row == 0 {
//                let cell: VMSwitchTableViewCell = tableView.dequeueReusableCell(withIdentifier: swIdentifier) as! VMSwitchTableViewCell
//                cell.iconView.image = UIImage(settingImage[0])
//                cell.titleLabel.text = settingName[0]
//                cell.isAudience = isAudience
//                cell.selectionStyle = .none
//                cell.swith.isOn = roomInfo?.room?.use_robot ?? false
//                cell.useRobotBlock = { [weak self] flag in
//                    guard let useRobotBlock = self?.useRobotBlock else { return }
//                    useRobotBlock(flag)
//                }
//                return cell
//            } else if indexPath.row == 1 {
//                let cell: VMSliderTableViewCell = tableView.dequeueReusableCell(withIdentifier: slIdentifier) as! VMSliderTableViewCell
//                cell.iconView.image = UIImage(settingImage[1])
//                cell.titleLabel.text = settingName[1]
//                cell.isAudience = isAudience
//                cell.selectionStyle = .none
//                cell.volBlock = { [weak self] vol in
//                    guard let volBlock = self?.volBlock else { return }
//                    volBlock(vol)
//                }
//
//                let volume = roomInfo?.room?.robot_volume ?? 50
//                cell.slider.value = Float(volume) / 100.0
//                cell.countLabel.text = "\(volume)"
//                return cell
//            }

        }

        return UITableViewCell()
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        if indexPath.section == 0 {
            guard let block = resBlock else { return }
            switch indexPath.row {
            case 0:
                //AINS
                block(.Noise)
            case 1:
                block(.AIAEC);
            case 2:
                block(.AGC)
            default:
                block(.Spatial)
                
            }
        } else if indexPath.section == 1 {
//            guard let block = resBlock else { return }
//            switch indexPath.row {
//            case 0:
//                //最佳音效
//                block(.effect)
//            case 1:
//                //AINs
//                block(.Noise)
//            default:
//                block(.Spatial)
//            }
        } else {
            guard let block = resBlock else { return }
            switch indexPath.row {
            case 0:
                //最佳音效
                block(.effect)
            default:
                block(.Spatial)
            }
        }
        /*
        if indexPath.section == 1 {
            guard let block = resBlock else { return }
            switch indexPath.row {
            case 0:
                block(.effect)
            case 1:
                block(.Noise)
            default:
                block(.Spatial)
            }
        }
        */
    }

    func textAutoWidth(height: CGFloat, font: UIFont, text: String) -> CGFloat {
        let origin = NSStringDrawingOptions.usesLineFragmentOrigin
        let lead = NSStringDrawingOptions.usesFontLeading
        let rect = text.boundingRect(with: CGSize(width: 0, height: height), options: [origin, lead], attributes: [NSAttributedString.Key.font: font], context: nil)
        return rect.width
    }
    
    private func getSoundType(with index: Int) -> String {
        var soundType: String = "Social Chat".localized()
        switch index {
        case 0:
            soundType = "Social Chat".localized()
        case 1:
            soundType = "Karaoke".localized()
        case 2:
            soundType = "Gaming Buddy".localized()
        case 3:
            soundType = "Professional Podcaster".localized()
        default:
            soundType = "Social Chat".localized()
        }
        return soundType
    }
}



