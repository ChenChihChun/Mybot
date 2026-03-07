package com.mybot.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.net.URL;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "music_prefs";
    private static final String KEY_CHANNEL_ID = "youtube_channel_id";
    private static final String KEY_CHANNEL_TITLE = "youtube_channel_title";

    private MusicDbHelper db;
    private LinearLayout songListContainer;
    private LinearLayout chipContainer;
    private TextView channelLabel;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Bitmap> thumbCache = new HashMap<>();

    private static final int FILTER_ALL = -1;
    private static final int FILTER_FAVORITES = -2;
    private int currentFilter = FILTER_ALL;

    private List<MusicDbHelper.Song> playQueue = null;
    private int playIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new MusicDbHelper(this);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSongList();
        if (playQueue != null && playIndex >= 0 && playIndex < playQueue.size() - 1) {
            playIndex++;
            showPlayNextDialog();
        }
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);

        // ── Top bar ──
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(UIHelper.BG_TOP_BAR);
        int p = UIHelper.dp(this, 16);
        topBar.setPadding(p, UIHelper.dp(this, 12), p, UIHelper.dp(this, 12));
        topBar.setElevation(UIHelper.dp(this, 4));

        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190");
        backBtn.setTextSize(22);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setPadding(0, 0, UIHelper.dp(this, 12), 0);
        backBtn.setOnClickListener(v -> finish());

        TextView titleTv = new TextView(this);
        titleTv.setText("\uD83C\uDFB5 \u97F3\u6A02\u7BA1\u7406");
        titleTv.setTextSize(18);
        titleTv.setTextColor(UIHelper.TEXT_PRIMARY);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleTv.setLayoutParams(titleLp);

        TextView syncBtn = new TextView(this);
        syncBtn.setText("\uD83D\uDD04");
        syncBtn.setTextSize(20);
        syncBtn.setPadding(UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8), 0);
        syncBtn.setOnClickListener(v -> startSync());

        TextView addBtn = new TextView(this);
        addBtn.setText("\uFF0B");
        addBtn.setTextSize(22);
        addBtn.setTextColor(UIHelper.ACCENT_GREEN);
        addBtn.setPadding(UIHelper.dp(this, 8), 0, 0, 0);
        addBtn.setOnClickListener(v -> showAddDialog());

        topBar.addView(backBtn);
        topBar.addView(titleTv);
        topBar.addView(syncBtn);
        topBar.addView(addBtn);

        // ── Channel indicator (built here, added to root later) ──
        LinearLayout channelRow = null;
        String savedChannelTitle = getPrefs().getString(KEY_CHANNEL_TITLE, "");
        if (!savedChannelTitle.isEmpty()) {
            channelRow = new LinearLayout(this);
            channelRow.setOrientation(LinearLayout.HORIZONTAL);
            channelRow.setGravity(Gravity.CENTER_VERTICAL);
            channelRow.setBackgroundColor(UIHelper.BG_CARD);
            int crp = UIHelper.dp(this, 16);
            channelRow.setPadding(crp, UIHelper.dp(this, 6), crp, UIHelper.dp(this, 6));

            channelLabel = new TextView(this);
            channelLabel.setText("\uD83D\uDCFA " + savedChannelTitle);
            channelLabel.setTextSize(12);
            channelLabel.setTextColor(UIHelper.TEXT_SECONDARY);
            LinearLayout.LayoutParams clLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            channelLabel.setLayoutParams(clLp);

            TextView switchBtn = new TextView(this);
            switchBtn.setText("\u5207\u63DB\u983B\u9053");
            switchBtn.setTextSize(12);
            switchBtn.setTextColor(UIHelper.ACCENT_BLUE);
            switchBtn.setOnClickListener(v -> showChannelPicker());

            channelRow.addView(channelLabel);
            channelRow.addView(switchBtn);
        }

        // ── Filter chips ──
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setBackgroundColor(UIHelper.BG_TOP_BAR);
        int chipPad = UIHelper.dp(this, 8);
        chipScroll.setPadding(p, 0, p, chipPad);

        chipContainer = new LinearLayout(this);
        chipContainer.setOrientation(LinearLayout.HORIZONTAL);
        chipScroll.addView(chipContainer);

        // ── Song list ──
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        songListContainer = new LinearLayout(this);
        songListContainer.setOrientation(LinearLayout.VERTICAL);
        int sp = UIHelper.dp(this, 12);
        songListContainer.setPadding(sp, sp, sp, UIHelper.dp(this, 80));
        scrollView.addView(songListContainer);

        // ── Play all button ──
        TextView playAllBtn = new TextView(this);
        playAllBtn.setText("\u25B6 \u9023\u7E8C\u64AD\u653E");
        playAllBtn.setTextSize(14);
        playAllBtn.setTextColor(Color.WHITE);
        playAllBtn.setTypeface(Typeface.DEFAULT_BOLD);
        playAllBtn.setGravity(Gravity.CENTER);
        playAllBtn.setBackground(UIHelper.roundRect(UIHelper.ACCENT_RED, 24, this));
        playAllBtn.setPadding(UIHelper.dp(this, 24), UIHelper.dp(this, 12),
                UIHelper.dp(this, 24), UIHelper.dp(this, 12));
        playAllBtn.setElevation(UIHelper.dp(this, 6));
        playAllBtn.setOnClickListener(v -> startSequentialPlay());

        // Use FrameLayout-like approach: overlay button at bottom
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.addView(scrollView, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        android.widget.FrameLayout.LayoutParams fabLp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fabLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        fabLp.bottomMargin = UIHelper.dp(this, 16);
        frame.addView(playAllBtn, fabLp);

        root.addView(topBar);
        if (channelRow != null) root.addView(channelRow);
        root.addView(chipScroll);
        root.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        refreshChips();
    }

    private void refreshChips() {
        chipContainer.removeAllViews();
        List<MusicDbHelper.Category> cats = db.getAllCategories();

        addChip("\u5168\u90E8", FILTER_ALL);
        addChip("\u2B50\u6700\u611B", FILTER_FAVORITES);
        for (MusicDbHelper.Category cat : cats) {
            String label = (cat.icon != null && !cat.icon.isEmpty() ? cat.icon + " " : "") + cat.name;
            addChip(label, (int) cat.id);
        }
        // Add category chip
        TextView addCatChip = new TextView(this);
        addCatChip.setText(" \uFF0B ");
        addCatChip.setTextSize(13);
        addCatChip.setTextColor(UIHelper.TEXT_SECONDARY);
        addCatChip.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.TEXT_HINT, 20, 1, this));
        int cp = UIHelper.dp(this, 10);
        addCatChip.setPadding(cp, UIHelper.dp(this, 6), cp, UIHelper.dp(this, 6));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(UIHelper.dp(this, 4), 0, 0, 0);
        addCatChip.setLayoutParams(clp);
        addCatChip.setOnClickListener(v -> showAddCategoryDialog());
        chipContainer.addView(addCatChip);
    }

    private void addChip(String label, int filterId) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(13);
        boolean selected = (filterId == currentFilter);
        if (selected) {
            chip.setTextColor(Color.WHITE);
            chip.setBackground(UIHelper.roundRect(UIHelper.ACCENT_BLUE, 20, this));
        } else {
            chip.setTextColor(UIHelper.TEXT_SECONDARY);
            chip.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.TEXT_HINT, 20, 1, this));
        }
        int cp = UIHelper.dp(this, 12);
        chip.setPadding(cp, UIHelper.dp(this, 6), cp, UIHelper.dp(this, 6));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(UIHelper.dp(this, 4), 0, 0, 0);
        chip.setLayoutParams(clp);
        chip.setOnClickListener(v -> {
            currentFilter = filterId;
            refreshChips();
            refreshSongList();
        });
        // Long press on category chips to edit/delete
        if (filterId > 0) {
            chip.setOnLongClickListener(v -> {
                showEditCategoryDialog(filterId);
                return true;
            });
        }
        chipContainer.addView(chip);
    }

    private void refreshSongList() {
        songListContainer.removeAllViews();
        List<MusicDbHelper.Song> songs;
        if (currentFilter == FILTER_ALL) {
            songs = db.getAllSongs();
        } else if (currentFilter == FILTER_FAVORITES) {
            songs = db.getFavorites();
        } else {
            songs = db.getSongsByCategory(currentFilter);
        }

        if (songs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("\u9084\u6C92\u6709\u6B4C\u66F2\n\u9EDE\u64CA \uD83D\uDD04 \u5F9E YouTube \u540C\u6B65\uFF0C\u6216\u9EDE\u64CA \uFF0B \u65B0\u589E");
            empty.setTextSize(14);
            empty.setTextColor(UIHelper.TEXT_HINT);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, UIHelper.dp(this, 60), 0, 0);
            songListContainer.addView(empty);
            return;
        }

        for (MusicDbHelper.Song song : songs) {
            songListContainer.addView(buildSongCard(song));
        }
    }

    private LinearLayout buildSongCard(MusicDbHelper.Song song) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 12, this));
        int cp = UIHelper.dp(this, 10);
        card.setPadding(cp, cp, cp, cp);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, UIHelper.dp(this, 8));
        card.setLayoutParams(cardLp);
        card.setElevation(UIHelper.dp(this, 2));

        // Thumbnail
        ImageView thumb = new ImageView(this);
        int thumbSize = UIHelper.dp(this, 56);
        LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(thumbSize, UIHelper.dp(this, 42));
        thumbLp.setMargins(0, 0, UIHelper.dp(this, 10), 0);
        thumb.setLayoutParams(thumbLp);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(UIHelper.BG_INPUT);
        GradientDrawable thumbBg = UIHelper.roundRect(UIHelper.BG_INPUT, 6, this);
        thumb.setClipToOutline(true);
        thumb.setBackground(thumbBg);
        loadThumbnail(thumb, song.thumbnailUrl);

        // Text column
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textCol.setLayoutParams(textLp);

        TextView titleTv = new TextView(this);
        titleTv.setText(song.title);
        titleTv.setTextSize(14);
        titleTv.setTextColor(UIHelper.TEXT_PRIMARY);
        titleTv.setMaxLines(2);

        TextView channelTv = new TextView(this);
        channelTv.setText(song.channelTitle != null ? song.channelTitle : "");
        channelTv.setTextSize(11);
        channelTv.setTextColor(UIHelper.TEXT_HINT);
        channelTv.setMaxLines(1);

        textCol.addView(titleTv);
        textCol.addView(channelTv);

        // Category badge
        if (song.categoryName != null) {
            TextView badge = new TextView(this);
            badge.setText(song.categoryName);
            badge.setTextSize(10);
            badge.setTextColor(UIHelper.ACCENT_PURPLE);
            badge.setBackground(UIHelper.roundRectStroke(Color.TRANSPARENT, UIHelper.ACCENT_PURPLE, 8, 1, this));
            badge.setPadding(UIHelper.dp(this, 6), UIHelper.dp(this, 2),
                    UIHelper.dp(this, 6), UIHelper.dp(this, 2));
            textCol.addView(badge);
        }

        // Favorite toggle
        TextView favBtn = new TextView(this);
        favBtn.setText(song.isFavorite ? "\u2B50" : "\u2606");
        favBtn.setTextSize(20);
        favBtn.setPadding(UIHelper.dp(this, 8), 0, 0, 0);
        favBtn.setOnClickListener(v -> {
            db.toggleFavorite(song.id);
            refreshSongList();
        });

        card.addView(thumb);
        card.addView(textCol);
        card.addView(favBtn);

        // Click to play
        card.setOnClickListener(v -> playInYouTube(song.videoId));

        // Long press for context menu
        card.setOnLongClickListener(v -> {
            showSongContextMenu(song);
            return true;
        });

        return card;
    }

    private void playInYouTube(String videoId) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + videoId));
        if (intent.resolveActivity(getPackageManager()) == null) {
            intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/watch?v=" + videoId));
        }
        startActivity(intent);
    }

    private void loadThumbnail(ImageView imageView, String url) {
        if (url == null || url.isEmpty()) return;
        Bitmap cached = thumbCache.get(url);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }
        executor.execute(() -> {
            try {
                InputStream in = new URL(url).openStream();
                Bitmap bmp = BitmapFactory.decodeStream(in);
                in.close();
                if (bmp != null) {
                    thumbCache.put(url, bmp);
                    mainHandler.post(() -> imageView.setImageBitmap(bmp));
                }
            } catch (Exception ignored) {}
        });
    }

    // ── Sync ──
    private void startSync() {
        if (!GoogleAuthHelper.isSignedIn(this)) {
            Toast.makeText(this, "\u8ACB\u5148\u5728 Google \u65E5\u66C6\u529F\u80FD\u4E2D\u767B\u5165 Google \u5E33\u865F", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "\u6B63\u5728\u540C\u6B65...", Toast.LENGTH_SHORT).show();
        GoogleAuthHelper.getCachedOrFreshToken(this, (token, error) -> {
            if (token == null) {
                Toast.makeText(this, "\u53D6\u5F97 Token \u5931\u6557: " + error, Toast.LENGTH_LONG).show();
                return;
            }
            String savedChannelId = getPrefs().getString(KEY_CHANNEL_ID, "");
            if (!savedChannelId.isEmpty()) {
                loadPlaylistsForChannel(token, savedChannelId);
            } else {
                // First time: pick a channel
                pickChannelThenSync(token);
            }
        });
    }

    private void pickChannelThenSync(String token) {
        YouTubeClient.listMyChannels(token, (channels, err) -> {
            if (err != null) {
                Toast.makeText(this, "\u53D6\u5F97\u983B\u9053\u5931\u6557: " + err, Toast.LENGTH_LONG).show();
                return;
            }
            if (channels == null || channels.isEmpty()) {
                Toast.makeText(this, "\u627E\u4E0D\u5230 YouTube \u983B\u9053", Toast.LENGTH_SHORT).show();
                return;
            }
            if (channels.size() == 1) {
                saveChannel(channels.get(0));
                loadPlaylistsForChannel(token, channels.get(0).id);
            } else {
                showChannelSelectionDialog(token, channels);
            }
        });
    }

    private void showChannelSelectionDialog(String token, List<YouTubeClient.ChannelInfo> channels) {
        String[] names = new String[channels.size()];
        for (int i = 0; i < channels.size(); i++) {
            names[i] = channels.get(i).title;
        }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("\u9078\u64C7 YouTube \u983B\u9053")
                .setItems(names, (d, which) -> {
                    saveChannel(channels.get(which));
                    loadPlaylistsForChannel(token, channels.get(which).id);
                })
                .show();
    }

    private void saveChannel(YouTubeClient.ChannelInfo channel) {
        getPrefs().edit()
                .putString(KEY_CHANNEL_ID, channel.id)
                .putString(KEY_CHANNEL_TITLE, channel.title)
                .apply();
        // Rebuild UI to show channel indicator
        buildUI();
        refreshChips();
    }

    private void showChannelPicker() {
        if (!GoogleAuthHelper.isSignedIn(this)) {
            Toast.makeText(this, "\u8ACB\u5148\u767B\u5165 Google \u5E33\u865F", Toast.LENGTH_SHORT).show();
            return;
        }
        GoogleAuthHelper.getCachedOrFreshToken(this, (token, error) -> {
            if (token == null) {
                Toast.makeText(this, "\u53D6\u5F97 Token \u5931\u6557", Toast.LENGTH_SHORT).show();
                return;
            }
            YouTubeClient.listMyChannels(token, (channels, err) -> {
                if (err != null || channels == null || channels.isEmpty()) {
                    Toast.makeText(this, "\u53D6\u5F97\u983B\u9053\u5931\u6557", Toast.LENGTH_SHORT).show();
                    return;
                }
                showChannelSelectionDialog(token, channels);
            });
        });
    }

    private void loadPlaylistsForChannel(String token, String channelId) {
        YouTubeClient.listPlaylists(token, channelId, (playlists, err) -> {
            if (err != null) {
                Toast.makeText(this, "\u53D6\u5F97\u64AD\u653E\u6E05\u55AE\u5931\u6557: " + err, Toast.LENGTH_LONG).show();
                return;
            }
            if (playlists == null || playlists.isEmpty()) {
                Toast.makeText(this, "\u8A72\u983B\u9053\u627E\u4E0D\u5230\u64AD\u653E\u6E05\u55AE", Toast.LENGTH_SHORT).show();
                return;
            }
            showPlaylistSelectionDialog(token, playlists);
        });
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void showPlaylistSelectionDialog(String token, List<YouTubeClient.PlaylistInfo> playlists) {
        String[] names = new String[playlists.size()];
        boolean[] checked = new boolean[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) {
            YouTubeClient.PlaylistInfo pl = playlists.get(i);
            names[i] = pl.title + " (" + pl.itemCount + " \u9996)";
            checked[i] = true;
        }

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("\u9078\u64C7\u8981\u532F\u5165\u7684\u64AD\u653E\u6E05\u55AE")
                .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("\u532F\u5165", (d, w) -> {
                    List<YouTubeClient.PlaylistInfo> selected = new ArrayList<>();
                    for (int i = 0; i < playlists.size(); i++) {
                        if (checked[i]) selected.add(playlists.get(i));
                    }
                    importPlaylists(token, selected);
                })
                .setNegativeButton("\u53D6\u6D88", null)
                .show();
    }

    private void importPlaylists(String token, List<YouTubeClient.PlaylistInfo> playlists) {
        final int[] totalImported = {0};
        final int[] remaining = {playlists.size()};

        for (YouTubeClient.PlaylistInfo pl : playlists) {
            YouTubeClient.listPlaylistItems(token, pl.id, (videos, err) -> {
                if (videos != null) {
                    for (YouTubeClient.VideoInfo v : videos) {
                        db.insertOrUpdateSong(v.videoId, v.title, v.channelTitle,
                                v.thumbnailUrl, pl.id, v.playlistItemId);
                        totalImported[0]++;
                    }
                }
                remaining[0]--;
                if (remaining[0] <= 0) {
                    Toast.makeText(this, "\u540C\u6B65\u5B8C\u6210\uFF1A\u532F\u5165 " + totalImported[0] + " \u9996\u6B4C\u66F2", Toast.LENGTH_SHORT).show();
                    refreshSongList();
                }
            });
        }
    }

    // ── Add dialog ──
    private void showAddDialog() {
        String[] options = {"\u5F9E YouTube \u6E05\u55AE\u532F\u5165", "\u8CBC\u4E0A YouTube \u9023\u7D50", "\u641C\u5C0B YouTube"};
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("\u65B0\u589E\u6B4C\u66F2")
                .setItems(options, (d, which) -> {
                    if (which == 0) startSync();
                    else if (which == 1) showPasteUrlDialog();
                    else showSearchDialog();
                })
                .show();
    }

    private void showPasteUrlDialog() {
        EditText input = UIHelper.styledInput(this, "\u8CBC\u4E0A YouTube \u9023\u7D50");
        int pad = UIHelper.dp(this, 20);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("\u8CBC\u4E0A YouTube \u9023\u7D50")
                .setView(container)
                .setPositiveButton("\u65B0\u589E", (d, w) -> {
                    String url = input.getText().toString().trim();
                    String videoId = extractVideoId(url);
                    if (videoId == null) {
                        Toast.makeText(this, "\u7121\u6548\u7684 YouTube \u9023\u7D50", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addVideoById(videoId);
                })
                .setNegativeButton("\u53D6\u6D88", null)
                .show();
    }

    private void showSearchDialog() {
        EditText input = UIHelper.styledInput(this, "\u641C\u5C0B\u95DC\u9375\u5B57");
        int pad = UIHelper.dp(this, 20);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("\u641C\u5C0B YouTube")
                .setView(container)
                .setPositiveButton("\u641C\u5C0B", (d, w) -> {
                    String query = input.getText().toString().trim();
                    if (query.isEmpty()) return;
                    GoogleAuthHelper.getCachedOrFreshToken(this, (token, error) -> {
                        if (token == null) {
                            Toast.makeText(this, "\u53D6\u5F97 Token \u5931\u6557", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        YouTubeClient.searchVideos(token, query, (videos, err) -> {
                            if (err != null) {
                                Toast.makeText(this, "\u641C\u5C0B\u5931\u6557: " + err, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (videos == null || videos.isEmpty()) {
                                Toast.makeText(this, "\u627E\u4E0D\u5230\u7D50\u679C", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            showSearchResults(videos);
                        });
                    });
                })
                .setNegativeButton("\u53D6\u6D88", null)
                .show();
    }

    private void showSearchResults(List<YouTubeClient.VideoInfo> videos) {
        String[] names = new String[videos.size()];
        for (int i = 0; i < videos.size(); i++) {
            YouTubeClient.VideoInfo v = videos.get(i);
            names[i] = v.title + "\n" + v.channelTitle;
        }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("\u641C\u5C0B\u7D50\u679C")
                .setItems(names, (d, which) -> {
                    YouTubeClient.VideoInfo v = videos.get(which);
                    db.insertOrUpdateSong(v.videoId, v.title, v.channelTitle, v.thumbnailUrl, null, null);
                    Toast.makeText(this, "\u5DF2\u65B0\u589E: " + v.title, Toast.LENGTH_SHORT).show();
                    refreshSongList();
                })
                .show();
    }

    private void addVideoById(String videoId) {
        if (db.getSongByVideoId(videoId) != null) {
            Toast.makeText(this, "\u6B4C\u66F2\u5DF2\u5B58\u5728", Toast.LENGTH_SHORT).show();
            return;
        }
        GoogleAuthHelper.getCachedOrFreshToken(this, (token, error) -> {
            if (token == null) {
                db.insertOrUpdateSong(videoId, "YouTube Video", "", "", null, null);
                Toast.makeText(this, "\u5DF2\u65B0\u589E (\u7121\u6CD5\u53D6\u5F97\u8A73\u7D30\u8CC7\u8A0A)", Toast.LENGTH_SHORT).show();
                refreshSongList();
                return;
            }
            YouTubeClient.getVideoInfo(token, videoId, (videos, err) -> {
                if (videos != null && !videos.isEmpty()) {
                    YouTubeClient.VideoInfo v = videos.get(0);
                    db.insertOrUpdateSong(v.videoId, v.title, v.channelTitle, v.thumbnailUrl, null, null);
                    Toast.makeText(this, "\u5DF2\u65B0\u589E: " + v.title, Toast.LENGTH_SHORT).show();
                } else {
                    db.insertOrUpdateSong(videoId, "YouTube Video", "", "", null, null);
                    Toast.makeText(this, "\u5DF2\u65B0\u589E", Toast.LENGTH_SHORT).show();
                }
                refreshSongList();
            });
        });
    }

    // ── Context menu ──
    private void showSongContextMenu(MusicDbHelper.Song song) {
        String[] options = {
                (song.isFavorite ? "\u53D6\u6D88\u6700\u611B" : "\u2B50 \u6A19\u8A18\u6700\u611B"),
                "\uD83D\uDCC2 \u79FB\u5230\u5206\u985E",
                "\uD83D\uDD17 \u8907\u88FD YouTube \u9023\u7D50",
                "\uD83D\uDDD1 \u522A\u9664"
        };
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(song.title)
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0:
                            db.toggleFavorite(song.id);
                            refreshSongList();
                            break;
                        case 1:
                            showMoveToCategoryDialog(song);
                            break;
                        case 2:
                            android.content.ClipboardManager clipboard =
                                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                                    "YouTube URL", "https://www.youtube.com/watch?v=" + song.videoId));
                            Toast.makeText(this, "\u5DF2\u8907\u88FD\u9023\u7D50", Toast.LENGTH_SHORT).show();
                            break;
                        case 3:
                            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                                    .setTitle("\u78BA\u5B9A\u522A\u9664\uFF1F")
                                    .setMessage(song.title)
                                    .setPositiveButton("\u522A\u9664", (dd, ww) -> {
                                        db.deleteSong(song.id);
                                        refreshSongList();
                                    })
                                    .setNegativeButton("\u53D6\u6D88", null)
                                    .show();
                            break;
                    }
                })
                .show();
    }

    private void showMoveToCategoryDialog(MusicDbHelper.Song song) {
        List<MusicDbHelper.Category> cats = db.getAllCategories();
        String[] names = new String[cats.size() + 1];
        names[0] = "(\u7121\u5206\u985E)";
        for (int i = 0; i < cats.size(); i++) {
            MusicDbHelper.Category cat = cats.get(i);
            names[i + 1] = (cat.icon != null ? cat.icon + " " : "") + cat.name;
        }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("\u9078\u64C7\u5206\u985E")
                .setItems(names, (d, which) -> {
                    if (which == 0) {
                        db.setSongCategory(song.id, 0);
                    } else {
                        db.setSongCategory(song.id, cats.get(which - 1).id);
                    }
                    refreshSongList();
                    refreshChips();
                })
                .show();
    }

    // ── Category management ──
    private void showAddCategoryDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = UIHelper.dp(this, 20);
        container.setPadding(pad, pad, pad, 0);

        EditText nameInput = UIHelper.styledInput(this, "\u5206\u985E\u540D\u7A31");
        EditText iconInput = UIHelper.styledInput(this, "Emoji \u5716\u793A (\u53EF\u7559\u7A7A)");
        iconInput.setPadding(iconInput.getPaddingLeft(), UIHelper.dp(this, 8),
                iconInput.getPaddingRight(), iconInput.getPaddingBottom());

        container.addView(nameInput);
        container.addView(iconInput);

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("\u65B0\u589E\u5206\u985E")
                .setView(container)
                .setPositiveButton("\u65B0\u589E", (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) return;
                    String icon = iconInput.getText().toString().trim();
                    db.insertCategory(name, icon.isEmpty() ? null : icon);
                    refreshChips();
                })
                .setNegativeButton("\u53D6\u6D88", null)
                .show();
    }

    private void showEditCategoryDialog(int categoryId) {
        List<MusicDbHelper.Category> cats = db.getAllCategories();
        MusicDbHelper.Category target = null;
        for (MusicDbHelper.Category c : cats) {
            if (c.id == categoryId) { target = c; break; }
        }
        if (target == null) return;
        MusicDbHelper.Category cat = target;

        String[] options = {"\u7DE8\u8F2F\u5206\u985E", "\u522A\u9664\u5206\u985E"};
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(cat.name)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        LinearLayout container = new LinearLayout(this);
                        container.setOrientation(LinearLayout.VERTICAL);
                        int pad = UIHelper.dp(this, 20);
                        container.setPadding(pad, pad, pad, 0);
                        EditText nameInput = UIHelper.styledInput(this, "\u5206\u985E\u540D\u7A31");
                        nameInput.setText(cat.name);
                        EditText iconInput = UIHelper.styledInput(this, "Emoji");
                        iconInput.setText(cat.icon != null ? cat.icon : "");
                        container.addView(nameInput);
                        container.addView(iconInput);
                        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                                .setTitle("\u7DE8\u8F2F\u5206\u985E")
                                .setView(container)
                                .setPositiveButton("\u5132\u5B58", (dd, ww) -> {
                                    String name = nameInput.getText().toString().trim();
                                    if (name.isEmpty()) return;
                                    String icon = iconInput.getText().toString().trim();
                                    db.updateCategory(cat.id, name, icon.isEmpty() ? null : icon);
                                    refreshChips();
                                    refreshSongList();
                                })
                                .setNegativeButton("\u53D6\u6D88", null)
                                .show();
                    } else {
                        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                                .setTitle("\u78BA\u5B9A\u522A\u9664\u5206\u985E\uFF1F")
                                .setMessage("\u5206\u985E\u5167\u7684\u6B4C\u66F2\u6703\u8B8A\u6210\u7121\u5206\u985E")
                                .setPositiveButton("\u522A\u9664", (dd, ww) -> {
                                    db.deleteCategory(cat.id);
                                    if (currentFilter == categoryId) currentFilter = FILTER_ALL;
                                    refreshChips();
                                    refreshSongList();
                                })
                                .setNegativeButton("\u53D6\u6D88", null)
                                .show();
                    }
                })
                .show();
    }

    // ── Sequential play ──
    private void startSequentialPlay() {
        List<MusicDbHelper.Song> songs;
        if (currentFilter == FILTER_ALL) {
            songs = db.getAllSongs();
        } else if (currentFilter == FILTER_FAVORITES) {
            songs = db.getFavorites();
        } else {
            songs = db.getSongsByCategory(currentFilter);
        }
        if (songs.isEmpty()) {
            Toast.makeText(this, "\u6C92\u6709\u6B4C\u66F2\u53EF\u64AD\u653E", Toast.LENGTH_SHORT).show();
            return;
        }
        playQueue = songs;
        playIndex = 0;
        Toast.makeText(this, "\u9023\u7E8C\u64AD\u653E " + songs.size() + " \u9996\u6B4C\u66F2", Toast.LENGTH_SHORT).show();
        playInYouTube(songs.get(0).videoId);
    }

    private void showPlayNextDialog() {
        if (playQueue == null || playIndex >= playQueue.size()) {
            playQueue = null;
            playIndex = -1;
            return;
        }
        MusicDbHelper.Song next = playQueue.get(playIndex);
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("\u9023\u7E8C\u64AD\u653E " + playIndex + "/" + playQueue.size())
                .setMessage("\u4E0B\u4E00\u9996: " + next.title)
                .setPositiveButton("\u64AD\u653E", (d, w) -> playInYouTube(next.videoId))
                .setNegativeButton("\u505C\u6B62", (d, w) -> {
                    playQueue = null;
                    playIndex = -1;
                })
                .show();
    }

    // ── Utility ──
    static String extractVideoId(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            Uri uri = Uri.parse(url);
            String vid = uri.getQueryParameter("v");
            if (vid != null && !vid.isEmpty()) return vid;
            String host = uri.getHost();
            if (host != null && host.contains("youtu.be")) {
                return uri.getLastPathSegment();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
