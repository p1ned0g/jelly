package dev.jelly.disk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class DiskManager implements AutoCloseable{
    private final Path heapFilePath;
    private final FileChannel fileChannel;
    private int nextPageId = 0;

    private static final int PAGE_SIZE = 4096;
    
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
    public void read(PageId pageId, ByteBuffer bb) throws IOException{
        long pos = pageId.toOffset(PAGE_SIZE);
        this.fileChannel.read(bb, pos);
    }
    public void write(PageId pageId, ByteBuffer bb) throws IOException{
        long pos = pageId.toOffset(PAGE_SIZE);
        this.fileChannel.write(bb, pos);    
    }
    public PageId allocate(){
        PageId pageId = new PageId(this.nextPageId);
        this.nextPageId += 1;
        return pageId;
    }
    private long getFileSize() throws IOException {
        System.out.println("this.fileChannel.size(): "+this.fileChannel.size());
        return this.fileChannel.size();
    }
}
