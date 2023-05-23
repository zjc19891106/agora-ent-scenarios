package io.agora.scene.ktv.singbattle.widget.rankList;


import androidx.annotation.NonNull;

import io.agora.scene.ktv.singbattle.R;
import io.agora.scene.ktv.singbattle.databinding.KtvItemRankListBinding;
import io.agora.scene.widget.basic.BindingSingleAdapter;
import io.agora.scene.widget.basic.BindingViewHolder;

public class RankListAdapter extends BindingSingleAdapter<RankItem, KtvItemRankListBinding> {
    @Override
    public void onBindViewHolder(@NonNull BindingViewHolder<KtvItemRankListBinding> holder, int position) {
        RankItem item = getItem(position);
        KtvItemRankListBinding mBinding = holder.binding;
        if (position == 0) {
            mBinding.getRoot().setBackgroundResource(R.mipmap.ktv_game_rank_list_1_background);
            mBinding.tvTank.setBackgroundResource(R.mipmap.ktv_game_rank_1);
            mBinding.tvTank.setText("");
        } else if (position == 1) {
            mBinding.getRoot().setBackgroundResource(R.mipmap.ktv_game_rank_list_2_background);
            mBinding.tvTank.setBackgroundResource(R.mipmap.ktv_game_rank_2);
            mBinding.tvTank.setText("");
        } else if (position == 2) {
            mBinding.getRoot().setBackgroundResource(R.mipmap.ktv_game_rank_list_3_background);
            mBinding.tvTank.setBackgroundResource(R.mipmap.ktv_game_rank_3);
            mBinding.tvTank.setText("");
        } else {
            mBinding.getRoot().setBackgroundResource(R.mipmap.ktv_game_rank_list_default_background);
            mBinding.tvTank.setBackgroundResource(0);
            mBinding.tvTank.setText("" + (position + 1));
        }
        mBinding.tvPlayer.setText(item.userName);
        mBinding.tvSongNum.setText(String.valueOf(item.songNum));
        mBinding.tvScore.setText(String.valueOf(item.score));
    }
}
