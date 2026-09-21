package dev.jelly.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import dev.jelly.common.PageConstants;

public class DiskManager implements AutoCloseable{
    private final Path heapFilePath;
    private final FileChannel fileChannel;
    private int nextPageId = 0;

    private static final int PAGE_SIZE = PageConstants.PAGE_SIZE;
    
    public DiskManager(String file_path) throws IOException{
        this.heapFilePath = Paths.get(file_path);
        if(!Files.exists(heapFilePath)) {
            Files.createFile(heapFilePath);
        }
        this.fileChannel = FileChannel.open(
            heapFilePath,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE
        );
        this.nextPageId = (int) (getFileSize() / PAGE_SIZE);
        System.out.println("next pageid: "+this.nextPageId);
    }
    
    public void open(){
        // todo 
    }    
        
    public void close() throws IOException{
        this.fileChannel.close();
    }
    public void read(PageId pageId, ByteBuffer bb)
            throws IOException {

        System.out.println("Read from Disk: PageId: " + pageId);
        long pos = pageId.toOffset(PAGE_SIZE);

        while (bb.hasRemaining()) {
            int n = fileChannel.read(bb, pos);

            if (n == -1) {
                throw new IOException("Unexpected EOF");
            }

            if (n == 0) {
                throw new IOException("No progress reading page");
            }

            pos += n;
        }
    }
    public void write(PageId pageId, ByteBuffer bb)
            throws IOException {
        System.out.println("Write in Disk: PageId: " + pageId);
        long pos = pageId.toOffset(PAGE_SIZE);

        while (bb.hasRemaining()) {
            int n = fileChannel.write(bb, pos);

            if (n == 0) {
                throw new IOException("No progress writing page");
            }

            pos += n;
        }
    }
    public PageId allocate() throws IOException{
        PageId pageId = new PageId(this.nextPageId);
        // このページの最後のバイト位置
        long endPos = pageId.toOffset(PAGE_SIZE) + PAGE_SIZE - 1;

        // ファイルをページサイズ分まで拡張する
        ByteBuffer zero = ByteBuffer.allocate(1);
        zero.put((byte) 0);
        zero.flip();

        while (zero.hasRemaining()) {
            int n = fileChannel.write(zero, endPos);

            if (n == 0) {
                throw new IOException("No progress allocating page");
            }
        }

        // 領域確保に成功してからIDを進める
        this.nextPageId++;

        return pageId;
    }
    private long getFileSize() throws IOException {
        System.out.println("this.fileChannel.size(): "+this.fileChannel.size());
        return this.fileChannel.size();
    }

    public int getPageCount() {
        // TODO Auto-generated method stub
        return this.nextPageId;
    }
}
