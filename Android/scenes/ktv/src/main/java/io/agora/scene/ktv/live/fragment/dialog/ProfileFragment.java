package io.agora.scene.ktv.live.fragment.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.agora.scene.base.component.BaseViewBindingFragment;
import io.agora.scene.ktv.R;
import io.agora.scene.ktv.databinding.FragmentProfileBinding;
import io.agora.scene.ktv.live.RoomLivingActivity;
import io.agora.scene.ktv.widget.MusicSettingBean;

public class ProfileFragment extends BaseViewBindingFragment<FragmentProfileBinding> {
    public static final String TAG = "ProfileFragment";
    private final MusicSettingBean mSetting;

    public ProfileFragment(MusicSettingBean mSetting) {
        this.mSetting = mSetting;
    }

    @Override
    protected FragmentProfileBinding getViewBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentProfileBinding.inflate(inflater);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void initListener() {
        super.initListener();

        getBinding().cbStartProfessionalMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mSetting.setProfessionalMode(isChecked);
                if (isChecked) {
                    getBinding().vSettingMark.setVisibility(View.INVISIBLE);
                } else {
                    getBinding().vSettingMark.setVisibility(View.VISIBLE);
                    getBinding().vSettingMark.setOnClickListener(v -> {});
                }
            }
        });
        getBinding().cbStartProfessionalMode.setChecked(mSetting.getProfes scenes/ktv/src/main/sionalMode());
//        if (mSetting.getProfessionalMode()) {
//            getBinding().vSettingMark.setVisibility(View.INVISIBLE);
//        } else {
//            getBinding().vSettingMark.setVisibility(View.VISIBLE);
//            getBinding().vSettingMark.setOnClickListener(v -> {});
//        }

        if (this.mSetting.getAECLevel() == 0) {
            getBinding().rgVoiceMode.check(R.id.tvModeLow);
        } else if (this.mSetting.getAECLevel() == 1) {
            getBinding().rgVoiceMode.check(R.id.tvModeMiddle);
        } else {
            getBinding().rgVoiceMode.check(R.id.tvModeHigh);
        }
        getBinding().rgVoiceMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.tvModeLow) {
                mSetting.setAECLevel(0);
            } else if (checkedId == R.id.tvModeMiddle) {
                mSetting.setAECLevel(1);
            } else {
                mSetting.setAECLevel(2);
            }
        });

        getBinding().cbLowLatency.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView.isPressed() && isChecked && mSetting.getAinsMode() != 0) {
                    getBinding().rgAINSMode.check(R.id.tvAINSClose);
                }
                mSetting.setLowLatencyMode(isChecked);
            }
        });
        getBinding().cbLowLatency.setChecked(mSetting.getLowLatencyMode());


        if (this.mSetting.getAinsMode() == 0) {
            getBinding().rgAINSMode.check(R.id.tvAINSClose);
        } else if (this.mSetting.getAECLevel() == 1) {
            getBinding().rgAINSMode.check(R.id.tvAINSMiddle);
        } else {
            getBinding().rgAINSMode.check(R.id.tvAINSHigh);
        }
        getBinding().rgAINSMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.tvAINSClose) {
                mSetting.setAinsMode(0);
            } else if (checkedId == R.id.tvAINSMiddle) {
                if (mSetting.getLowLatencyMode()) {
                    getBinding().cbLowLatency.setChecked(false);
                }
                mSetting.setAinsMode(1);
            } else if (checkedId == R.id.tvAINSHigh) {
                if (mSetting.getLowLatencyMode()) {
                    getBinding().cbLowLatency.setChecked(false);
                }
                mSetting.setAinsMode(2);
            }
        });

        getBinding().ivBackIcon.setOnClickListener(view -> {
            ((RoomLivingActivity) requireActivity()).closeMenuDialog();
        });
    }
}
