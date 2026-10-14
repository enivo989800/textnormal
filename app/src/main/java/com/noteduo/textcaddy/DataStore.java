package com.noteduo.textcaddy;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Local-first JSON store. No network or account is required. */
public final class DataStore {
    public static final int[] PALETTE = new int[]{
            Color.rgb(255, 224, 190), Color.rgb(204, 228, 255), Color.rgb(207, 239, 218),
            Color.rgb(231, 215, 255), Color.rgb(255, 211, 220), Color.rgb(255, 239, 182),
            Color.rgb(203, 236, 235), Color.rgb(238, 220, 202), Color.rgb(224, 229, 246)
    };

    private static final String PREFS = "text_caddy_store";
    private static final String DATA_KEY = "data_v1";
    private final SharedPreferences prefs;
    private final List<GroupRecord> groups = new ArrayList<>();
    private final List<CardRecord> cards = new ArrayList<>();

    public DataStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(DATA_KEY, null);
        boolean loaded = false;
        if (stored != null) {
            try {
                readJson(new JSONObject(stored));
                loaded = !groups.isEmpty();
            } catch (Exception ignored) {
                groups.clear();
                cards.clear();
            }
        }
        if (!loaded) {
            seedGroups();
            save();
        }
    }

    private void seedGroups() {
        groups.clear();
        groups.add(new GroupRecord("ideas", "灵感", PALETTE[0], 0));
        groups.add(new GroupRecord("work", "工作", PALETTE[1], 1));
        groups.add(new GroupRecord("important", "重要", PALETTE[3], 2));
        groups.add(new GroupRecord("todo", "待办", PALETTE[2], 3));
        groups.add(new GroupRecord("clips", "收藏", PALETTE[4], 4));
    }

    public synchronized List<GroupRecord> getGroups() {
        List<GroupRecord> result = new ArrayList<>(groups);
        Collections.sort(result, new Comparator<GroupRecord>() {
            @Override public int compare(GroupRecord a, GroupRecord b) {
                return Integer.compare(a.order, b.order);
            }
        });
        return result;
    }

    public synchronized GroupRecord getGroup(String id) {
        for (GroupRecord group : groups) if (group.id.equals(id)) return group;
        return null;
    }

    public synchronized GroupRecord addGroup(String name, int color) {
        String id = UUID.randomUUID().toString();
        GroupRecord group = new GroupRecord(id, name.trim(), color, nextGroupOrder());
        groups.add(group);
        save();
        return group;
    }

    public synchronized void updateGroup(GroupRecord updated) {
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).id.equals(updated.id)) {
                groups.set(i, updated);
                save();
                return;
            }
        }
    }

    public synchronized boolean deleteGroup(String id) {
        if (groups.size() <= 1) return false;
        GroupRecord target = getGroup(id);
        if (target == null) return false;
        List<GroupRecord> ordered = getGroups();
        GroupRecord replacement = null;
        for (GroupRecord group : ordered) {
            if (!group.id.equals(id)) { replacement = group; break; }
        }
        if (replacement == null) return false;
        for (CardRecord card : cards) {
            if (card.groupId.equals(id)) {
                card.groupId = replacement.id;
                card.updatedAt = System.currentTimeMillis();
            }
        }
        groups.remove(target);
        normalizeGroupOrder();
        save();
        return true;
    }

    public synchronized void moveGroup(String id, int delta) {
        List<GroupRecord> ordered = getGroups();
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) if (ordered.get(i).id.equals(id)) index = i;
        int other = index + delta;
        if (index < 0 || other < 0 || other >= ordered.size()) return;
        GroupRecord a = ordered.get(index);
        GroupRecord b = ordered.get(other);
        int temp = a.order;
        a.order = b.order;
        b.order = temp;
        save();
    }

    public synchronized List<CardRecord> getCards(String groupId) {
        List<CardRecord> result = new ArrayList<>();
        for (CardRecord card : cards) {
            if ("all".equals(groupId) || card.groupId.equals(groupId)) result.add(card);
        }
        Collections.sort(result, new Comparator<CardRecord>() {
            @Override public int compare(CardRecord a, CardRecord b) {
                if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
                int orderCompare = Integer.compare(a.order, b.order);
                return orderCompare != 0 ? orderCompare : Long.compare(a.createdAt, b.createdAt);
            }
        });
        return result;
    }

    public synchronized List<CardRecord> search(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        List<CardRecord> result = new ArrayList<>();
        if (needle.isEmpty()) return result;
        for (CardRecord card : cards) {
            if (card.body.toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || card.title.toLowerCase(java.util.Locale.ROOT).contains(needle)) result.add(card);
        }
        Collections.sort(result, new Comparator<CardRecord>() {
            @Override public int compare(CardRecord a, CardRecord b) {
                return Long.compare(b.updatedAt, a.updatedAt);
            }
        });
        return result;
    }

    public synchronized CardRecord getCard(String id) {
        for (CardRecord card : cards) if (card.id.equals(id)) return card;
        return null;
    }

    public synchronized CardRecord addCard(String groupId, String title, String body, int color) {
        long now = System.currentTimeMillis();
        CardRecord card = new CardRecord(UUID.randomUUID().toString(), groupId, title, body,
                color, false, false, now, now, nextCardOrder(groupId));
        cards.add(card);
        save();
        return card;
    }

    public synchronized void updateCard(CardRecord updated) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).id.equals(updated.id)) {
                updated.updatedAt = System.currentTimeMillis();
                cards.set(i, updated);
                save();
                return;
            }
        }
    }

    public synchronized void deleteCard(String id) {
        for (int i = cards.size() - 1; i >= 0; i--) {
            if (cards.get(i).id.equals(id)) cards.remove(i);
        }
        save();
    }

    public synchronized void toggleFavorite(String id) {
        CardRecord card = getCard(id);
        if (card != null) { card.favorite = !card.favorite; card.updatedAt = System.currentTimeMillis(); save(); }
    }

    public synchronized void togglePinned(String id) {
        CardRecord card = getCard(id);
        if (card != null) { card.pinned = !card.pinned; card.updatedAt = System.currentTimeMillis(); save(); }
    }

    public synchronized void moveCard(String id, String groupId) {
        CardRecord card = getCard(id);
        if (card != null && getGroup(groupId) != null) {
            card.groupId = groupId;
            card.order = nextCardOrder(groupId);
            card.updatedAt = System.currentTimeMillis();
            save();
        }
    }

    public synchronized String exportJson() {
        return toJson().toString(2);
    }

    public synchronized void importJson(String raw) throws JSONException {
        JSONObject object = new JSONObject(raw);
        JSONArray groupArray = object.optJSONArray("groups");
        JSONArray cardArray = object.optJSONArray("cards");
        if (groupArray == null || cardArray == null) throw new JSONException("备份文件缺少 groups 或 cards 数据");
        List<GroupRecord> oldGroups = new ArrayList<>(groups);
        List<CardRecord> oldCards = new ArrayList<>(cards);
        groups.clear(); cards.clear();
        try {
            readJson(object);
            if (groups.isEmpty()) throw new JSONException("备份中没有可用组别");
            for (CardRecord card : cards) {
                if (getGroup(card.groupId) == null) card.groupId = groups.get(0).id;
            }
            save();
        } catch (JSONException e) {
            groups.clear(); groups.addAll(oldGroups);
            cards.clear(); cards.addAll(oldCards);
            throw e;
        }
    }

    private void readJson(JSONObject object) throws JSONException {
        JSONArray groupArray = object.optJSONArray("groups");
        JSONArray cardArray = object.optJSONArray("cards");
        if (groupArray == null || cardArray == null) throw new JSONException("invalid data schema");
        groups.clear(); cards.clear();
        for (int i = 0; i < groupArray.length(); i++) {
            JSONObject g = groupArray.getJSONObject(i);
            String id = g.optString("id", UUID.randomUUID().toString());
            String name = g.optString("name", "未命名组别").trim();
            if (name.isEmpty()) name = "未命名组别";
            groups.add(new GroupRecord(id, name, g.optInt("color", PALETTE[i % PALETTE.length]), g.optInt("order", i)));
        }
        for (int i = 0; i < cardArray.length(); i++) {
            JSONObject c = cardArray.getJSONObject(i);
            long now = System.currentTimeMillis();
            cards.add(new CardRecord(
                    c.optString("id", UUID.randomUUID().toString()),
                    c.optString("groupId", groups.isEmpty() ? "" : groups.get(0).id),
                    c.optString("title", ""), c.optString("body", ""), c.optInt("color", PALETTE[i % PALETTE.length]),
                    c.optBoolean("favorite", false), c.optBoolean("pinned", false),
                    c.optLong("createdAt", now), c.optLong("updatedAt", now), c.optInt("order", i)));
        }
    }

    private JSONObject toJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("format", "text-caddy-backup");
            root.put("schemaVersion", 1);
            root.put("exportedAt", System.currentTimeMillis());
            JSONArray groupArray = new JSONArray();
            for (GroupRecord group : getGroups()) {
                JSONObject g = new JSONObject();
                g.put("id", group.id); g.put("name", group.name); g.put("color", group.color); g.put("order", group.order);
                groupArray.put(g);
            }
            JSONArray cardArray = new JSONArray();
            for (CardRecord card : cards) {
                JSONObject c = new JSONObject();
                c.put("id", card.id); c.put("groupId", card.groupId); c.put("title", card.title); c.put("body", card.body);
                c.put("color", card.color); c.put("favorite", card.favorite); c.put("pinned", card.pinned);
                c.put("createdAt", card.createdAt); c.put("updatedAt", card.updatedAt); c.put("order", card.order);
                cardArray.put(c);
            }
            root.put("groups", groupArray); root.put("cards", cardArray);
        } catch (JSONException ignored) { }
        return root;
    }

    private int nextGroupOrder() {
        int max = -1; for (GroupRecord g : groups) max = Math.max(max, g.order); return max + 1;
    }

    private int nextCardOrder(String groupId) {
        int max = -1; for (CardRecord c : cards) if (c.groupId.equals(groupId)) max = Math.max(max, c.order); return max + 1;
    }

    private void normalizeGroupOrder() {
        List<GroupRecord> ordered = getGroups();
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).order = i;
    }

    private void save() {
        prefs.edit().putString(DATA_KEY, toJson().toString()).apply();
    }
}
