//
//  VLCreateRoomViewController.m
//  VoiceOnLine
//

#import "VLCreateRoomViewController.h"
#import <AgoraRtcKit/AgoraRtcKit.h>
#import "VLCreateRoomView.h"
//#import "VLKTVViewController.h"
#import "VLAddRoomModel.h"
#import "VLMacroDefine.h"
#import "VLUserCenter.h"
#import "VLToast.h"
#import "VLURLPathConfig.h"
#import "AppContext+DHCKTV.h"
#import "AESMacro.h"
#import <SVProgressHUD/SVProgressHUD.h>
@interface VLCreateRoomViewController ()<VLCreateRoomViewDelegate/*,AgoraRtmDelegate*/>
@property (nonatomic, strong) AgoraRtcEngineKit *RTCkit;
@property (nonatomic, strong) DHCVLCreateRoomView *createView;
@end

@implementation VLCreateRoomViewController

- (void)viewDidLoad {
    [super viewDidLoad];
//    AgoraRtcEngineConfig *config = [[AgoraRtcEngineConfig alloc]init];
//    config.appId = [AppContext.shared appId];
//    config.audioScenario = AgoraAudioScenarioChorus;
//    config.channelProfile = AgoraChannelProfileLiveBroadcasting;
//    self.RTCkit = [AgoraRtcEngineKit sharedEngineWithConfig:config delegate:nil];
//    /// 开启唱歌评分功能
//    int code = [self.RTCkit enableAudioVolumeIndication:20 smooth:3 reportVad:YES];
//    if (code == 0) {
//        VLLog(@"评分回调开启成功\n");
//    } else {
//        VLLog(@"评分回调开启失败：%d\n",code);
//    }
    [self commonUI];
    [self setUpUI];
}

- (void)commonUI {
    [self setBackgroundImage:@"online_list_BgIcon"];
    [self setNaviTitleName:KTVLocalizedString(@"ktv_create_room")];
    [self setBackBtn];
}

- (void)viewWillDisappear:(BOOL)animated{
    [super viewWillDisappear:animated];
    [SVProgressHUD dismiss];
}

#pragma mark - Public Methods
- (void)configNavigationBar:(UINavigationBar *)navigationBar {
    [super configNavigationBar:navigationBar];
}
- (BOOL)preferredNavigationBarHidden {
    return true;
}

- (void)createBtnAction:(VLAddRoomModel *)roomModel {  //房主创建
    if (roomModel.isPrivate && roomModel.password.length != 4) {
        return;
    }
    
    KTVCreateRoomInputModel* intputModel = [KTVCreateRoomInputModel new];
    intputModel.belCanto = @"0";
    intputModel.icon = [NSString stringWithFormat:@"%@",roomModel.icon];
    intputModel.isPrivate = roomModel.isPrivate == true ? @(1) : @(0);
    intputModel.name = [NSString stringWithFormat:@"%@",roomModel.name];
    intputModel.password = roomModel.password.length > 0 ? [NSString stringWithFormat:@"%@",roomModel.password] : @"";
    intputModel.soundEffect = @"0";
    intputModel.creatorAvatar = VLUserCenter.user.headUrl;
//    intputModel.userNo = VLUserCenter.user.id;
    VL(weakSelf);
    self.createView.createBtn.userInteractionEnabled = NO;
    [[AppContext ktvServiceImp] createRoomWith:intputModel
                                         completion:^(NSError * error, KTVCreateRoomOutputModel * outputModel) {
        self.createView.createBtn.userInteractionEnabled = YES;
        if (error != nil) {
            [VLToast toast:error.description];
            return;
        }
        
//        [self.RTCkit joinChannelByToken:VLUserCenter.user.agoraRTCToken
//                              channelId:outputModel.roomNo
//                                   info:nil uid:[VLUserCenter.user.id integerValue]
//                            joinSuccess:^(NSString * _Nonnull channel, NSUInteger uid, NSInteger elapsed) {
//            VLLog(@"Agora - 加入RTC成功");
//            [self.RTCkit setClientRole:AgoraClientRoleBroadcaster];
//        }];
        //处理座位信息
        UIViewController *topViewController = self.navigationController.viewControllers.lastObject;
        if(![topViewController isMemberOfClass:[VLCreateRoomViewController class]]){
            return;
        }
        if (error == nil) {
            VLRoomListModel *listModel = [[VLRoomListModel alloc]init];
            listModel.roomNo = outputModel.roomNo;
            listModel.name = outputModel.name;
            listModel.bgOption = 0;
            listModel.creatorAvatar = outputModel.creatorAvatar;
            listModel.creatorNo = VLUserCenter.user.id;
            UIViewController *VC = [ViewControllerFactory createCustomViewControllerWithTitle:listModel seatsArray:outputModel.seatsArray];
            [weakSelf.navigationController pushViewController:VC animated:YES];
        }
    }];
}

//- (NSArray *)configureSeatsWithArray:(NSArray *)seatsArray songArray:(NSArray *)songArray {
//    NSMutableArray *seatMuArray = [NSMutableArray array];
//    NSArray *modelArray = [VLRoomSeatModel vj_modelArrayWithJson:seatsArray];
//    for (int i=0; i<8; i++) {
//        BOOL ifFind = NO;
//        for (VLRoomSeatModel *model in modelArray) {
//            if (model.seatIndex == i) { //这个位置已经有人了
//                ifFind = YES;
//                if(songArray != nil && [songArray count] >= 1) {
//                    if([model.userNo isEqualToString:songArray[0][@"userNo"]]) {
//                        model.isSelTheSingSong = YES;
//                    }
//                    else if([model.userNo isEqualToString:songArray[0][@"chorusNo"]]) {
//                        model.isJoinedChorus = YES;
//                    }
//                }
//                
//                [seatMuArray addObject:model];
//            }
//        }
//        if (!ifFind) {
//            VLRoomSeatModel *model = [[VLRoomSeatModel alloc]init];
//            model.seatIndex = i;
//            [seatMuArray addObject:model];
//        }
//    }
//    return seatMuArray.mutableCopy;
//}

- (void)setUpUI {
    VLCreateRoomView *createRoomView = [[VLCreateRoomView alloc]initWithFrame:CGRectMake(0, kTopNavHeight, SCREEN_WIDTH, SCREEN_HEIGHT-kTopNavHeight) withDelegate:self];
    [self.view addSubview:createRoomView];
    self.createView = createRoomView;
}



@end