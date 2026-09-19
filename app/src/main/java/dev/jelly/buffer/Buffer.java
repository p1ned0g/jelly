package dev.jelly.buffer;

import java.nio.ByteBuffer;

import dev.jelly.disk.PageId;

public class Buffer {
    private PageId pageId;
    private final ByteBuffer data;
    private boolean isDirty;
    private int pinCount;

    // For BufferPool
    public Buffer(int pageSize) {
        this.pageId = new PageId(-1);
        this.data = ByteBuffer.allocate(pageSize);
        this.isDirty = false; 
        this.pinCount = 0;
    }
    // For DiskManager
    public Buffer(PageId pageId, int pageSize) {
        this.pageId = pageId;
        this.data = ByteBuffer.allocate(pageSize);
        this.isDirty = false;
        this.pinCount = 0;
    }

    public PageId getPageId() {
        return this.pageId;
    }

    public void setPageId(PageId pageId) {
        this.pageId = pageId;
    }

    public ByteBuffer getData() {
        return this.data;
    }

    public boolean isDirty() {
        return this.isDirty;
    }

    public void markDirty() {
        this.isDirty = true;
    }

    public void clearDirty() {
        this.isDirty = false;
    }

    public byte readByte(int offset) {
        return this.data.get(offset);
    }

    public void writeByte(int offset, byte val){
        this.data.put(offset, val);
        this.markDirty();
    }

    public void pin() {
        pinCount++;
    }

    public void unpin() {
        if (pinCount > 0) pinCount--;
    }

    public void resetpin() {
        this.pinCount = 0;
    }

    public boolean isPinned() {
        return pinCount > 0;
    }
}
