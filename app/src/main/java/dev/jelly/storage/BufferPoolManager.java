package dev.jelly.storage;

import java.io.IOException;
import java.nio.ByteBuffer;

import dev.jelly.common.PageConstants;
import dev.jelly.storage.DiskManager;
import dev.jelly.storage.PageId;

public class BufferPoolManager {
    private static final int DEFAULT_POOL_SIZE = 1;
    private static final int PAGE_SIZE = PageConstants.PAGE_SIZE;

    private final DiskManager disk;
    private final BufferPool bufferPool;

    public BufferPoolManager(DiskManager disk) {
        this.disk = disk;
        this.bufferPool = new BufferPool(
            DEFAULT_POOL_SIZE,
            PAGE_SIZE
        );
    }

    /*
     * テスト用にpoolSize/pageSizeを指定できる。
     */
    public BufferPoolManager(DiskManager disk, int poolSize, int pageSize) {

        this.disk = disk;
        this.bufferPool = new BufferPool(poolSize, pageSize);
    }

    public Buffer fetchPage(PageId pageId) throws IOException {
        // キャッシュヒット
        Buffer cached = bufferPool.getIfExists(pageId);

        if (cached != null) {
            cached.pin();
            return cached;
        }

        // フレーム確保
        int frameIndex = bufferPool.allocateFrame();

        if (frameIndex == -1) {
            throw new IllegalStateException("No available frame: all frames are pinned");
        }

        Buffer frame = bufferPool.getFrameByIndex(frameIndex);
        PageId oldPageId = frame.getPageId();

        // 古いページがdirtyならflush
        flushVictimIfDirty(frame);

        // 新ページを一時バッファに読み込む
        ByteBuffer temporary = ByteBuffer.allocate(frame.getPageSize());

        disk.read(pageId, temporary);

        // 読み込み成功後にフレームを切り替える
        frame.copyFrom(temporary);
        frame.initialize(pageId, false);

        // 古い登録を消して新しい登録を行う
        bufferPool.replacePage(frameIndex, oldPageId, pageId);

        // 呼び出し元に返すためpin
        frame.pin();

        return frame;
    }

    public int getPageCount() {
        return this.disk.getPageCount();
    }

    public Buffer newPage() throws IOException {
        // 先にフレームを確保する
        int frameIndex = bufferPool.allocateFrame();

        if (frameIndex == -1) {
            throw new IllegalStateException("No available frame: all frames are pinned");
        }

        Buffer frame = bufferPool.getFrameByIndex(frameIndex);
        PageId oldPageId = frame.getPageId();

        // 古いページがdirtyならflush
        flushVictimIfDirty(frame);

        // 新しいPageIdを確保
        PageId newPageId = disk.allocate();

        // 新規ページの内容をゼロ初期化
        frame.clearData();
        frame.initialize(newPageId, true);

        // pageTableを切り替える
        bufferPool.replacePage(frameIndex, oldPageId, newPageId);

        // 呼び出し元が使えるようpin
        frame.pin();

        return frame;
    }

    /*
     * dirtyな退避候補をディスクへ書き戻す。
     *
     * 成功するまでdirtyフラグを消さない。
     */
    private void flushVictimIfDirty(Buffer frame) throws IOException {

        if (frame.getPageId().isInvalid()) {
            return;
        }

        if (!frame.isDirty()) {
            return;
        }

        disk.write(frame.getPageId(), frame.internalData().duplicate());

        frame.clearDirty();
    }

    public void unpinPage(PageId pageId) {
        Buffer buffer = bufferPool.getIfExists(pageId);

        if (buffer == null) {
            throw new IllegalArgumentException(
                "Page is not in buffer pool: " + pageId
            );
        }

        buffer.unpin();
    }

    public void unpinPage(PageId pageId, boolean isDirty) {

        Buffer buffer = bufferPool.getIfExists(pageId);

        if (buffer == null) {
            throw new IllegalArgumentException(
                "Page is not in buffer pool: " + pageId
            );
        }

        if (isDirty) {
            buffer.markDirty();
        }

        buffer.unpin();
    }

    public void flushPage(PageId pageId) throws IOException {
        Buffer buffer = bufferPool.getIfExists(pageId);

        if (buffer == null || !buffer.isDirty()) {
            return;
        }

        disk.write(
            pageId,
            buffer.internalData().duplicate()
        );

        buffer.clearDirty();
    }

    public void flushAll() throws IOException {
        for (int i = 0; i < bufferPool.getPoolSize(); i++) {
            Buffer buffer = bufferPool.getFrameByIndex(i);

            if (buffer.getPageId().isInvalid()) {
                continue;
            }

            if (!buffer.isDirty()) {
                continue;
            }

            disk.write(
                buffer.getPageId(),
                buffer.internalData().duplicate()
            );

            buffer.clearDirty();
        }
    }

    /*
     * テスト用の読み取りAPI。
     */
    BufferPool getBufferPool() {
        return bufferPool;
    }
}
