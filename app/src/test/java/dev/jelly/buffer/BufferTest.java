package dev.jelly.buffer;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import dev.jelly.disk.PageId;

public class BufferTest {

    @Test
    void testPinUnpin() {
        Buffer buf = new Buffer(4096);

        assertFalse(buf.isPinned());
        buf.pin();
        assertTrue(buf.isPinned());

        buf.unpin();
        assertFalse(buf.isPinned());
    }

    @Test
    void testResetPin() {
        Buffer buf = new Buffer(4096);

        buf.pin();
        buf.pin();
        assertTrue(buf.isPinned());

        buf.resetpin();
        assertFalse(buf.isPinned());
    }

    @Test
    void testDirtyFlag() {
        Buffer buf = new Buffer(4096);

        assertFalse(buf.isDirty());
        buf.writeByte(0, (byte) 1);
        assertTrue(buf.isDirty());

        buf.clearDirty();
        assertFalse(buf.isDirty());
    }

    @Test
    void testReadWriteByte() {
        Buffer buf = new Buffer(4096);

        buf.writeByte(10, (byte) 42);
        byte val = buf.readByte(10);

        assertEquals(42, val);
    }

    @Test
    void testPageIdSetAndGet() {
        Buffer buf = new Buffer(4096);
        PageId pid = new PageId(5);

        buf.setPageId(pid);
        assertEquals(5, buf.getPageId().getPageId());
    }
}
