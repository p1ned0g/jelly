package dev.jelly.buffer;

import java.util.HashMap;
import java.util.Map;

import dev.jelly.disk.PageId;

public class BufferPool {
    private int poolSize;
    private int pageSize;
    private Buffer[] frames;
    private Map<PageId,Integer> pageTable;
    private int clockHand = 0;

    public BufferPool(int poolSize, int pageSize) {
        this.poolSize = poolSize;
        this.pageSize = pageSize;
        this.frames = new Buffer[poolSize];
        this.pageTable = new HashMap<PageId,Integer>();

        for (int i = 0; i < frames.length; i++) {
            Buffer frame = new Buffer(this.pageSize);
            this.frames[i] = frame;
        }
    }

    public Buffer[] getFrames() {
        return this.frames;
    }

    public Buffer getFrameByIndex(int idx) {
        return this.frames[idx];
    }

    public int getClockHand() {
        return this.clockHand;
    }

    public Map<PageId,Integer> getpageTable() {
        return this.pageTable;
    }

    public Buffer getIfExists(PageId pageId) {
        Integer idx = this.pageTable.get(pageId);
        if(idx == null) return null;
        return frames[idx];
    }

    public int allocateFrame() {
        for (int i = 0; i < poolSize; i++) {
            // framesの中から使用されていないページのインデックスを返却
            if(this.frames[i].getPageId().isInvalid()){
                return i;
            }
        }
        // 開いていない場合は退避したインデックスを返却
        // TODO:呼び出しもとで退避したインデックスのキャッシュをクリアする必要あり
        return evict();
    }

    public int evict() {
        while (true) {
            // 0から順番に見ていく
            Buffer buf = this.frames[clockHand];

            if(!buf.isPinned()){
                int victim = clockHand;
                // 0 → 1 → 2 ... → 0 → 1 → 2  
                clockHand = (clockHand + 1) % poolSize;
                return victim;
            }
            clockHand = (clockHand + 1) % poolSize;  
        }
    }

    public void registerPage(PageId pageId, int frameIndex) {
        this.pageTable.put(pageId, frameIndex);
    }
}
