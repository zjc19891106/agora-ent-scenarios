//
//  GameAdView.swift
//  InteractiveJoy
//
//  Created by qinhui on 2024/7/29.
//
import UIKit

class GameAdView: UIView {
    lazy var imageView: UIImageView = {
       let imageView = UIImageView()
        imageView.backgroundColor = .purple
        return imageView
    }()
    
    lazy var titleLabel: UILabel = {
        let label = UILabel()
        label.text = "时下最热"
        return label
    }()
    
    lazy var descriptionLabel: UILabel = {
        let label = UILabel()
        label.text = "体验弹幕新玩法"
        return label
    }()
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        
        self.addSubview(imageView)
        self.addSubview(titleLabel)
        self.addSubview(descriptionLabel)
        
        imageView.snp.makeConstraints { make in
            make.left.equalTo(14)
            make.centerY.equalTo(self)
            make.height.equalTo(60)
            make.width.equalTo(60)
        }
        
        titleLabel.snp.makeConstraints { make in
            make.left.equalTo(imageView.snp.right).offset(20)
            make.bottom.equalTo(self.snp.centerY).offset(-3)
        }
        
        descriptionLabel.snp.makeConstraints { make in
            make.left.equalTo(titleLabel)
            make.top.equalTo(self.snp.centerY).offset(3)
        }
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}
