package org.schabi.newpipe.player;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.playlist.PlayQueueItemBuilder;
import org.schabi.newpipe.playlist.PlayQueueItemHolder;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ThemeHelper;

public abstract class ServicePlayerActivity extends AppCompatActivity
        implements PlayerEventListener, SeekBar.OnSeekBarChangeListener, View.OnClickListener {

    private boolean serviceBound;
    private ServiceConnection serviceConnection;

    protected BasePlayer player;

    private boolean seeking;

    ////////////////////////////////////////////////////////////////////////////
    // Views
    ////////////////////////////////////////////////////////////////////////////

    private static final int RECYCLER_ITEM_POPUP_MENU_GROUP_ID = 47;
    private static final int PLAYBACK_SPEED_POPUP_MENU_GROUP_ID = 61;
    private static final int PLAYBACK_PITCH_POPUP_MENU_GROUP_ID = 97;

    private View rootView;

    private RecyclerView itemsList;
    private ItemTouchHelper itemTouchHelper;

    private TextView metadataTitle;
    private TextView metadataArtist;

    private SeekBar progressSeekBar;
    private TextView progressCurrentTime;
    private TextView progressEndTime;

    private ImageButton repeatButton;
    private ImageButton backwardButton;
    private ImageButton playPauseButton;
    private ImageButton forwardButton;
    private ImageButton shuffleButton;
    private ProgressBar progressBar;

    private TextView playbackSpeedButton;
    private PopupMenu playbackSpeedPopupMenu;
    private TextView playbackPitchButton;
    private PopupMenu playbackPitchPopupMenu;

    ////////////////////////////////////////////////////////////////////////////
    // Abstracts
    ////////////////////////////////////////////////////////////////////////////

    public abstract String getTag();

    public abstract String getSupportActionTitle();

    public abstract Intent getBindIntent();

    public abstract void startPlayerListener();

    public abstract void stopPlayerListener();

    public abstract BasePlayer playerFrom(final IBinder binder);

    ////////////////////////////////////////////////////////////////////////////
    // Activity Lifecycle
    ////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.setTheme(this);
        setContentView(R.layout.activity_player_queue_control);
        rootView = findViewById(R.id.main_content);

        final Toolbar toolbar = rootView.findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getSupportActionTitle());
        }

        serviceConnection = getServiceConnection();
        bind();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_play_queue, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_history:
                NavigationHelper.openHistory(this);
                return true;
            case R.id.action_settings:
                NavigationHelper.openSettings(this);
                return true;
            case R.id.action_system_audio:
                startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbind();
    }

    ////////////////////////////////////////////////////////////////////////////
    // Service Connection
    ////////////////////////////////////////////////////////////////////////////

    private void bind() {
        final boolean success = bindService(getBindIntent(), serviceConnection, BIND_AUTO_CREATE);
        if (!success) {
            unbindService(serviceConnection);
        }
        serviceBound = success;
    }

    private void unbind() {
        if(serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
            stopPlayerListener();
            player = null;
        }
    }

    private ServiceConnection getServiceConnection() {
        return new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(getTag(), "Player service is disconnected");
            }

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(getTag(), "Player service is connected");
                player = playerFrom(service);
                if (player == null || player.playQueue == null || player.playQueueAdapter == null || player.simpleExoPlayer == null) {
                    unbind();
                    finish();
                } else {
                    buildComponents();
                    startPlayerListener();
                }
            }
        };
    }

    ////////////////////////////////////////////////////////////////////////////
    // Component Building
    ////////////////////////////////////////////////////////////////////////////

    private void buildComponents() {
        buildQueue();
        buildMetadata();
        buildSeekBar();
        buildControls();
    }

    private void buildQueue() {
        itemsList = findViewById(R.id.play_queue);
        itemsList.setLayoutManager(new LinearLayoutManager(this));
        itemsList.setAdapter(player.playQueueAdapter);
        itemsList.setClickable(true);
        itemsList.setLongClickable(true);

        itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(itemsList);

        player.playQueueAdapter.setSelectedListener(getOnSelectedListener());
    }

    private void buildMetadata() {
        metadataTitle = rootView.findViewById(R.id.song_name);
        metadataArtist = rootView.findViewById(R.id.artist_name);
    }

    private void buildSeekBar() {
        progressCurrentTime = rootView.findViewById(R.id.current_time);
        progressSeekBar = rootView.findViewById(R.id.seek_bar);
        progressEndTime = rootView.findViewById(R.id.end_time);

        progressSeekBar.setOnSeekBarChangeListener(this);
    }

    private void buildControls() {
        repeatButton = rootView.findViewById(R.id.control_repeat);
        backwardButton = rootView.findViewById(R.id.control_backward);
        playPauseButton = rootView.findViewById(R.id.control_play_pause);
        forwardButton = rootView.findViewById(R.id.control_forward);
        shuffleButton = rootView.findViewById(R.id.control_shuffle);
        playbackSpeedButton = rootView.findViewById(R.id.control_playback_speed);
        playbackPitchButton = rootView.findViewById(R.id.control_playback_pitch);
        progressBar = rootView.findViewById(R.id.control_progress_bar);

        repeatButton.setOnClickListener(this);
        backwardButton.setOnClickListener(this);
        playPauseButton.setOnClickListener(this);
        forwardButton.setOnClickListener(this);
        shuffleButton.setOnClickListener(this);
        playbackSpeedButton.setOnClickListener(this);
        playbackPitchButton.setOnClickListener(this);

        playbackSpeedPopupMenu = new PopupMenu(this, playbackSpeedButton);
        playbackPitchPopupMenu = new PopupMenu(this, playbackPitchButton);
        buildPlaybackSpeedMenu();
        buildPlaybackPitchMenu();
    }

    private void buildPlaybackSpeedMenu() {
        if (playbackSpeedPopupMenu == null) return;

        playbackSpeedPopupMenu.getMenu().removeGroup(PLAYBACK_SPEED_POPUP_MENU_GROUP_ID);
        for (int i = 0; i < BasePlayer.PLAYBACK_SPEEDS.length; i++) {
            final float playbackSpeed = BasePlayer.PLAYBACK_SPEEDS[i];
            final String formattedSpeed = player.formatSpeed(playbackSpeed);
            final MenuItem item = playbackSpeedPopupMenu.getMenu().add(PLAYBACK_SPEED_POPUP_MENU_GROUP_ID, i, Menu.NONE, formattedSpeed);
            item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    player.setPlaybackSpeed(playbackSpeed);
                    return true;
                }
            });
        }
    }

    private void buildPlaybackPitchMenu() {
        if (playbackPitchPopupMenu == null) return;

        playbackPitchPopupMenu.getMenu().removeGroup(PLAYBACK_PITCH_POPUP_MENU_GROUP_ID);
        for (int i = 0; i < BasePlayer.PLAYBACK_PITCHES.length; i++) {
            final float playbackPitch = BasePlayer.PLAYBACK_PITCHES[i];
            final String formattedPitch = player.formatPitch(playbackPitch);
            final MenuItem item = playbackPitchPopupMenu.getMenu().add(PLAYBACK_PITCH_POPUP_MENU_GROUP_ID, i, Menu.NONE, formattedPitch);
            item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    player.setPlaybackPitch(playbackPitch);
                    return true;
                }
            });
        }
    }

    private void buildItemPopupMenu(final PlayQueueItem item, final View view) {
        final PopupMenu menu = new PopupMenu(this, view);
        final MenuItem remove = menu.getMenu().add(RECYCLER_ITEM_POPUP_MENU_GROUP_ID, 0, Menu.NONE, R.string.play_queue_remove);
        remove.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                final int index = player.playQueue.indexOf(item);
                if (index != -1) player.playQueue.remove(index);
                return true;
            }
        });

        final MenuItem detail = menu.getMenu().add(RECYCLER_ITEM_POPUP_MENU_GROUP_ID, 1, Menu.NONE, R.string.play_queue_stream_detail);
        detail.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                onOpenDetail(item.getServiceId(), item.getUrl(), item.getTitle());
                return true;
            }
        });

        menu.show();
    }

    ////////////////////////////////////////////////////////////////////////////
    // Component Helpers
    ////////////////////////////////////////////////////////////////////////////

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
                if (source.getItemViewType() != target.getItemViewType()) {
                    return false;
                }

                final int sourceIndex = source.getLayoutPosition();
                final int targetIndex = target.getLayoutPosition();
                player.playQueue.move(sourceIndex, targetIndex);
                return true;
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {}
        };
    }

    private PlayQueueItemBuilder.OnSelectedListener getOnSelectedListener() {
        return new PlayQueueItemBuilder.OnSelectedListener() {
            @Override
            public void selected(PlayQueueItem item, View view) {
                player.onSelected(item);
            }

            @Override
            public void held(PlayQueueItem item, View view) {
                final int index = player.playQueue.indexOf(item);
                if (index != -1) buildItemPopupMenu(item, view);
            }

            @Override
            public void onStartDrag(PlayQueueItemHolder viewHolder) {
                if (itemTouchHelper != null) itemTouchHelper.startDrag(viewHolder);
            }
        };
    }

    private void onOpenDetail(int serviceId, String videoUrl, String videoTitle) {
        NavigationHelper.openVideoDetail(this, serviceId, videoUrl, videoTitle);
    }

    private void scrollToSelected() {
        itemsList.smoothScrollToPosition(player.playQueue.getIndex());
    }

    ////////////////////////////////////////////////////////////////////////////
    // Component On-Click Listener
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void onClick(View view) {
        if (view.getId() == repeatButton.getId()) {
            player.onRepeatClicked();

        } else if (view.getId() == backwardButton.getId()) {
            player.onPlayPrevious();

        } else if (view.getId() == playPauseButton.getId()) {
            player.onVideoPlayPause();

        } else if (view.getId() == forwardButton.getId()) {
            player.onPlayNext();

        } else if (view.getId() == shuffleButton.getId()) {
            player.onShuffleClicked();

        } else if (view.getId() == playbackSpeedButton.getId()) {
            playbackSpeedPopupMenu.show();

        } else if (view.getId() == playbackPitchButton.getId()) {
            playbackPitchPopupMenu.show();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Seekbar Listener
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) progressCurrentTime.setText(Localization.getDurationString(progress / 1000));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        seeking = true;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        player.simpleExoPlayer.seekTo(seekBar.getProgress());
        seeking = false;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Binding Service Listener
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPlaybackUpdate(int state, int repeatMode, boolean shuffled, PlaybackParameters parameters) {
        onStateChanged(state);
        onPlayModeChanged(repeatMode, shuffled);
        onPlaybackParameterChanged(parameters);
        scrollToSelected();
    }

    @Override
    public void onProgressUpdate(int currentProgress, int duration, int bufferPercent) {
        // Set buffer progress
        progressSeekBar.setSecondaryProgress((int) (progressSeekBar.getMax() * ((float) bufferPercent / 100)));

        // Set Duration
        progressSeekBar.setMax(duration);
        progressEndTime.setText(Localization.getDurationString(duration / 1000));

        // Set current time if not seeking
        if (!seeking) {
            progressSeekBar.setProgress(currentProgress);
            progressCurrentTime.setText(Localization.getDurationString(currentProgress / 1000));
        }
    }

    @Override
    public void onMetadataUpdate(StreamInfo info) {
        if (info != null) {
            metadataTitle.setText(info.name);
            metadataArtist.setText(info.uploader_name);
            scrollToSelected();
        }
    }

    @Override
    public void onServiceStopped() {
        unbind();
        finish();
    }

    ////////////////////////////////////////////////////////////////////////////
    // Binding Service Helper
    ////////////////////////////////////////////////////////////////////////////

    private void onStateChanged(final int state) {
        switch (state) {
            case BasePlayer.STATE_PAUSED:
                playPauseButton.setImageResource(R.drawable.ic_play_arrow_white);
                break;
            case BasePlayer.STATE_PLAYING:
                playPauseButton.setImageResource(R.drawable.ic_pause_white);
                break;
            case BasePlayer.STATE_COMPLETED:
                playPauseButton.setImageResource(R.drawable.ic_replay_white);
                break;
            default:
                break;
        }

        switch (state) {
            case BasePlayer.STATE_PAUSED:
            case BasePlayer.STATE_PLAYING:
            case BasePlayer.STATE_COMPLETED:
                playPauseButton.setClickable(true);
                playPauseButton.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                break;
            default:
                playPauseButton.setClickable(false);
                playPauseButton.setVisibility(View.INVISIBLE);
                progressBar.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void onPlayModeChanged(final int repeatMode, final boolean shuffled) {
        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                repeatButton.setImageResource(R.drawable.exo_controls_repeat_off);
                break;
            case Player.REPEAT_MODE_ONE:
                repeatButton.setImageResource(R.drawable.exo_controls_repeat_one);
                break;
            case Player.REPEAT_MODE_ALL:
                repeatButton.setImageResource(R.drawable.exo_controls_repeat_all);
                break;
        }

        final int shuffleAlpha = shuffled ? 255 : 77;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            shuffleButton.setImageAlpha(shuffleAlpha);
        } else {
            shuffleButton.setAlpha(shuffleAlpha);
        }
    }

    private void onPlaybackParameterChanged(final PlaybackParameters parameters) {
        if (parameters != null) {
            playbackSpeedButton.setText(player.formatSpeed(parameters.speed));
            playbackPitchButton.setText(player.formatPitch(parameters.pitch));
        }
    }
}
