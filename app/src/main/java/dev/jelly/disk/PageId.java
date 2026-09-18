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
}
