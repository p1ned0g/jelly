package dev.jelly.storage;

import java.nio.ByteBuffer;

import dev.jelly.storage.*;

public class Buffer {
    private static final int MAX_USAGE_COUNT = 5;

    private PageId pageId;
    private final ByteBuffer data;
    private boolean isDirty;
    private int pinCount;
    private int usageCount;

    public Buffer(int pageSize) {
        this.pageId = new PageId(-1);
        this.data = ByteBuffer.allocate(pageSize);
        this.isDirty = false;
        this.pinCount = 0;
        this.usageCount = 0;
    }

    public PageId getPageId() {
        return pageId;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public boolean isPinned() {
        return pinCount > 0;
    }

    public int getPinCount() {
        return pinCount;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public int getPageSize() {
        return data.capacity();
    }

    public void pin() {
        pinCount++;
        recordAccess();
    }

    public void unpin() {
        if (pinCount <= 0) {
            throw new IllegalStateException(
                "Cannot unpin: pinCount is already zero"
            );
        }

        pinCount--;
    }

    private void recordAccess() {
        if (usageCount < MAX_USAGE_COUNT) {
            usageCount++;
        }
    }

    void decrementUsageCount() {
        if (usageCount > 0) {
            usageCount--;
        }
    }

    public void markDirty() {
        isDirty = true;
    }

    public void clearDirty() {
        isDirty = false;
    }

    public byte readByte(int offset) {
        return data.get(offset);
    }

    public void writeByte(int offset, byte value) {
        data.put(offset, value);
        markDirty();
    }

    /*
     * 読み取り用。
     * 呼び出し側がpositionを変更しても、
     * Buffer自身のpositionには影響させない。
     */
    public ByteBuffer getData() {
        return data.asReadOnlyBuffer();
    }

    /*
     * BufferPoolManager / DiskManagerとの
     * データ受け渡しに使用する。
     */
    ByteBuffer internalData() {
        return data;
    }

    /*
     * 一時バッファの内容を、このBufferにコピーする。
     */
    void copyFrom(ByteBuffer source) {
        ByteBuffer src = source.duplicate();
        src.clear();

        if (src.remaining() != data.capacity()) {
            throw new IllegalArgumentException(
                "Source buffer size does not match page size"
            );
        }

        data.clear();
        data.put(src);
        data.clear();
    }

    /*
     * 新規ページ用。全バイトを0にする。
     */
    void clearData() {
        data.clear();

        while (data.hasRemaining()) {
            data.put((byte) 0);
        }

        data.clear();
    }

    /*
     * BufferPoolManagerだけが、
     * フレームを新しいページに切り替える。
     */
    void initialize(PageId newPageId, boolean dirty) {
        if (isPinned()) {
            throw new IllegalStateException(
                "Cannot initialize a pinned buffer"
            );
        }

        this.pageId = newPageId;
        this.isDirty = dirty;
        this.pinCount = 0;
        this.usageCount = 0;
    }
}