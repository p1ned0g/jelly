package dev.jelly.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HeapFile {
    private final BufferPoolManager bufferPoolManager;

    public HeapFile(BufferPoolManager bufferPoolManager) {
        this.bufferPoolManager = bufferPoolManager;
    }

    // レコードを挿入する
    // 空き領域のあるページを探し、なければ新しいページを作成する
    // 挿入成功時はRecordIdを返す
    public RecordId insertRecord(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException(
                "Record data must not be null or empty"
            );
        }

        // 既存ページを順番に探す
        int pageCount = bufferPoolManager.getPageCount();

        for (int i = 0; i < pageCount; i++) {
            PageId pageId = new PageId(i);

            Buffer buffer = bufferPoolManager.fetchPage(pageId);
            boolean dirty = false;

            try {
                SlottedPage page = new SlottedPage(buffer);

                int slotId = page.insertRecord(data);

                // insertRecord内でcompactされる可能性がある
                dirty = true;

                if (slotId != -1) {
                    return new RecordId(pageId, slotId);
                }
            } finally {
                bufferPoolManager.unpinPage(pageId, dirty);
            }
        }

        // 既存ページに入らなかったので新規ページを作成
        Buffer buffer = bufferPoolManager.newPage();
        PageId pageId = buffer.getPageId();

        try {
            SlottedPage page = new SlottedPage(buffer);

            int slotId = page.insertRecord(data);

            if (slotId == -1) {
                throw new IllegalArgumentException(
                    "Record does not fit in a new page"
                );
            }

            return new RecordId(pageId, slotId);
        } finally {
            bufferPoolManager.unpinPage(pageId, true);
        }
    }


    // RecordIdからレコードを取得する
    // 対象ページを取得し、SlottedPage経由でレコードを読む
    public byte[] getRecord(RecordId recordId) throws IOException {
        if(recordId == null) {
            throw new IllegalArgumentException("recordId must not be null");
        }
        PageId pageId = recordId.pageId();
        int slotId = recordId.slotId();
        Buffer buf = this.bufferPoolManager.fetchPage(pageId);
        try {
            SlottedPage sPage = new SlottedPage(buf);
            return sPage.getRecord(slotId);
        } finally {
            this.bufferPoolManager.unpinPage(pageId, false);
        }
    }
    
    // RecordIdからレコードを削除する
    // 対象ページを取得し、SlottedPage経由で削除する
    public void deleteRecord(RecordId recordId) throws IOException {
        if(recordId == null) {
            throw new IllegalArgumentException("recordId must not be null");
        }
        PageId pageId = recordId.pageId();
        int slotId = recordId.slotId();
        Buffer buf = this.bufferPoolManager.fetchPage(pageId);
        boolean dirty = false;

        try {
            SlottedPage page = new SlottedPage(buf);

            boolean result = page.deleteRecord(slotId);

            if (!result) {
                throw new IllegalStateException("delete record: failed");
            }

            dirty = true;
        } finally {
            bufferPoolManager.unpinPage(pageId, dirty);
        }
    }

    public List<byte[]> scan() throws IOException {
        List<byte[]> records = new ArrayList<>();

        // 既存ページ数を取得
        int pageCount = bufferPoolManager.getPageCount();

        for (int i = 0; i < pageCount; i++) {
            PageId pageId = new PageId(i);
            Buffer buf = bufferPoolManager.fetchPage(pageId);

            try {
                SlottedPage sPage = new SlottedPage(buf);

                // ページ内のslotを順番に確認
                int slotCount = sPage.getSlotCount();

                for (int slotId = 0; slotId < slotCount; slotId++) {
                    byte[] record = sPage.getRecord(slotId);

                    // 削除済みslotはスキップ
                    if (record != null) {
                        records.add(record);
                    }
                }
            } finally {
                bufferPoolManager.unpinPage(pageId, false);
            }
        }

        return records;
    }

    // 全ページをディスクへ書き込む
    public void flush() throws IOException {
        this.bufferPoolManager.flushAll();
    }
}