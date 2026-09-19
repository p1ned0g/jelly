package dev.jelly.buffer;

import java.io.IOException;

import dev.jelly.disk.DiskManager;
import dev.jelly.disk.PageId;

public class BufferPoolManager {
    private final DiskManager disk;
    private BufferPool bufferPool;

    public BufferPoolManager(DiskManager disk) {
        this.disk = disk;
        this.bufferPool = new BufferPool(10, 4096);
    }
    
    public Buffer fetchPage(PageId pageId) throws IOException {
        Buffer buf = this.bufferPool.getIfExists(pageId);
        // BufferPoolに存在する場合はそのBufferを返し、pinCountを1増やす
        if(buf != null) {
            buf.pin();
            return buf;
        }

        // BufferPoolに存在しなかった場合は新規で開いているインデックスを返す
        // または退避したインデックスを返す
        int frameIdx = this.bufferPool.allocateFrame();
        Buffer frame = this.bufferPool.getFrameByIndex(frameIdx);

        // evictされているかつDirtyの場合、Flushする
        if (!frame.getPageId().isInvalid() && frame.isDirty()) {
            disk.write(frame.getPageId(), frame.getData());
        }

        // Disk から読み込む
        disk.read(pageId, frame.getData());

        // Diskから読み込んだデータでBufferPoolのframeを初期化する
        frame.setPageId(pageId);
        frame.clearDirty();
        frame.resetpin();
        frame.pin();

        // BufferPoolのキャッシュ(pageTable)に登録する
        bufferPool.registerPage(pageId, frameIdx);

        return frame;
    }

    public Buffer newPage() throws IOException {
        // Diskにページを新規作成し、PageId をもらう
        PageId newPageId = disk.allocate();

        // BufferPoolに存在しなかった場合は新規で開いているインデックスを返す
        // または退避したインデックスを返す
        int frameIndex = bufferPool.allocateFrame();
        Buffer frame = bufferPool.getFrameByIndex(frameIndex);

        // evictされているかつDirtyの場合、Flushする
        if (!frame.getPageId().isInvalid() && frame.isDirty()) {
            disk.write(frame.getPageId(), frame.getData());
        }

        //　新しいページを初期化
        frame.getData().clear();
        frame.setPageId(newPageId);
        frame.clearDirty();
        frame.resetpin();
        frame.pin();

        // BufferPoolのキャッシュ(pageTable)に登録する
        bufferPool.registerPage(newPageId, frameIndex);

        return frame;
    }


    public void flushPage(PageId pageId) throws IOException {
        Buffer buf = bufferPool.getIfExists(pageId);
        if (buf != null && buf.isDirty()) {
            disk.write(pageId, buf.getData());
            buf.clearDirty();
        }
    }

    public void flushAll() throws IOException {
        for (Buffer buf : bufferPool.getFrames()) {
            if (!buf.getPageId().isInvalid() && buf.isDirty()) {
                disk.write(buf.getPageId(), buf.getData());
                buf.clearDirty();
            }
        }
    }
}
