package dev.jelly.disk;

import org.junit.jupiter.api.Test;

import dev.jelly.disk.DiskManager;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

class DiskManagerTest {

    private static final int PAGE_SIZE = 4096;

    @Test
    void testAllocateAndWriteAndRead() throws IOException {
        Path tempFile = Files.createTempFile("diskmanager-test", ".db");

        DiskManager dm = new DiskManager(tempFile.toString());

        int pageId = dm.allocate();
        assertEquals(0, pageId);

        ByteBuffer writeBuf = ByteBuffer.allocate(PAGE_SIZE);
        writeBuf.put("Hello, World".getBytes());
        writeBuf.flip();

        dm.write(pageId, writeBuf);

        ByteBuffer readBuf = ByteBuffer.allocate(PAGE_SIZE);
        dm.read(pageId, readBuf);
        readBuf.flip();

        byte[] data = new byte[readBuf.remaining()];
        readBuf.get(data);

        assertEquals("Hello, World", new String(data).trim());

        dm.close();
    }

    @Test
    void testAllocateIncrementsPageId() throws IOException {
        Path tempFile = Files.createTempFile("diskmanager-test2", ".db");

        DiskManager dm = new DiskManager(tempFile.toString());

        int p0 = dm.allocate();
        int p1 = dm.allocate();
        int p2 = dm.allocate();

        assertEquals(0, p0);
        assertEquals(1, p1);
        assertEquals(2, p2);

        dm.close();
    }
}
