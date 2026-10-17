import java.io.*;

/** Read-only, hash-guarded firmware-specific native state. No memory writes. */
public final class NativeSession implements Closeable {
    final int pid;
    private final RandomAccessFile memory;
    private final long base;
    public NativeSession(int process) throws Exception {
        pid=process;long address=0;
        BufferedReader input=new BufferedReader(new FileReader("/proc/"+pid+"/maps"));
        try {
            String line;
            while((line=input.readLine())!=null) {
                String[] row=line.trim().split("\\s+");
                if(row.length>=6 && row[2].equals("00000000") && row[5].equals("/system/lib/libCarPlayAndroid.so")) {
                    address=Long.parseLong(row[0].split("-")[0],16);break;
                }
            }
        } finally {input.close();}
        if(address==0)throw new IOException("native-map-unavailable");
        base=address;memory=new RandomAccessFile("/proc/"+pid+"/mem","r");
    }
    long u32(long offset) throws IOException {
        if(offset<4096 || offset>0xffffffffL)throw new IOException("native-pointer");
        memory.seek(offset);return Integer.reverseBytes(memory.readInt())&0xffffffffL;
    }
    long context() throws IOException {return u32(base+0x102c0);}
    public boolean ready() throws IOException { return context()!=0; }
    public boolean active() throws IOException {long context=context();return context!=0 && u32(context+0x30)!=0;}
    public boolean hasControlOwner() throws IOException {long context=context();return context!=0 && u32(context+0x174)!=0;}
    public String generation() throws IOException {
        String stat=CarPlayController.read(new File("/proc/"+pid+"/stat"),4096);
        int end=stat.lastIndexOf(')');
        if(end<0)throw new IOException("native-generation");
        String[] fields=stat.substring(end+2).split("\\s+");
        if(fields.length<20 || !fields[19].matches("[1-9][0-9]{0,18}"))throw new IOException("native-generation");
        return pid+":"+fields[19];
    }
    public void close() throws IOException {memory.close();}
    public static int findPid(String process) throws IOException {
        File[] paths=new File("/proc").listFiles();if(paths==null)return 0;
        int found=0;
        for(File file:paths) {
            if(!file.getName().matches("[1-9][0-9]*"))continue;
            FileInputStream input=null;
            try {
                input=new FileInputStream(new File(file,"cmdline"));byte[] raw=new byte[256];int size=input.read(raw);if(size<1)continue;
                int end=0;while(end<size && raw[end]!=0)end++;
                if(process.equals(new String(raw,0,end,"US-ASCII"))) {if(found!=0)throw new IOException("ambiguous-process");found=Integer.parseInt(file.getName());}
            } catch(FileNotFoundException vanished) {} finally {if(input!=null)input.close();}
        }
        return found;
    }
}
