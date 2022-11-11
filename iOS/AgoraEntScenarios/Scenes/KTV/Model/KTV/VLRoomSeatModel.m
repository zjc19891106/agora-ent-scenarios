//
//  VLRoomSetModel.m
//  VoiceOnLine
//

#import "VLRoomSeatModel.h"

@implementation VLRoomSeatModel

- (instancetype)init {
    if (self = [super init]) {
    }
    return self;
}

- (void)resetLeaveSeat {
    self.isMaster = false;
    self.headUrl = @"";
    self.name = @"";
    self.userNo = @"";
    self.rtcUid = nil;
    self.isSelfMuted = 0;
    self.isVideoMuted = 0;
    self.isJoinedChorus = NO;
}

@end
