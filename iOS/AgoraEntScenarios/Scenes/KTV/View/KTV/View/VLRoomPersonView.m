//
//  VLRoomPersonView.m
//  VoiceOnLine
//

#import "VLRoomPersonView.h"
#import "VLRoomPersonIteimCCell.h"
#import "VLRoomSeatModel.h"
#import "VLRoomSelSongModel.h"
#import "VLMacroDefine.h"
#import "VLUserCenter.h"
#import "KTVMacro.h"
@import YYCategories;
@import SDWebImage;

@interface VLRoomPersonView ()<UICollectionViewDataSource,UICollectionViewDelegate>

@property(nonatomic, weak) id <VLRoomPersonViewDelegate>delegate;

@property (nonatomic, strong) UICollectionView *personCollectionView;
@end

@implementation VLRoomPersonView

- (instancetype)initWithFrame:(CGRect)frame withDelegate:(id<VLRoomPersonViewDelegate>)delegate withRTCkit:(AgoraRtcEngineKit *)RTCkit{
    if (self = [super initWithFrame:frame]) {
        self.delegate = delegate;
        self.roomSeatsArray = [[NSArray alloc]init];
        [self setupView];
    }
    return self;
}

- (void)setupView {
    
    UICollectionViewFlowLayout *flowLayOut = [[UICollectionViewFlowLayout alloc]init];
    flowLayOut.scrollDirection = UICollectionViewScrollDirectionVertical;
    
    CGFloat itemW = VLREALVALUE_WIDTH(54);
    CGFloat middleMargin = (SCREEN_WIDTH-2*27-4*itemW)/3.0;
    CGFloat itemH = VLREALVALUE_WIDTH(54)+33;
    flowLayOut.itemSize = CGSizeMake(itemW, itemH);
    flowLayOut.minimumInteritemSpacing = middleMargin;
    flowLayOut.minimumLineSpacing = 15;
    
    self.personCollectionView = [[UICollectionView alloc] initWithFrame:CGRectMake(0, 0, SCREEN_WIDTH, itemH*2+15) collectionViewLayout:flowLayOut];
    self.personCollectionView.dataSource = self;
    self.personCollectionView.delegate = self;
    self.personCollectionView.alwaysBounceVertical = true;
    self.personCollectionView.showsHorizontalScrollIndicator = false;
    self.personCollectionView.showsVerticalScrollIndicator = false;
    self.personCollectionView.backgroundColor = UIColorClear;
    self.personCollectionView.scrollEnabled = NO;
    self.personCollectionView.contentInset = UIEdgeInsetsMake(0, 27, 0, 27);
    if (@available(iOS 11, *)) {
        self.personCollectionView.contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentNever;
    }
    [self.personCollectionView registerClass:[VLRoomPersonIteimCCell class] forCellWithReuseIdentifier:[VLRoomPersonIteimCCell className]];
    [self addSubview:self.personCollectionView];
    
}

- (void)setRoomSeatsArray:(NSArray *)roomSeatsArray {
    _roomSeatsArray = [[NSArray alloc]initWithArray:roomSeatsArray];
//    for (VLRoomSeatModel *seatModel in self.roomSeatsArray) {
//        if (seatModel.rtcUid != nil) {
//            if ([[self.roomSeatsViewArray allKeys]containsObject:seatModel.rtcUid]) {
//                [self.roomSeatsViewArray removeObjectForKey:seatModel.rtcUid];
//            }
//            UIView *renderView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, VLREALVALUE_WIDTH(54), VLREALVALUE_WIDTH(54))];
//            renderView.tag = viewTag+[seatModel.rtcUid integerValue];
//
//            AgoraRtcVideoCanvas *videoCanvas = [[AgoraRtcVideoCanvas alloc] init];
//            videoCanvas.uid = [seatModel.rtcUid integerValue];
//            videoCanvas.view = renderView;
//            videoCanvas.renderMode = AgoraVideoRenderModeHidden;
            //TODO video display
//            if ([seatModel.id isEqual:VLUserCenter.user.id]) {
//                [self.RTCkit setupLocalVideo:videoCanvas];
//                [self.RTCkit enableVideo];
//                if (self.delegate && [self.delegate respondsToSelector:@selector(ifMyCameraIsOpened)]) {
//                    if([self.delegate ifMyCameraIsOpened]) {
//                        [self.RTCkit startPreview];
//                    }
//                }
//            }
//            else{
//                [self.RTCkit setupRemoteVideo:videoCanvas];
//            }
//            [self.roomSeatsViewArray setObject:renderView forKey:seatModel.rtcUid];
//        }
//    }
    [self.personCollectionView reloadData];
}

//- (void)updateSeatsByModel:(VLRoomSeatModel *)model{
//    for (NSInteger i = 0; i < [self.roomSeatsArray count]; i++) {
//        VLRoomSeatModel *seatModel = [self.roomSeatsArray objectAtIndex:i];
//        if (seatModel.rtcUid != nil) {
//            if ([seatModel.rtcUid isEqual:model.rtcUid]) {
//                if ([[self.roomSeatsViewArray allKeys]containsObject:seatModel.rtcUid]) {
//                    [self.roomSeatsViewArray removeObjectForKey:seatModel.rtcUid];
//                }
//                UIView *renderView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, VLREALVALUE_WIDTH(54), VLREALVALUE_WIDTH(54))];
//                renderView.tag = viewTag + [seatModel.rtcUid integerValue];
//                AgoraRtcVideoCanvas *videoCanvas = [[AgoraRtcVideoCanvas alloc] init];
//                videoCanvas.uid = [seatModel.rtcUid integerValue];
//                videoCanvas.view = renderView;
//                videoCanvas.renderMode = AgoraVideoRenderModeHidden;
//                if ([model.id isEqual:VLUserCenter.user.id]) {
////                    [self.RTCkit enableVideo];
//                    [self.RTCkit setupLocalVideo:videoCanvas];
//                    if (self.delegate && [self.delegate respondsToSelector:@selector(ifMyCameraIsOpened)]) {
//                        if([self.delegate ifMyCameraIsOpened]) {
//                            [self.RTCkit startPreview];
//                        }
//                    }
//                }
//                else{
//                    [self.RTCkit setupRemoteVideo:videoCanvas];
//                }
//                [self.roomSeatsViewArray setObject:renderView forKey:seatModel.rtcUid];
//                [self.roomSeatsArray removeObject:seatModel];
//                [self.roomSeatsArray insertObject:model atIndex:i];
//                [self.personCollectionView reloadData];
//                return;
//            }
//        }
//    }
//}

- (void)updateIfNeeded
{
    [self.personCollectionView reloadData];
}

#pragma mark - UITableViewDelegate,UITableViewDataSource
- (NSInteger)collectionView:(UICollectionView *)collectionView numberOfItemsInSection:(NSInteger)section {
    return self.roomSeatsArray.count;
}

- (UICollectionViewCell *)collectionView:(UICollectionView *)collectionView cellForItemAtIndexPath:(NSIndexPath *)indexPath {
    VLRoomPersonIteimCCell *cell = nil;
    cell = [collectionView dequeueReusableCellWithReuseIdentifier:[VLRoomPersonIteimCCell className] forIndexPath:indexPath];
    
    for (UIView *view in cell.videoView.subviews) {
        if (view.tag > viewTag) {
            [view removeFromSuperview];
        }
    }
    VLRoomSeatModel *seatModel = self.roomSeatsArray[indexPath.row];
    
    if (seatModel.name.length > 0) {
        cell.nickNameLabel.text = seatModel.name;
    }else{
        cell.nickNameLabel.text = [NSString stringWithFormat:KTVLocalizedString(@"%d号麦"), (int)indexPath.row + 1];
    }
    if (seatModel.isMaster) {
        cell.avatarImgView.layer.borderWidth = 2.0;
        cell.avatarImgView.layer.borderColor = UIColorMakeWithHex(@"#75ADFF").CGColor;
        cell.roomerImgView.hidden = cell.roomerLabel.hidden = NO;
        cell.nickNameLabel.textColor = UIColorMakeWithHex(@"#DBDAE9");
    }else{
        cell.roomerImgView.hidden = cell.roomerLabel.hidden = YES;
        cell.nickNameLabel.textColor = UIColorMakeWithHex(@"#AEABD0");
        cell.avatarImgView.layer.borderColor = UIColorClear.CGColor;
    }
    cell.roomerLabel.text = KTVLocalizedString(@"房主");
    if (seatModel.headUrl.length > 0) {
        [cell.avatarImgView sd_setImageWithURL:[NSURL URLWithString:seatModel.headUrl]];
    }else{
        cell.avatarImgView.image = [UIImage sceneImageWithName:@"ktv_emptySeat_icon"];
    }
    cell.singingBtn.hidden = !seatModel.isOwner;
    
    cell.muteImgView.hidden = !seatModel.isAudioMuted;
    
    if(seatModel.isJoinedChorus)
        cell.joinChorusBtn.hidden = NO;
    else
        cell.joinChorusBtn.hidden = YES;
    
    if (seatModel.rtcUid == nil) {
        cell.muteImgView.hidden = YES;
        cell.singingBtn.hidden = YES;
        cell.joinChorusBtn.hidden = YES;
    }
    
    //only display when rtcUid exists (on mic seat), and video is not muted
    cell.videoView.hidden = !(seatModel.rtcUid != nil && !seatModel.isVideoMuted);
    if (!seatModel.isVideoMuted) { //开启了视频
        [self.delegate onVLRoomPersonView:self onRenderVideo:seatModel inView:cell.videoView atIndex:indexPath.row];
    }
    
    return cell;
}


//- (AgoraRtcChannelMediaOptions *)mediaOption {
//    if (!_mediaOption) {
//        _mediaOption = [[AgoraRtcChannelMediaOptions alloc] init];
//        _mediaOption.autoSubscribeAudio = [AgoraRtcBoolOptional of:YES];
//        _mediaOption.autoSubscribeVideo = [AgoraRtcBoolOptional of:YES];
//    }
//    return _mediaOption;
//}

- (void)collectionView:(UICollectionView *)collectionView didSelectItemAtIndexPath:(NSIndexPath *)indexPath {
    VLRoomSeatModel *roomSeatModel = self.roomSeatsArray[indexPath.row];
    if (self.delegate && [self.delegate respondsToSelector:@selector(onVLRoomPersonView:seatItemTappedWithModel:atIndex:)]) {
        [self.delegate onVLRoomPersonView:self seatItemTappedWithModel:roomSeatModel atIndex:indexPath.row];
    }
}

- (void)updateSingBtnWithChoosedSongArray:(NSArray *)choosedSongArray {
    if (choosedSongArray.count > 0) {
        VLRoomSelSongModel *songModel = choosedSongArray.firstObject;
        for (VLRoomSeatModel *seatModel in self.roomSeatsArray) {
            if ([seatModel.userNo isEqualToString:songModel.userNo]) {
                seatModel.isOwner = YES;
            }else{
                seatModel.isOwner = NO;
            }
            seatModel.isJoinedChorus = NO;
        }
        [self.personCollectionView reloadData];
    }else{
        for (VLRoomSeatModel *seatModel in self.roomSeatsArray) {
            seatModel.isOwner = NO;
            seatModel.isJoinedChorus = NO;
        }
        [self.personCollectionView reloadData];
    }
}


@end
