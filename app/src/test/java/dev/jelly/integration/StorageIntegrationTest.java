package dev.jelly.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jelly.buffer.Buffer;
import dev.jelly.buffer.BufferPoolManager;
import dev.jelly.disk.DiskManager;
import dev.jelly.disk.PageId;

public class StorageIntegrationTest {

    private DiskManager disk;
    private BufferPoolManager bpm;

    @BeforeEach
    void setup() throws IOException {
        Path tempFile = Files.createTempFile("storage-integration-test", ".db");
        disk = new DiskManager(tempFile.toString());
        bpm = new BufferPoolManager(disk);
    }

    // 1. newPage() で新しいページを作る
    // 2. そのページに書き込む
    // 3. unpin() する
    // 4. fetchPage() でキャッシュヒットする
    // 5. flushAll() で Disk に書き戻す
    // 6. 再度 fetchPage() して Disk の内容が正しいか確認する
    @Test
    void testStorageIntegration() throws IOException {
        // 1. newPage()で新しいページを作成(DiskManager.allocate → PageId発行)
        Buffer buf = bpm.newPage();
        PageId pid = buf.getPageId();

        // 2. ページに書き込み(dirty = true)
        buf.writeByte(0, (byte) 123);

        // 3. unpin()してキャッシュ内で解放(evict対象になり得る状態に)
        buf.unpin();

        // 4. fetchPage() → キャッシュヒット(Diskを読まずにBufferPoolから返る)
        Buffer buf2 = bpm.fetchPage(pid);
        assertEquals(123, buf2.readByte(0));
        buf2.unpin();

        // 5. flushAll() → dirtyページをDiskに書き戻す
        bpm.flushAll();

        // 6. 再fetchPage() → Diskの内容が正しく読み込まれることを確認
        Buffer buf3 = bpm.fetchPage(pid);
        assertEquals(123, buf3.readByte(0));
        buf3.unpin();
    }
}
