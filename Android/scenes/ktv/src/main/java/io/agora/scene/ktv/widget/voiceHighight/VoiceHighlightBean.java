package io.agora.scene.ktv.widget.voiceHighight;

import androidx.annotation.DrawableRes;

import io.agora.scene.ktv.bean.EffectVoiceBean;
import io.agora.scene.ktv.service.RoomSeatModel;

public class VoiceHighlightBean {
    public RoomSeatModel user;

    private boolean select;


    public boolean isSelect() {
        return select;
    }

    public VoiceHighlightBean setSelect(boolean select) {
        this.select = select;
        return this;
    }
}
