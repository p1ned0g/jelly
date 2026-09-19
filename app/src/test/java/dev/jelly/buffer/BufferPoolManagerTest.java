package dev.jelly.buffer;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jelly.disk.DiskManager;
import dev.jelly.disk.PageId;

public class BufferPoolManagerTest {

    private DiskManager disk;
    private BufferPoolManager bpm;

    @BeforeEach
    void setup() throws IOException {
        Path tempFile = Files.createTempFile("diskmanager-test", ".db");
        disk = new DiskManager(tempFile.toString());
        bpm = new BufferPoolManager(disk);
    }

    @Test
    void testFetchPageCacheHitAndMiss() throws IOException {
        Buffer buf1 = bpm.newPage();
        PageId pid = buf1.getPageId();

        buf1.writeByte(0, (byte) 100);
        buf1.unpin();

        Buffer buf2 = bpm.fetchPage(pid);
        assertEquals(100, buf2.readByte(0));
        buf2.unpin();
    }

    @Test
    void testEvictAndFlush() throws IOException {
        Buffer p1 = bpm.newPage();
        PageId pid1 = p1.getPageId();
        p1.writeByte(0, (byte) 1);
        p1.unpin();

        Buffer p2 = bpm.newPage();
        PageId pid2 = p2.getPageId();
        p2.writeByte(0, (byte) 2);
        p2.unpin();

        bpm.flushAll();

        Buffer r1 = bpm.fetchPage(pid1);
        Buffer r2 = bpm.fetchPage(pid2);

        assertEquals(1, r1.readByte(0));
        assertEquals(2, r2.readByte(0));

        r1.unpin();
        r2.unpin();
    }

    @Test
    void testNewPageCreatesUniquePageIds() throws IOException {
        Buffer p1 = bpm.newPage();
        Buffer p2 = bpm.newPage();

        assertNotEquals(p1.getPageId().getPageId(), p2.getPageId().getPageId());
    }

    @Test
    void testFlushPageWritesDirtyPage() throws IOException {
        Buffer buf = bpm.newPage();
        PageId pid = buf.getPageId();

        buf.writeByte(0, (byte) 77);
        bpm.flushPage(pid);
        buf.unpin();

        Buffer buf2 = bpm.fetchPage(pid);
        assertEquals(77, buf2.readByte(0));
        buf2.unpin();
    }
}
