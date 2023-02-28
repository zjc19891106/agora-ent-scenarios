package io.agora.scene.ktv.widget.song;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import io.agora.scene.base.component.BaseViewBindingFragment;
import io.agora.scene.base.component.OnItemClickListener;
import io.agora.scene.ktv.R;
import io.agora.scene.ktv.databinding.KtvFragmentSongListBinding;

/**
 * 歌单列表
 */
public final class SongChooseFragment extends BaseViewBindingFragment<KtvFragmentSongListBinding>
        implements OnItemClickListener<SongItem> {

    private Listener listener;
    private final List<Runnable> pendingViewCreatedRuns = new ArrayList<>();

    //private final SongChooseViewAdapter mRankListAdapter = new SongChooseViewAdapter() {
    //    @Override
    //    void onSongChosen(SongItem song, int position) {
    //        onSongItemChosen(song);
    //    }
    //};
    private final SongChooseViewAdapter mSearchAdapter = new SongChooseViewAdapter() {
        @Override
        void onSongChosen(SongItem song, int position) {
            onSongItemChosen(song);
        }
    };


    @NonNull
    @Override
    protected KtvFragmentSongListBinding getViewBinding(@NonNull LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup) {
        return KtvFragmentSongListBinding.inflate(layoutInflater);
    }

    @Override
    public void initView() {
        getBinding().tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                //mRankListAdapter.resetAll(null);
                onSongsRefreshing(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        PageAdapter adapter = new PageAdapter();
        getBinding().mViewPager2.setAdapter(adapter);
        getBinding().mViewPager2.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (getBinding().tabLayout.getSelectedTabPosition() == position) {
                    return;
                }
                TabLayout.Tab tabAt = getBinding().tabLayout.getTabAt(position);
                if (tabAt == null) {
                    return;
                }
                getBinding().tabLayout.selectTab(tabAt);
            }
        });
        getBinding().recyclerSearchResult.setAdapter(mSearchAdapter);

        Iterator<Runnable> iterator = pendingViewCreatedRuns.iterator();
        while (iterator.hasNext()){
            Runnable next = iterator.next();
            next.run();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!Objects.requireNonNull(getBinding().etSearch.getText()).toString().equals("")) {
            onSongsSearching(getBinding().etSearch.getText().toString());
        }
    }

    @Override
    public void initListener() {
        getBinding().llEmpty.setOnClickListener(v -> onSongsRefreshing(getBinding().tabLayout.getSelectedTabPosition()));
        getBinding().etSearch.setOnKeyListener((view, keyCode, keyEvent) -> {
            if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_SEARCH) {
                    onSongsSearching(getBinding().etSearch.getText().toString());
                }
                return true;
            }
            return false;
        });
        getBinding().etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable == null || editable.length() == 0) {
                    getBinding().iBtnClear.setVisibility(View.GONE);
                    getBinding().mViewPager2.setVisibility(View.VISIBLE);
                    getBinding().recyclerSearchResult.setVisibility(View.GONE);
                    getBinding().hScrollView.setVisibility(View.VISIBLE);
                    getBinding().llEmpty.setVisibility(View.GONE);
                } else {
                    getBinding().iBtnClear.setVisibility(View.VISIBLE);
                    getBinding().mViewPager2.setVisibility(View.GONE);
                    getBinding().recyclerSearchResult.setVisibility(View.VISIBLE);
                    getBinding().hScrollView.setVisibility(View.GONE);
                    getBinding().llEmpty.setVisibility(View.GONE);
                }
            }
        });
        getBinding().iBtnClear.setOnClickListener(view -> getBinding().etSearch.setText(""));
        //getBinding().smartRefreshLayout.setOnRefreshListener(refreshLayout -> {
        //    onSongsRefreshing(getBinding().tabLayout.getSelectedTabPosition());
        //});
        //getBinding().smartRefreshLayout.setOnLoadMoreListener(refreshLayout -> {
        //    onSongsLoadMore(getBinding().tabLayout.getSelectedTabPosition());
        //});
    }

    private void runOnViewCreated(Runnable runnable){
        View view = getView();
        if(view == null){
            pendingViewCreatedRuns.add(runnable);
        }else{
            runnable.run();
        }
    }

    int getCurrentTabIndex(){
        if(getView() == null){
            return 0;
        }
        return getBinding().tabLayout.getSelectedTabPosition();
    }

    void setSongTagsTitle(List<String> titles, int defaultIndex) {
        runOnViewCreated(() -> {
            for (String title : titles) {
                getBinding().tabLayout.addTab(getBinding().tabLayout.newTab().setText(title));
            }
            TabLayout.Tab tabAt = getBinding().tabLayout.getTabAt(defaultIndex);
            if (tabAt != null) {
                getBinding().tabLayout.selectTab(tabAt);
            }
        });
    }

    void setSongItemStatus(SongItem songItem, boolean isChosen) {
        if (getBinding().recyclerSearchResult.getVisibility() == View.VISIBLE) {
            int searchCount = mSearchAdapter.getItemCount();
            for (int i = 0; i < searchCount; i++) {
                SongItem item = mSearchAdapter.getItem(i);
                if (item.songNo.equals(songItem.songNo)) {
                    item.isChosen = isChosen;
                    mSearchAdapter.notifyItemChanged(i);
                    break;
                }
            }
        } else {
            //int itemCount = mRankListAdapter.getItemCount();
            //for (int i = 0; i < itemCount; i++) {
            //    SongItem item = mRankListAdapter.getItem(i);
            //    if (item.songNo.equals(songItem.songNo)) {
            //        item.isChosen = isChosen;
            //        mRankListAdapter.notifyItemChanged(i);
            //        break;
            //    }
            //}
        }
    }

    void setSearchResult(List<SongItem> list) {
        if (list == null || list.isEmpty()) {
            getBinding().llEmpty.setVisibility(View.VISIBLE);
        } else {
            getBinding().llEmpty.setVisibility(View.GONE);
        }
        mSearchAdapter.resetAll(list);
    }

    void setRefreshingResult(List<SongItem> list) {
        if (list == null || list.isEmpty()) {
            getBinding().llEmpty.setVisibility(View.VISIBLE);
        } else {
            getBinding().llEmpty.setVisibility(View.GONE);
        }
        //mRankListAdapter.resetAll(list);
        //
        //getBinding().smartRefreshLayout.setEnableLoadMore(true);
        //getBinding().smartRefreshLayout.finishRefresh();
        enableTabLayoutClick(true);
    }

    void setLoadMoreResult(List<SongItem> list, boolean hasMore) {
        //mRankListAdapter.insertAll(list);
        //getBinding().smartRefreshLayout.finishLoadMore();
        //getBinding().smartRefreshLayout.setEnableLoadMore(hasMore);
        enableTabLayoutClick(true);
    }

    private void enableTabLayoutClick(boolean enable){
        KtvFragmentSongListBinding binding = getBinding();
        if(binding == null){
            return;
        }
        TabLayout tabLayout = binding.tabLayout;
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            tabLayout.getTabAt(i).view.setClickable(enable);
        }
    }


    private void onSongItemChosen(@NonNull SongItem songItem){
        if(listener != null){
            listener.onSongItemChosen(songItem);
        }
    }

    private void onSongsSearching(String condition){
        if(listener != null){
            listener.onSongsSearching(condition);
        }
    }

    private void onSongsRefreshing(int tagIndex) {
        enableTabLayoutClick(false);
        if (listener != null) {
            listener.onSongsRefreshing(tagIndex);
        }
        getBinding().mViewPager2.setCurrentItem(tagIndex);
    }

    private void onSongsLoadMore(int tagIndex){
        enableTabLayoutClick(false);
        if(listener != null){
            listener.onSongsLoadMore(tagIndex);
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    interface Listener {
        void onSongItemChosen(@NonNull SongItem songItem);
        void onSongsSearching(String condition);
        void onSongsRefreshing(int tagIndex);
        void onSongsLoadMore(int tagIndex);
    }


    static class PageAdapter extends RecyclerView.Adapter<PageAdapter.MainHolder> {

        private List<Object> list = new ArrayList<>();

        public PageAdapter() {
            list.add("");
            list.add("");
            list.add("");
            list.add("");
        }

        @NonNull
        @Override
        public MainHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new MainHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_page, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull MainHolder holder, int position) {
            holder.tvTitle.setText(String.valueOf(position));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class MainHolder extends RecyclerView.ViewHolder {

            private TextView tvTitle;

            public MainHolder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvTitle);
            }
        }

    }

}
