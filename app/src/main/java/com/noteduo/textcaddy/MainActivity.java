package com.noteduo.textcaddy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity implements DeckView.Listener {
    private static final int REQUEST_EXPORT = 4101;
    private static final int REQUEST_IMPORT = 4102;
    private static final int INK = Color.rgb(39, 37, 57);
    private static final int MUTED = Color.rgb(130, 127, 149);
    private static final int ACCENT = Color.rgb(109, 99, 203);
    private static final int BG = Color.rgb(244, 242, 248);
    private static final int[] GROUP_COLORS = new int[]{
            Color.rgb(255, 202, 145), Color.rgb(151, 196, 255), Color.rgb(145, 218, 170),
            Color.rgb(190, 159, 248), Color.rgb(255, 155, 177), Color.rgb(244, 211, 111),
            Color.rgb(105, 202, 195), Color.rgb(206, 177, 151)
    };

    private DataStore store;
    private SoundEngine sound;
    private android.content.SharedPreferences settings;
    private LinearLayout root;
    private ArcGroupRail groupRail;
    private PopupWindow groupDrawerPopup;
    private LinearLayout header;
    private TextView titleText;
    private TextView subtitleText;
    private TextView countText;
    private TextView activeGroupText;
    private DeckView deck;
    private String currentGroupId = "all";
    private final Map<String, String> lastCardByGroup = new HashMap<>();
    private boolean arcEnabled = true;
    private boolean hapticsEnabled = false;
    private float soundVolume = 0.36f;
    private boolean soundEnabled = true;
    private boolean railSoundEnabled = true;
    private boolean cardSoundEnabled = true;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new DataStore(this);
        settings = getSharedPreferences("text_caddy_settings", MODE_PRIVATE);
        soundEnabled = settings.getBoolean("sound_enabled", true);
        railSoundEnabled = settings.getBoolean("rail_sound_enabled", true);
        cardSoundEnabled = settings.getBoolean("card_sound_enabled", true);
        hapticsEnabled = settings.getBoolean("haptics_enabled", false);
        arcEnabled = settings.getBoolean("arc_enabled", true);
        soundVolume = settings.getInt("sound_volume", 36) / 100f;
        sound = new SoundEngine(this, soundEnabled, soundVolume);
        sound.setRailEnabled(railSoundEnabled);
        sound.setCardEnabled(cardSoundEnabled);
        currentGroupId = settings.getString("current_group", "all");
        if (!"all".equals(currentGroupId) && store.getGroup(currentGroupId) == null) currentGroupId = "all";
        buildLayout();
        renderAll();
    }

    @Override public void onBackPressed() {
        if (groupDrawerPopup != null && groupDrawerPopup.isShowing()) {
            groupDrawerPopup.dismiss();
            return;
        }
        super.onBackPressed();
    }

    private void buildLayout() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        // A narrow circular-dial rail keeps most horizontal space available for text cards.
        groupRail = new ArcGroupRail(this, sound, new ArcGroupRail.Listener() {
            @Override public void onGroupSelected(String groupId) { selectGroup(groupId); }
            @Override public void onOpenGroupList() { showGroupDrawer(); }
            @Override public void onAddGroup() { showGroupEditor(null); }
        });
        groupRail.setHapticsEnabled(hapticsEnabled);
        root.addView(groupRail, new LinearLayout.LayoutParams(dp(50), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        root.addView(main, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        // Compact top bar; the saved text itself remains the main visual focus.
        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(3), dp(8), dp(3));
        main.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        titleText = text("全部记录", 18, INK, true);
        titleText.setSingleLine(true);
        titleText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitleText = text("快速浏览文字卡片", 10, MUTED, false);
        // Keep the subtitle model available for state updates, but do not spend screen height on it.
        subtitleText.setVisibility(View.GONE);
        heading.addView(titleText);
        header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView searchButton = smallTopButton("⌕", "搜索全部记录");
        searchButton.setOnClickListener(v -> showSearchDialog());
        header.addView(searchButton, new LinearLayout.LayoutParams(dp(35), dp(35)));
        TextView settingsButton = smallTopButton("☷", "设置");
        LinearLayout.LayoutParams settingLp = new LinearLayout.LayoutParams(dp(35), dp(35));
        settingLp.leftMargin = dp(4);
        settingsButton.setOnClickListener(v -> showSettings());
        header.addView(settingsButton, settingLp);

        FrameLayout deckFrame = new FrameLayout(this);
        deck = new DeckView(this, this, sound);
        deck.setArcEnabled(arcEnabled);
        deck.setHapticsEnabled(hapticsEnabled);
        deckFrame.addView(deck, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams deckLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        deckLp.leftMargin = dp(1);
        deckLp.rightMargin = dp(1);
        main.addView(deckFrame, deckLp);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(8), dp(2), dp(8), dp(2));
        main.addView(footer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(49)));

        LinearLayout footerInfo = new LinearLayout(this);
        footerInfo.setOrientation(LinearLayout.VERTICAL);
        footerInfo.setGravity(Gravity.CENTER_VERTICAL);
        countText = text("0 张卡片", 12, INK, true);
        countText.setSingleLine(true);
        countText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        activeGroupText = text("上下快速拨动", 9, MUTED, false);
        activeGroupText.setSingleLine(true);
        activeGroupText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        footerInfo.addView(countText);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.topMargin = dp(1);
        footerInfo.addView(activeGroupText, infoLp);
        footer.addView(footerInfo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView addCard = text("＋ 新建卡片", 12, Color.WHITE, true);
        addCard.setGravity(Gravity.CENTER);
        addCard.setBackground(roundBg(ACCENT, dp(15)));
        addCard.setElevation(dp(2));
        addCard.setPadding(dp(12), 0, dp(12), 0);
        addCard.setOnClickListener(v -> showCardEditor(null));
        footer.addView(addCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(39)));
    }

    private void renderAll() {
        renderGroups();
        renderCards();
        String name = getCurrentGroupName();
        titleText.setText(name);
        subtitleText.setText("快速浏览文字卡片");
        settings.edit().putString("current_group", currentGroupId).apply();
    }

    private void renderGroups() {
        List<ArcGroupRail.Tab> tabs = new ArrayList<>();
        tabs.add(new ArcGroupRail.Tab("all", "全", "全部记录", ACCENT, store.getCards("all").size()));
        for (GroupRecord group : store.getGroups()) {
            String name = group.name == null ? "组别" : group.name.trim();
            String shortLabel = "收藏".equals(name) ? "藏" : (name.isEmpty() ? "组" : name.substring(0, 1));
            tabs.add(new ArcGroupRail.Tab(group.id, shortLabel, name, group.color, store.getCards(group.id).size()));
        }
        groupRail.setTabs(tabs, currentGroupId);
    }

    private void selectGroup(String id) {
        if (!currentGroupId.equals(id)) {
            currentGroupId = id;
            sound.stopRotate();
            renderAll();
        }
    }

    private void showGroupDrawer() {
        if (groupDrawerPopup != null && groupDrawerPopup.isShowing()) return;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(15), dp(12), dp(13), dp(12));
        panel.setBackground(roundBg(Color.rgb(252, 251, 255), dp(18)));
        panel.setElevation(dp(16));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("选择组别", 18, INK, true);
        heading.addView(label, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView close = text("×", 25, MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭组别列表");
        heading.addView(close, new LinearLayout.LayoutParams(dp(38), dp(38)));
        panel.addView(heading);

        TextView hint = text("轻触切换；长按组别进行管理", 11, MUTED, false);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28));
        hintLp.topMargin = dp(1);
        panel.addView(hint, hintLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(options);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        addDrawerGroupRow(options, "all", "全部记录", ACCENT, store.getCards("all").size(), true);
        for (GroupRecord group : store.getGroups()) {
            addDrawerGroupRow(options, group.id, group.name, group.color, store.getCards(group.id).size(), false);
        }

        TextView add = text("＋  新建组别", 13, ACCENT, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(roundBg(Color.rgb(239, 236, 251), dp(12)));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(45));
        addLp.topMargin = dp(8);
        panel.addView(add, addLp);

        PopupWindow popup = new PopupWindow(panel,
                Math.min(dp(286), (int) (getResources().getDisplayMetrics().widthPixels * 0.78f)),
                root.getHeight() > 0 ? root.getHeight() : ViewGroup.LayoutParams.MATCH_PARENT, true);
        popup.setBackgroundDrawable(roundBg(Color.rgb(252, 251, 255), dp(18)));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(16));
        popup.setClippingEnabled(true);
        popup.setOnDismissListener(() -> { if (groupDrawerPopup == popup) groupDrawerPopup = null; });
        groupDrawerPopup = popup;
        close.setOnClickListener(v -> popup.dismiss());
        add.setOnClickListener(v -> {
            popup.dismiss();
            showGroupEditor(null);
        });
        popup.showAtLocation(root, Gravity.TOP | Gravity.LEFT, 0, 0);
    }

    private void addDrawerGroupRow(LinearLayout parent, String id, String name, int color, int count, boolean all) {
        boolean selected = id.equals(currentGroupId);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(9), dp(6), dp(8), dp(6));
        row.setBackground(roundBg(selected ? Color.rgb(239, 236, 251) : Color.TRANSPARENT, dp(11)));

        View dot = new View(this);
        dot.setBackground(roundBg(color, dp(5)));
        row.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));

        TextView nameView = text(name, 14, selected ? ACCENT : INK, selected);
        nameView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        nameView.setPadding(dp(10), dp(3), dp(4), dp(3));
        row.addView(nameView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setMinimumHeight(dp(50));

        TextView countView = text(String.valueOf(count), 11, MUTED, false);
        countView.setGravity(Gravity.CENTER);
        row.addView(countView, new LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(3);
        parent.addView(row, rowLp);
        row.setOnClickListener(v -> {
            selectGroup(id);
            if (groupDrawerPopup != null && groupDrawerPopup.isShowing()) groupDrawerPopup.dismiss();
        });
        if (!all) row.setOnLongClickListener(v -> {
            if (groupDrawerPopup != null && groupDrawerPopup.isShowing()) groupDrawerPopup.dismiss();
            showGroupMenu(id);
            return true;
        });
    }

    private void renderCards() {
        List<CardRecord> cards = store.getCards(currentGroupId);
        String selectedId = lastCardByGroup.get(currentGroupId);
        if (selectedId == null && deck != null) {
            CardRecord previous = deck.getSelectedCard();
            if (previous != null) {
                for (CardRecord candidate : cards) {
                    if (candidate.id.equals(previous.id)) {
                        selectedId = previous.id;
                        break;
                    }
                }
            }
        }
        deck.setCards(cards, selectedId);
        countText.setText(cards.size() + " 张卡片");
        updateSelectedInfo(deck.getSelectedCard());
    }

    private void updateSelectedInfo(CardRecord card) {
        if (card == null) {
            activeGroupText.setText(store.getCards(currentGroupId).isEmpty() ? "粘贴一段文字，建立第一张卡片" : "上下拨动浏览");
        } else {
            String marker = card.pinned ? "置顶 · " : (card.favorite ? "已收藏 · " : "");
            activeGroupText.setText(marker + "轻触阅读 · 长按操作");
        }
    }

    @Override public void onCardChanged(CardRecord card) {
        if (card != null) lastCardByGroup.put(currentGroupId, card.id);
        updateSelectedInfo(card);
    }

    @Override public void onCardTap(CardRecord card) {
        if (card != null) showCardDetail(card.id);
        else showCardEditor(null);
    }

    @Override public void onCardLongPress(CardRecord card) {
        if (card != null) showCardMenu(card.id);
    }

    @Override public void onSettled(CardRecord card) {
        if (card != null) lastCardByGroup.put(currentGroupId, card.id);
    }

    private void showCardEditor(CardRecord original) {
        final EditText titleInput = new EditText(this);
        titleInput.setSingleLine(true);
        titleInput.setTextSize(15);
        titleInput.setHint("标题（可选）");
        titleInput.setText(original == null ? "" : original.title);
        titleInput.setPadding(dp(12), dp(11), dp(12), dp(11));
        titleInput.setBackground(roundBg(Color.rgb(247, 246, 251), dp(12)));

        final EditText bodyInput = new EditText(this);
        bodyInput.setGravity(Gravity.TOP | Gravity.START);
        bodyInput.setTextSize(15);
        bodyInput.setLineSpacing(dp(3), 1f);
        bodyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        bodyInput.setHint("在这里粘贴或输入文字记录…");
        bodyInput.setText(original == null ? "" : original.body);
        bodyInput.setMinLines(8);
        bodyInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        bodyInput.setBackground(roundBg(Color.rgb(247, 246, 251), dp(12)));

        final String[] selectedGroup = new String[]{original != null ? original.groupId : defaultGroupForNewCard()};
        final int[] selectedColor = new int[]{original != null ? original.color : DataStore.PALETTE[(store.getCards("all").size()) % DataStore.PALETTE.length]};
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView groupPick = smallChoice("分组：" + groupName(selectedGroup[0]));
        TextView colorPick = smallChoice("卡片颜色  ●");
        colorPick.setTextColor(INK);
        colorPick.setCompoundDrawables(null, null, null, null);
        colorPick.setBackground(roundBg(selectedColor[0], dp(11)));
        colorPick.setText("　颜色　");
        controls.addView(groupPick, new LinearLayout.LayoutParams(0, dp(39), 1f));
        LinearLayout.LayoutParams colorLp = new LinearLayout.LayoutParams(dp(94), dp(39));
        colorLp.leftMargin = dp(7);
        controls.addView(colorPick, colorLp);

        TextView paste = text("从剪贴板粘贴", 12, ACCENT, true);
        paste.setGravity(Gravity.CENTER);
        paste.setBackground(roundBg(Color.rgb(238, 235, 252), dp(11)));
        paste.setPadding(dp(9), 0, dp(9), 0);
        LinearLayout editorContent = new LinearLayout(this);
        editorContent.setOrientation(LinearLayout.VERTICAL);
        editorContent.setPadding(dp(18), dp(6), dp(18), dp(3));
        editorContent.addView(titleInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(47)));
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(205));
        bodyLp.topMargin = dp(10);
        editorContent.addView(bodyInput, bodyLp);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(39));
        controlsLp.topMargin = dp(10);
        editorContent.addView(controls, controlsLp);
        LinearLayout.LayoutParams pasteLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(35));
        pasteLp.topMargin = dp(9);
        editorContent.addView(paste, pasteLp);

        groupPick.setOnClickListener(v -> {
            List<GroupRecord> groups = store.getGroups();
            String[] names = new String[groups.size()];
            for (int i = 0; i < groups.size(); i++) names[i] = groups.get(i).name;
            new AlertDialog.Builder(this).setTitle("选择所属组别").setItems(names, (d, which) -> {
                selectedGroup[0] = groups.get(which).id;
                groupPick.setText("分组：" + groupName(selectedGroup[0]));
            }).show();
        });
        colorPick.setOnClickListener(v -> {
            int current = 0;
            for (int i = 0; i < DataStore.PALETTE.length; i++) if (DataStore.PALETTE[i] == selectedColor[0]) current = i;
            selectedColor[0] = DataStore.PALETTE[(current + 1) % DataStore.PALETTE.length];
            colorPick.setBackground(roundBg(selectedColor[0], dp(11)));
        });
        paste.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (text != null) bodyInput.setText(text);
            } else Toast.makeText(this, "剪贴板中没有文字", Toast.LENGTH_SHORT).show();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(original == null ? "新建文字卡片" : "编辑文字卡片")
                .setView(editorContent)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String body = bodyInput.getText().toString();
            String title = titleInput.getText().toString().trim();
            if (body.trim().isEmpty() && title.isEmpty()) {
                Toast.makeText(this, "请先输入或粘贴一些文字", Toast.LENGTH_SHORT).show();
                return;
            }
            if (original == null) {
                CardRecord added = store.addCard(selectedGroup[0], title, body, selectedColor[0]);
                lastCardByGroup.put(currentGroupId, added.id);
            } else {
                original.title = title;
                original.body = body;
                original.groupId = selectedGroup[0];
                original.color = selectedColor[0];
                store.updateCard(original);
                lastCardByGroup.put(currentGroupId, original.id);
            }
            dialog.dismiss();
            renderAll();
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void showCardDetail(String id) {
        CardRecord card = store.getCard(id);
        if (card == null) return;
        Dialog dialog = new Dialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(19), dp(17), dp(19), dp(16));
        content.setBackground(roundBg(Color.rgb(255, 254, 255), dp(24)));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(card.displayTitle(), 19, INK, true);
        heading.setMaxLines(2);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = text("×", 25, MUTED, false);
        close.setGravity(Gravity.CENTER);
        top.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));
        content.addView(top);

        LinearLayout metadata = new LinearLayout(this);
        metadata.setOrientation(LinearLayout.HORIZONTAL);
        metadata.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        GroupRecord group = store.getGroup(card.groupId);
        dot.setBackground(roundBg(group == null ? ACCENT : group.color, dp(5)));
        metadata.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        TextView groupText = text("  " + (group == null ? "未分类" : group.name)
                + (card.pinned ? "  ·  置顶" : "") + (card.favorite ? "  ·  收藏" : ""), 11, MUTED, false);
        metadata.addView(groupText);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28));
        metaLp.topMargin = dp(4);
        content.addView(metadata, metaLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        TextView body = text(card.body, 16, Color.rgb(54, 52, 70), false);
        body.setTextIsSelectable(true);
        body.setLineSpacing(dp(5), 1.08f);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setPadding(dp(2), dp(11), dp(2), dp(12));
        scroll.addView(body);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollLp.topMargin = dp(4);
        scrollLp.bottomMargin = dp(9);
        content.addView(scroll, scrollLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView copy = actionButton("复制全文", ACCENT, Color.WHITE);
        TextView edit = actionButton("编辑", INK, Color.WHITE);
        TextView more = actionButton("更多", Color.rgb(240, 238, 247), INK);
        actions.addView(copy, new LinearLayout.LayoutParams(0, dp(43), 1.25f));
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(0, dp(43), 0.8f);
        editLp.leftMargin = dp(6);
        actions.addView(edit, editLp);
        LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(0, dp(43), 0.8f);
        moreLp.leftMargin = dp(6);
        actions.addView(more, moreLp);
        content.addView(actions);

        dialog.setContentView(content);
        close.setOnClickListener(v -> dialog.dismiss());
        copy.setOnClickListener(v -> { copyText(card.body, "已复制全文"); });
        edit.setOnClickListener(v -> { dialog.dismiss(); showCardEditor(store.getCard(id)); });
        more.setOnClickListener(v -> { dialog.dismiss(); showCardMenu(id); });
        dialog.setOnDismissListener(d -> renderAll());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.91f), (int) (getResources().getDisplayMetrics().heightPixels * 0.78f));
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void showCardMenu(String id) {
        CardRecord card = store.getCard(id);
        if (card == null) return;
        String[] options = new String[]{"打开阅读", "复制全文", card.favorite ? "取消收藏" : "加入收藏",
                card.pinned ? "取消置顶" : "置顶此卡片", "移动到其他组别", "分享文字", "编辑", "删除"};
        new AlertDialog.Builder(this).setTitle(card.displayTitle()).setItems(options, (dialog, which) -> {
            CardRecord latest = store.getCard(id);
            if (latest == null) return;
            switch (which) {
                case 0: showCardDetail(id); break;
                case 1: copyText(latest.body, "已复制全文"); break;
                case 2: store.toggleFavorite(id); renderAll(); break;
                case 3: store.togglePinned(id); renderAll(); break;
                case 4: showMoveCardDialog(id); break;
                case 5: shareText(latest); break;
                case 6: showCardEditor(latest); break;
                case 7: confirmDeleteCard(id); break;
            }
        }).show();
    }

    private void confirmDeleteCard(String id) {
        CardRecord card = store.getCard(id);
        if (card == null) return;
        new AlertDialog.Builder(this).setTitle("删除这张卡片？")
                .setMessage("删除后无法直接恢复。建议先确认文字不再需要。")
                .setNegativeButton("取消", null)
                .setNeutralButton("删除", (d, w) -> {
                    store.deleteCard(id);
                    java.util.Iterator<Map.Entry<String, String>> iterator = lastCardByGroup.entrySet().iterator();
                    while (iterator.hasNext()) {
                        if (id.equals(iterator.next().getValue())) iterator.remove();
                    }
                    renderAll();
                    Toast.makeText(this, "卡片已删除", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void showMoveCardDialog(String id) {
        List<GroupRecord> groups = store.getGroups();
        String[] names = new String[groups.size()];
        for (int i = 0; i < groups.size(); i++) names[i] = groups.get(i).name;
        new AlertDialog.Builder(this).setTitle("移动到组别").setItems(names, (d, which) -> {
            store.moveCard(id, groups.get(which).id);
            currentGroupId = groups.get(which).id;
            lastCardByGroup.put(currentGroupId, id);
            renderAll();
        }).show();
    }

    private void showGroupEditor(GroupRecord existing) {
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("组别名称");
        nameInput.setText(existing == null ? "" : existing.name);
        nameInput.setTextSize(15);
        nameInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        nameInput.setBackground(roundBg(Color.rgb(247, 246, 251), dp(12)));
        final int[] color = new int[]{existing == null ? GROUP_COLORS[store.getGroups().size() % GROUP_COLORS.length] : existing.color};
        TextView colorButton = text("点按更换组别颜色", 12, INK, true);
        colorButton.setGravity(Gravity.CENTER);
        colorButton.setBackground(roundBg(color[0], dp(12)));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), dp(2));
        box.addView(nameInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        LinearLayout.LayoutParams colorLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(43));
        colorLp.topMargin = dp(11);
        box.addView(colorButton, colorLp);
        colorButton.setOnClickListener(v -> {
            int index = 0;
            for (int i = 0; i < GROUP_COLORS.length; i++) if (GROUP_COLORS[i] == color[0]) index = i;
            color[0] = GROUP_COLORS[(index + 1) % GROUP_COLORS.length];
            colorButton.setBackground(roundBg(color[0], dp(12)));
        });
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(existing == null ? "新建组别" : "编辑组别")
                .setView(box).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) { Toast.makeText(this, "组别名称不能为空", Toast.LENGTH_SHORT).show(); return; }
            if (existing == null) {
                GroupRecord added = store.addGroup(name, color[0]);
                currentGroupId = added.id;
            } else {
                existing.name = name;
                existing.color = color[0];
                store.updateGroup(existing);
            }
            dialog.dismiss();
            renderAll();
        }));
        dialog.show();
    }

    private void showGroupMenu(String id) {
        GroupRecord group = store.getGroup(id);
        if (group == null) return;
        String[] options = new String[]{"重命名 / 改色", "上移组别", "下移组别", "删除组别"};
        new AlertDialog.Builder(this).setTitle(group.name).setItems(options, (d, which) -> {
            GroupRecord latest = store.getGroup(id);
            if (latest == null) return;
            switch (which) {
                case 0: showGroupEditor(latest); break;
                case 1: store.moveGroup(id, -1); renderAll(); break;
                case 2: store.moveGroup(id, 1); renderAll(); break;
                case 3:
                    if (store.getGroups().size() <= 1) {
                        Toast.makeText(this, "至少需要保留一个组别", Toast.LENGTH_SHORT).show(); return;
                    }
                    new AlertDialog.Builder(this).setTitle("删除组别？")
                            .setMessage("组别中的卡片会移动到其他组别，不会被删除。")
                            .setNegativeButton("取消", null).setPositiveButton("删除组别", (dd, ww) -> {
                                store.deleteGroup(id);
                                if (currentGroupId.equals(id)) currentGroupId = "all";
                                renderAll();
                            }).show();
                    break;
            }
        }).show();
    }

    private void showSearchDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setHint("搜索标题或正文");
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(roundBg(Color.rgb(247, 246, 251), dp(12)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("搜索全部记录")
                .setView(input).setNegativeButton("取消", null).setPositiveButton("搜索", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String query = input.getText().toString().trim();
            if (query.isEmpty()) { Toast.makeText(this, "请输入要搜索的内容", Toast.LENGTH_SHORT).show(); return; }
            List<CardRecord> matches = store.search(query);
            dialog.dismiss();
            if (matches.isEmpty()) {
                new AlertDialog.Builder(this).setTitle("没有找到匹配记录").setMessage("试试更短的关键词，或检查其他写法。")
                        .setPositiveButton("知道了", null).show();
                return;
            }
            int limit = Math.min(matches.size(), 50);
            String[] labels = new String[limit];
            final List<CardRecord> subset = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                CardRecord card = matches.get(i);
                subset.add(card);
                String snippet = card.body.replace('\n', ' ').trim();
                if (snippet.length() > 45) snippet = snippet.substring(0, 45) + "…";
                labels[i] = card.displayTitle() + "\n" + groupName(card.groupId) + " · " + snippet;
            }
            new AlertDialog.Builder(this).setTitle("找到 " + matches.size() + " 条记录")
                    .setItems(labels, (res, which) -> {
                        CardRecord card = subset.get(which);
                        currentGroupId = card.groupId;
                        lastCardByGroup.put(currentGroupId, card.id);
                        renderAll();
                        showCardDetail(card.id);
                    }).setNegativeButton("关闭", null).show();
        }));
        dialog.show();
    }

    private void showSettings() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(19), dp(5), dp(19), dp(5));
        Switch soundSwitch = new Switch(this);
        soundSwitch.setText("机械声音总开关"); soundSwitch.setTextSize(14); soundSwitch.setChecked(soundEnabled);
        Switch railSoundSwitch = new Switch(this);
        railSoundSwitch.setText("圆弧转盘机械声"); railSoundSwitch.setTextSize(14); railSoundSwitch.setChecked(railSoundEnabled);
        Switch cardSoundSwitch = new Switch(this);
        cardSoundSwitch.setText("卡片滑动机械声"); cardSoundSwitch.setTextSize(14); cardSoundSwitch.setChecked(cardSoundEnabled);
        Switch arcSwitch = new Switch(this);
        arcSwitch.setText("卡片弧形与惯性动画"); arcSwitch.setTextSize(14); arcSwitch.setChecked(arcEnabled);
        Switch hapticSwitch = new Switch(this);
        hapticSwitch.setText("定位时轻微振动"); hapticSwitch.setTextSize(14); hapticSwitch.setChecked(hapticsEnabled);
        panel.addView(soundSwitch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        panel.addView(railSoundSwitch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        panel.addView(cardSoundSwitch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        panel.addView(arcSwitch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        panel.addView(hapticSwitch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        TextView volumeLabel = text("声音音量　" + Math.round(soundVolume * 100) + "%", 12, MUTED, false);
        SeekBar volumeBar = new SeekBar(this);
        volumeBar.setMax(100); volumeBar.setProgress(Math.round(soundVolume * 100));
        panel.addView(volumeLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));
        panel.addView(volumeBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        TextView export = settingsAction("导出全部记录（JSON 备份）");
        TextView restore = settingsAction("从 JSON 备份恢复");
        TextView about = text("数据只保存在本机；备份由你主动导出。", 11, MUTED, false);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        actionLp.topMargin = dp(6);
        panel.addView(export, actionLp);
        LinearLayout.LayoutParams restoreLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        restoreLp.topMargin = dp(7);
        panel.addView(restore, restoreLp);
        LinearLayout.LayoutParams aboutLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        aboutLp.topMargin = dp(6);
        panel.addView(about, aboutLp);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("交互与数据设置")
                .setView(panel).setPositiveButton("完成", null).create();
        soundSwitch.setOnCheckedChangeListener((button, checked) -> {
            soundEnabled = checked;
            sound.setEnabled(checked);
            settings.edit().putBoolean("sound_enabled", checked).apply();
        });
        railSoundSwitch.setOnCheckedChangeListener((button, checked) -> {
            railSoundEnabled = checked;
            sound.setRailEnabled(checked);
            settings.edit().putBoolean("rail_sound_enabled", checked).apply();
        });
        cardSoundSwitch.setOnCheckedChangeListener((button, checked) -> {
            cardSoundEnabled = checked;
            sound.setCardEnabled(checked);
            settings.edit().putBoolean("card_sound_enabled", checked).apply();
        });
        arcSwitch.setOnCheckedChangeListener((button, checked) -> {
            arcEnabled = checked; deck.setArcEnabled(checked); settings.edit().putBoolean("arc_enabled", checked).apply();
        });
        hapticSwitch.setOnCheckedChangeListener((button, checked) -> {
            hapticsEnabled = checked;
            deck.setHapticsEnabled(checked);
            groupRail.setHapticsEnabled(checked);
            settings.edit().putBoolean("haptics_enabled", checked).apply();
        });
        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                soundVolume = progress / 100f;
                sound.setVolume(soundVolume);
                volumeLabel.setText("声音音量　" + progress + "%");
                settings.edit().putInt("sound_volume", progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        export.setOnClickListener(v -> { dialog.dismiss(); startExport(); });
        restore.setOnClickListener(v -> {
            new AlertDialog.Builder(this).setTitle("恢复备份？")
                    .setMessage("恢复会替换当前全部组别和卡片。建议先导出一份当前备份。")
                    .setNegativeButton("取消", null).setPositiveButton("选择备份文件", (d, w) -> { dialog.dismiss(); startImport(); }).show();
        });
        dialog.show();
    }

    private TextView settingsAction(String label) {
        TextView action = text(label, 13, INK, true);
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setPadding(dp(12), 0, dp(12), 0);
        action.setBackground(roundBg(Color.rgb(243, 241, 250), dp(11)));
        return action;
    }

    private void startExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "文字卡匣备份.json");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("无法写入所选文件");
                out.write(store.exportJson().getBytes(StandardCharsets.UTF_8));
                out.flush();
                Toast.makeText(this, "备份已导出", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "导出失败：" + safeMessage(e), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_IMPORT) {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("无法读取所选文件");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
                store.importJson(new String(out.toByteArray(), StandardCharsets.UTF_8));
                currentGroupId = "all";
                lastCardByGroup.clear();
                renderAll();
                Toast.makeText(this, "备份已恢复", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "恢复失败：" + safeMessage(e), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void shareText(CardRecord card) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        String text = (card.title.trim().isEmpty() ? "" : card.title.trim() + "\n\n") + card.body;
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, "分享文字卡片"));
    }

    private void copyText(String value, String message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("文字卡匣", value == null ? "" : value));
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String defaultGroupForNewCard() {
        if (!"all".equals(currentGroupId) && store.getGroup(currentGroupId) != null) return currentGroupId;
        List<GroupRecord> groups = store.getGroups();
        return groups.isEmpty() ? "" : groups.get(0).id;
    }

    private String getCurrentGroupName() { return "all".equals(currentGroupId) ? "全部记录" : groupName(currentGroupId); }

    private String groupName(String id) {
        GroupRecord g = store.getGroup(id);
        return g == null ? "未分类" : g.name;
    }

    private TextView smallChoice(String label) {
        TextView view = text(label, 12, INK, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(roundBg(Color.rgb(243, 241, 249), dp(11)));
        return view;
    }

    private TextView smallTopButton(String label, String contentDescription) {
        TextView button = text(label, 20, INK, true);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(contentDescription);
        button.setBackground(roundBg(Color.rgb(255, 255, 255), dp(13)));
        return button;
    }

    private TextView actionButton(String label, int background, int foreground) {
        TextView button = text(label, 12, foreground, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundBg(background, dp(13)));
        return button;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setIncludeFontPadding(true);
        return view;
    }

    private GradientDrawable roundBg(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int contrastInk(int background) {
        double luminance = (0.299 * Color.red(background) + 0.587 * Color.green(background) + 0.114 * Color.blue(background)) / 255.0;
        return luminance < 0.52 ? Color.WHITE : Color.rgb(43, 39, 59);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? "未知错误" : message;
    }

    @Override protected void onDestroy() {
        if (groupDrawerPopup != null && groupDrawerPopup.isShowing()) groupDrawerPopup.dismiss();
        if (sound != null) sound.release();
        super.onDestroy();
    }
}
