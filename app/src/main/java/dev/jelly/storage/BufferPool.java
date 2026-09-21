package dev.jelly.storage;

import java.util.HashMap;
import java.util.Map;

import dev.jelly.storage.PageId;

public class BufferPool {
    private final int poolSize;
    private final int pageSize;
    private final Buffer[] frames;
    private final Map<PageId, Integer> pageTable;

    private int clockHand = 0;

    public BufferPool(int poolSize, int pageSize) {
        if (poolSize <= 0) {
            throw new IllegalArgumentException(
                "poolSize must be positive"
            );
        }

        if (pageSize <= 0) {
            throw new IllegalArgumentException(
                "pageSize must be positive"
            );
        }

        this.poolSize = poolSize;
        this.pageSize = pageSize;
        this.frames = new Buffer[poolSize];
        this.pageTable = new HashMap<>();

        for (int i = 0; i < poolSize; i++) {
            frames[i] = new Buffer(pageSize);
        }
    }

    public Buffer getIfExists(PageId pageId) {
        Integer index = pageTable.get(pageId);

        if (index == null) {
            return null;
        }
        System.out.println("Hit in cache: PageId: " + pageId);
        return frames[index];
    }

    /*
     * 空フレームを優先し、なければClockで選ぶ。
     *
     * 戻り値:
     *   0以上 : 使用するフレームのindex
     *   -1    : 全フレームがpinned
     *
     * このメソッドはpageTableやpageIdを変更しない。
     */
    int allocateFrame() {
        // 空フレームを優先
        for (int i = 0; i < poolSize; i++) {
            if (frames[i].getPageId().isInvalid()) {
                return i;
            }
        }

        // 空きがなければClock置換
        return evict();
    }

    /*
     * Clock方式の置換候補選択。
     *
     * pinnedなフレームは選ばない。
     * usageCountが残っていれば減らして猶予を与える。
     * usageCountが0なら置換候補とする。
     */
    int evict() {
        boolean hasUnpinned = false;

        for (Buffer frame : frames) {
            if (!frame.isPinned()) {
                hasUnpinned = true;
                break;
            }
        }

        if (!hasUnpinned) {
            return -1;
        }

        while (true) {
            Buffer frame = frames[clockHand];
            int victimIndex = clockHand;

            clockHand = (clockHand + 1) % poolSize;

            if (frame.isPinned()) {
                continue;
            }

            if (frame.getUsageCount() > 0) {
                frame.decrementUsageCount();
                continue;
            }

            return victimIndex;
        }
    }

    /*
     * BufferPoolManagerが、フレーム切替成功後に呼ぶ。
     */
    void replacePage(int frameIndex, PageId oldPageId, PageId newPageId) {

        if (!oldPageId.isInvalid()) {
            Integer registeredIndex = pageTable.get(oldPageId);

            if (registeredIndex != null && registeredIndex == frameIndex) {
                pageTable.remove(oldPageId);
            }
        }

        pageTable.put(newPageId, frameIndex);
    }

    /*
     * 新しいページを空フレームに登録する。
     */
    void registerPage(PageId pageId, int frameIndex) {
        pageTable.put(pageId, frameIndex);
    }

    /*
     * テスト用。
     */
    boolean containsPage(PageId pageId) {
        return pageTable.containsKey(pageId);
    }

    int getFrameIndex(PageId pageId) {
        Integer index = pageTable.get(pageId);
        return index == null ? -1 : index;
    }

    Buffer getFrameByIndex(int index) {
        return frames[index];
    }

    int getClockHand() {
        return clockHand;
    }

    int getPoolSize() {
        return poolSize;
    }
}
