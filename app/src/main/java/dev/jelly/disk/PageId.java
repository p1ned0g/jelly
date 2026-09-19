package dev.jelly.disk;

public class PageId {
    private int pageId;

    public PageId(int pageId) {
        this.pageId = pageId;
    }

    public int getPageId() {
        return this.pageId;
    }

    public long toOffset(int pageSize) {
        return (long) this.pageId * pageSize;
    }

    // Buffer初期化時は-1でpageIDが採番される
    public boolean isInvalid() {
        return this.pageId == -1;
    }
}
