package dev.jelly.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import dev.jelly.storage.PageId;

class BufferTest {

    /**
     * pin()でpinCountが増加し、unpin()で減少すること。
     * pinCountが1以上の場合はpinned状態となり、
     * 0になるとpinned状態が解除されること。
     */
    @Test
    void pinAndUnpin() {
        Buffer buffer = new Buffer(4096);

        assertFalse(buffer.isPinned());
        assertEquals(0, buffer.getPinCount());

        buffer.pin();
        assertTrue(buffer.isPinned());
        assertEquals(1, buffer.getPinCount());

        buffer.pin();
        assertEquals(2, buffer.getPinCount());

        buffer.unpin();
        assertEquals(1, buffer.getPinCount());

        buffer.unpin();
        assertFalse(buffer.isPinned());
        assertEquals(0, buffer.getPinCount());
    }

    /**
     * 一度もpin()していないBufferに対してunpin()すると、
     * IllegalStateExceptionが発生すること。
     */
    @Test
    void unpinWithoutPinThrows() {
        Buffer buffer = new Buffer(4096);

        assertThrows(
            IllegalStateException.class,
            buffer::unpin
        );
    }

    /**
     * pin()後にunpin()を2回呼び出すと、
     * 2回目のunpin()でIllegalStateExceptionが発生すること。
     * pinCountが0未満にならないことを確認する。
     */
    @Test
    void doubleUnpinThrows() {
        Buffer buffer = new Buffer(4096);

        buffer.pin();
        buffer.unpin();

        assertThrows(
            IllegalStateException.class,
            buffer::unpin
        );
    }

    /**
     * pin()によるアクセスのたびにusageCountが増加すること。
     * usageCountが置換アルゴリズム用のアクセス履歴として
     * 管理されていることを確認する。
     */
    @Test
    void pinIncrementsUsageCount() {
        Buffer buffer = new Buffer(4096);

        assertEquals(0, buffer.getUsageCount());

        buffer.pin();
        assertEquals(1, buffer.getUsageCount());

        buffer.pin();
        assertEquals(2, buffer.getUsageCount());
    }

    /**
     * unpin()ではusageCountが減少しないこと。
     * pinCountとusageCountが別々に管理されていることを確認する。
     */
    @Test
    void usageCountDoesNotDecreaseWhenUnpinning() {
        Buffer buffer = new Buffer(4096);

        buffer.pin();
        buffer.unpin();

        assertEquals(1, buffer.getUsageCount());
    }

    /**
     * decrementUsageCount()によってusageCountが1減少すること。
     * Clockがアクセス履歴を減らす処理を確認する。
     */
    @Test
    void usageCountCanBeDecremented() {
        Buffer buffer = new Buffer(4096);

        buffer.pin();
        buffer.unpin();

        buffer.decrementUsageCount();

        assertEquals(0, buffer.getUsageCount());
    }

    /**
     * usageCountが0の状態で減算しても、
     * 負の値にならないこと。
     */
    @Test
    void usageCountDoesNotBecomeNegative() {
        Buffer buffer = new Buffer(4096);

        buffer.decrementUsageCount();

        assertEquals(0, buffer.getUsageCount());
    }

    /**
     * writeByte()で指定位置のデータが更新され、
     * Bufferのdirtyフラグがtrueになること。
     */
    @Test
    void writeByteMarksDirty() {
        Buffer buffer = new Buffer(4096);

        assertFalse(buffer.isDirty());

        buffer.writeByte(0, (byte) 42);

        assertTrue(buffer.isDirty());
        assertEquals(42, buffer.readByte(0));
    }

    /**
     * clearDirty()によってdirtyフラグがfalseに戻ること。
     * ディスクへの書き戻し完了後の状態を想定する。
     */
    @Test
    void clearDirtyResetsDirtyFlag() {
        Buffer buffer = new Buffer(4096);

        buffer.writeByte(0, (byte) 42);
        buffer.clearDirty();

        assertFalse(buffer.isDirty());
    }

    /**
     * initialize()で新しいPageIdを設定すると、
     * dirty、pinCount、usageCountが初期状態になること。
     */
    @Test
    void initializeResetsPageState() {
        Buffer buffer = new Buffer(4096);

        buffer.pin();
        buffer.unpin();
        buffer.writeByte(0, (byte) 42);

        buffer.initialize(new PageId(10), false);

        assertEquals(new PageId(10), buffer.getPageId());
        assertFalse(buffer.isDirty());
        assertEquals(0, buffer.getPinCount());
        assertEquals(0, buffer.getUsageCount());
    }

    /**
     * pinned状態のBufferをinitialize()で別ページに
     * 切り替えようとすると、IllegalStateExceptionが発生すること。
     * 利用中のページが誤って再利用されないことを確認する。
     */
    @Test
    void cannotInitializePinnedBuffer() {
        Buffer buffer = new Buffer(4096);
        buffer.pin();

        assertThrows(
            IllegalStateException.class,
            () -> buffer.initialize(new PageId(10), false)
        );
    }

    /**
     * clearData()によって、以前書き込んだデータが
     * すべて0に初期化されること。
     * 新規ページの初期化処理を確認する。
     */
    @Test
    void clearDataSetsAllBytesToZero() {
        Buffer buffer = new Buffer(4096);

        buffer.writeByte(0, (byte) 10);
        buffer.writeByte(100, (byte) 20);

        buffer.clearData();

        assertEquals(0, buffer.readByte(0));
        assertEquals(0, buffer.readByte(100));
    }
}