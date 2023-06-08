//
//  VLVoicePerShowView.h
//  AgoraEntScenarios
//
//  Created by CP on 2023/3/3.
//

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
NS_ASSUME_NONNULL_BEGIN

@class VLVoicePerShowView;
@protocol VLVoicePerShowViewDelegate <NSObject>

@optional
- (void)voicePerItemSelectedAction:(BOOL)isSelected;
- (void)didAIAECGradeChangedWithIndex:(NSInteger)index;
- (void)voiceDelaySelectedAction:(BOOL)isSelected;
@end

@interface VLVoicePerShowView : UIView
- (instancetype)initWithFrame:(CGRect)frame isProfessional:(BOOL)isProfessional isDelay:(BOOL)isDelay isRoomOwner:(BOOL)isRoomOwner aecGrade:(NSInteger)grade withDelegate:(id<VLVoicePerShowViewDelegate>)delegate;
@end

NS_ASSUME_NONNULL_END
