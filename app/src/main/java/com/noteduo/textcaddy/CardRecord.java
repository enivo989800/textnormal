package com.noteduo.textcaddy;

public final class CardRecord {
    public String id;
    public String groupId;
    public String title;
    public String body;
    public int color;
    public boolean favorite;
    public boolean pinned;
    public long createdAt;
    public long updatedAt;
    public int order;

    public CardRecord(String id, String groupId, String title, String body, int color,
                      boolean favorite, boolean pinned, long createdAt, long updatedAt, int order) {
        this.id = id;
        this.groupId = groupId;
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.color = color;
        this.favorite = favorite;
        this.pinned = pinned;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.order = order;
    }

    public String displayTitle() {
        if (!title.trim().isEmpty()) return title.trim();
        String value = body == null ? "" : body.trim().replace('\n', ' ');
        if (value.isEmpty()) return "未命名记录";
        return value.length() > 22 ? value.substring(0, 22) + "…" : value;
    }
}
