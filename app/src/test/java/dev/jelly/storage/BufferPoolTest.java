package dev.jelly.storage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import dev.jelly.storage.PageId;

class BufferPoolTest {

    /**
     * 空のフレームが存在する場合、
     * allocateFrame()が空フレームのindexを返すこと。
     *
     * 注意：
     * allocateFrame()はフレームを確保するだけで、
     * ページの登録や初期化は行わない。
     */
    @Test
    void allocateFrameReturnsEmptyFramesFirst() {
        BufferPool pool = new BufferPool(3, 4096);

        assertEquals(0, pool.allocateFrame());
        assertEquals(0, pool.allocateFrame());
    }

    /**
     * 取得したフレームにPageIdを設定すると、
     * 次のallocateFrame()が別の空フレームを返すこと。
     * 空フレームが優先的に使われることを確認する。
     */
    @Test
    void allocateFrameReturnsDifferentEmptyFramesAfterInitialization() {
        BufferPool pool = new BufferPool(3, 4096);

        int index0 = pool.allocateFrame();
        pool.getFrameByIndex(index0)
            .initialize(new PageId(0), false);

        int index1 = pool.allocateFrame();
        pool.getFrameByIndex(index1)
            .initialize(new PageId(1), false);

        int index2 = pool.allocateFrame();
        pool.getFrameByIndex(index2)
            .initialize(new PageId(2), false);

        assertEquals(0, index0);
        assertEquals(1, index1);
        assertEquals(2, index2);
    }

    /**
     * Clockが置換候補を探すとき、
     * pinned状態のフレームを選ばず、
     * 未pinnedのフレームを返すこと。
     */
    @Test
    void evictSkipsPinnedFrames() {
        BufferPool pool = new BufferPool(3, 4096);

        for (int i = 0; i < 3; i++) {
            pool.getFrameByIndex(i)
                .initialize(new PageId(i), false);
        }

        pool.getFrameByIndex(0).pin();
        pool.getFrameByIndex(1).pin();

        assertEquals(2, pool.evict());
    }

    /**
     * 全フレームがpinned状態の場合、
     * 置換可能なフレームが存在しないため、
     * evict()が-1を返すこと。
     * 無限ループしないことを確認する。
     */
    @Test
    void evictReturnsMinusOneWhenAllFramesPinned() {
        BufferPool pool = new BufferPool(2, 4096);

        for (int i = 0; i < 2; i++) {
            Buffer buffer = pool.getFrameByIndex(i);
            buffer.initialize(new PageId(i), false);
            buffer.pin();
        }

        assertEquals(-1, pool.evict());
    }

    /**
     * usageCountが残っている未pinnedフレームに対して、
     * ClockがusageCountを減らして置換を猶予すること。
     * その後、usageCountが0のフレームを候補として返すこと。
     */
    @Test
    void evictGivesUsageCountSecondChance() {
        BufferPool pool = new BufferPool(2, 4096);

        Buffer first = pool.getFrameByIndex(0);
        first.initialize(new PageId(0), false);
        first.pin();
        first.unpin();

        Buffer second = pool.getFrameByIndex(1);
        second.initialize(new PageId(1), false);

        assertEquals(1, pool.evict());
        assertEquals(0, first.getUsageCount());
    }

    /**
     * registerPage()で登録したPageIdが
     * pageTableから検索でき、対応するフレームindexを
     * 取得できること。
     */
    @Test
    void pageTableStoresAndFindsPage() {
        BufferPool pool = new BufferPool(2, 4096);

        PageId pageId = new PageId(99);
        pool.registerPage(pageId, 1);

        assertTrue(pool.containsPage(pageId));
        assertEquals(1, pool.getFrameIndex(pageId));
    }

    /**
     * replacePage()でページを入れ替えたとき、
     * 古いPageIdの登録が削除され、
     * 新しいPageIdが正しいフレームindexに登録されること。
     */
    @Test
    void replacePageRemovesOldMapping() {
        BufferPool pool = new BufferPool(2, 4096);

        PageId oldPageId = new PageId(1);
        PageId newPageId = new PageId(2);

        pool.registerPage(oldPageId, 0);

        pool.replacePage(0, oldPageId, newPageId);

        assertFalse(pool.containsPage(oldPageId));
        assertTrue(pool.containsPage(newPageId));
        assertEquals(0, pool.getFrameIndex(newPageId));
    }
}