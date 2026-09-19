package dev.jelly.buffer;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import dev.jelly.disk.PageId;
import dev.jelly.disk.DiskManager;

public class BufferPoolTest {

    @Test
    void testAllocateFrameReturnsFreeFrame() {
        BufferPool pool = new BufferPool(3, 4096);

        int idx = pool.allocateFrame();
        assertEquals(0, idx);
        // pageIdにallocateしたインデックスを設定
        pool.getFrameByIndex(idx).setPageId(new PageId(0));
        
        int idx2 = pool.allocateFrame();
        assertEquals(1, idx2);
        // pageIdにallocateしたインデックスを設定
        pool.getFrameByIndex(idx2).setPageId(new PageId(1));
        
        int idx3 = pool.allocateFrame();
        assertEquals(2, idx3);
        // pageIdにallocateしたインデックスを設定
        pool.getFrameByIndex(idx3).setPageId(new PageId(2));
    }

    @Test
    void testEvictChoosesUnpinnedFrame() {
        BufferPool pool = new BufferPool(3, 4096);

        for (int i = 0; i < 3; i++) {
            pool.getFrameByIndex(i).setPageId(new PageId(i));
        }

        // frame0 と frame1 は pin する
        pool.getFrameByIndex(0).pin();
        pool.getFrameByIndex(1).pin();

        // frame2 は unpinned → evict の victim になるはず
        int victim = pool.evict();
        assertEquals(2, victim);
    }

    @Test
    void testEvictClockHandMoves() {
        BufferPool pool = new BufferPool(3, 4096);

        // 全部埋める
        for (int i = 0; i < 3; i++) {
            pool.getFrameByIndex(i).setPageId(new PageId(i));
        }

        // frame0 は pinned → スキップ
        pool.getFrameByIndex(0).pin();

        // frame1 は pinned → スキップ
        pool.getFrameByIndex(1).pin();

        // frame2 は unpinned → victim
        int victim = pool.evict();
        assertEquals(2, victim);

        // clockHand は 3 → 0 に戻るはず
        assertEquals(0, pool.getClockHand());
    }

    @Test
    void testRegisterPageStoresMapping() {
        BufferPool pool = new BufferPool(3, 4096);

        PageId pid = new PageId(99);
        pool.registerPage(pid, 1);

        Buffer buf = pool.getIfExists(pid);
        assertNotNull(buf);
        assertEquals(1, pool.getpageTable().get(pid));
    }
}
