package dev.jelly.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jelly.storage.DiskManager;
import dev.jelly.storage.PageId;

class BufferPoolManagerTest {

    private Path tempFile;
    private DiskManager disk;
    private BufferPoolManager bpm;

    private static final int PAGE_SIZE = 4096;

    @BeforeEach
    void setup() throws IOException {
        tempFile = Files.createTempFile(
            "buffer-pool-test",
            ".db"
        );

        disk = new DiskManager(tempFile.toString());

        bpm = new BufferPoolManager(
            disk,
            1,
            PAGE_SIZE
        );
    }

    @AfterEach
    void cleanup() throws IOException {
        if (disk != null) {
            disk.close();
        }

        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * newPage()で新規ページを作成すると、
     * 呼び出し元が利用できるようにpinされた状態で
     * Bufferが返されること。
     */
    @Test
    void newPageReturnsPinnedPage() throws IOException {
        Buffer page = bpm.newPage();

        assertTrue(page.isPinned());
        assertEquals(1, page.getPinCount());

        bpm.unpinPage(page.getPageId());
    }

    /**
     * 一度作成したページをunpinしてからfetchすると、
     * キャッシュに残っている同じBufferが返され、
     * 書き込んだ内容も保持されていること。
     */
    @Test
    void fetchPageReturnsCachedPage() throws IOException {
        Buffer page = bpm.newPage();
        PageId pageId = page.getPageId();

        page.writeByte(0, (byte) 42);
        bpm.unpinPage(pageId);

        Buffer fetched = bpm.fetchPage(pageId);

        assertSame(page, fetched);
        assertEquals(42, fetched.readByte(0));

        bpm.unpinPage(pageId);
    }

    /**
     * 同じページを複数回fetchすると、
     * 同じBufferが返され、fetchの回数に応じて
     * pinCountが増加すること。
     */
    @Test
    void fetchPageIncrementsPinCount() throws IOException {
        Buffer page = bpm.newPage();
        PageId pageId = page.getPageId();

        bpm.unpinPage(pageId);

        Buffer fetched1 = bpm.fetchPage(pageId);
        Buffer fetched2 = bpm.fetchPage(pageId);

        assertSame(fetched1, fetched2);
        assertEquals(2, fetched1.getPinCount());

        bpm.unpinPage(pageId);
        bpm.unpinPage(pageId);
    }

    /**
     * poolSize=1でページがpinnedされている状態では、
     * 新しいページを作成できず例外になること。
     * 既存ページが誤って置換されないことを確認する。
     */
    @Test
    void cannotReplacePinnedPage() throws IOException {
        Buffer page1 = bpm.newPage();

        assertThrows(
            IllegalStateException.class,
            () -> bpm.newPage()
        );

        assertTrue(page1.isPinned());

        bpm.unpinPage(page1.getPageId());
    }

    /**
     * dirtyなページを置換したときにディスクへ書き戻され、
     * 後から再fetchしても書き込んだデータが復元されること。
     */
    @Test
    void dirtyPageIsFlushedWhenEvicted() throws IOException {
        Buffer page1 = bpm.newPage();
        PageId pageId1 = page1.getPageId();

        page1.writeByte(0, (byte) 11);
        bpm.unpinPage(pageId1);

        Buffer page2 = bpm.newPage();
        PageId pageId2 = page2.getPageId();

        assertNotEquals(pageId1, pageId2);

        bpm.unpinPage(pageId2);

        Buffer restored = bpm.fetchPage(pageId1);

        assertEquals(11, restored.readByte(0));

        bpm.unpinPage(pageId1);
    }

    /**
     * ページを置換したとき、pageTableから古いPageIdが
     * 削除され、新しいPageIdが登録されていること。
     */
    @Test
    void pageTableDoesNotKeepEvictedPage() throws IOException {
        Buffer page1 = bpm.newPage();
        PageId pageId1 = page1.getPageId();

        bpm.unpinPage(pageId1);

        Buffer page2 = bpm.newPage();
        PageId pageId2 = page2.getPageId();

        BufferPool pool = bpm.getBufferPool();

        assertFalse(pool.containsPage(pageId1));
        assertTrue(pool.containsPage(pageId2));

        bpm.unpinPage(pageId2);
    }

    /**
     * flushPage()でdirtyなページを書き戻すと、
     * dirtyフラグがfalseになり、
     * 後から再fetchしてもデータが保持されていること。
     */
    @Test
    void flushPageWritesDirtyPage() throws IOException {
        Buffer page = bpm.newPage();
        PageId pageId = page.getPageId();

        page.writeByte(0, (byte) 77);

        bpm.flushPage(pageId);

        assertFalse(page.isDirty());

        bpm.unpinPage(pageId);

        Buffer another = bpm.newPage();
        bpm.unpinPage(another.getPageId());

        Buffer restored = bpm.fetchPage(pageId);

        assertEquals(77, restored.readByte(0));

        bpm.unpinPage(pageId);
    }

    /**
     * flushAll()でBufferPool内のdirtyページが
     * すべてディスクへ書き戻され、
     * dirtyフラグがfalseになること。
     */
    @Test
    void flushAllWritesDirtyPages() throws IOException {
        Buffer page = bpm.newPage();
        PageId pageId = page.getPageId();

        page.writeByte(0, (byte) 88);

        bpm.flushAll();

        assertFalse(page.isDirty());

        bpm.unpinPage(pageId);
    }

    /**
     * unpinPage()を同じページに対して2回呼び出すと、
     * 2回目にIllegalStateExceptionが発生すること。
     */
    @Test
    void unpinPageTwiceThrows() throws IOException {
        Buffer page = bpm.newPage();
        PageId pageId = page.getPageId();

        bpm.unpinPage(pageId);

        assertThrows(
            IllegalStateException.class,
            () -> bpm.unpinPage(pageId)
        );
    }

    /**
     * BufferPoolに登録されていないPageIdを
     * unpinPage()に渡すと、IllegalArgumentExceptionが
     * 発生すること。
     */
    @Test
    void unpinPageForUnknownPageThrows() {
        assertThrows(
            IllegalArgumentException.class,
            () -> bpm.unpinPage(new PageId(999))
        );
    }
}