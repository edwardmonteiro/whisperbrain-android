package com.edward.whisperbrain;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

/** Encrypted audio attachments; clear audio exists only during capture/playback or a user export. */
public final class AudioArchive {
    private AudioArchive() {}
    private static File file(Context c,String id) {
        if(!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Anexo inválido.");
        File dir=new File(c.getFilesDir(),"audio");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Sem espaço para áudio.");
        return new File(dir,id+".enc");
    }
    public static void delete(Context c,String id) {if(!id.isEmpty()) file(c,id).delete();}
    public static final class Writer implements Closeable {
        public final String id=GraphData.id();
        private final File path;
        private final OutputStream out;
        public long bytes;
        public Writer(Context c) throws Exception {
            path=file(c,id);Cipher cipher=new Vault(c).encryptor("audio:"+id);
            FileOutputStream base=new FileOutputStream(path);
            try {byte[] iv=cipher.getIV();base.write(iv.length);base.write(iv);out=new CipherOutputStream(base,cipher);}
            catch(Exception e){base.close();path.delete();throw e;}
        }
        public void write(byte[] data,int count) throws IOException {if(bytes+count>160_000_000)throw new IOException("Limite do áudio atingido.");out.write(data,0,count);bytes+=count;}
        @Override public void close() throws IOException {out.close();}
        public void discard(){try{out.close();}catch(Exception ignored){}path.delete();}
    }
    public static InputStream open(Context c,String id) throws Exception {
        FileInputStream in=new FileInputStream(file(c,id));
        try{int length=in.read();if(length!=12)throw new IOException("Cabeçalho de áudio inválido.");byte[] iv=new byte[length];new DataInputStream(in).readFully(iv);return new CipherInputStream(in,new Vault(c).decryptor("audio:"+id,iv));}
        catch(Exception e){in.close();throw e;}
    }
    public static String save(Context c,InputStream in) throws Exception {
        Writer writer=new Writer(c);try {byte[] data=new byte[16384];int n;while((n=in.read(data))!=-1)writer.write(data,n);writer.close();return writer.id;}
        catch(Exception e){writer.discard();throw e;}
    }
    public static File playback(Context c,String id,String format) throws Exception {
        File temp=File.createTempFile("wb-play-",format.equals("pcm24k")?".wav":".m4a",c.getCacheDir());
        try(InputStream in=open(c,id);OutputStream out=new FileOutputStream(temp)){
            if(format.equals("pcm24k"))out.write(new byte[44]);byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
        }catch(Exception e){temp.delete();throw e;}
        if(format.equals("pcm24k"))try(RandomAccessFile f=new RandomAccessFile(temp,"rw")){
            int size=(int)(f.length()-44);f.write("RIFF".getBytes(StandardCharsets.US_ASCII));le(f,size+36);f.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));le(f,16);
            f.write(new byte[]{1,0,1,0});le(f,24000);le(f,48000);f.write(new byte[]{2,0,16,0});f.write("data".getBytes(StandardCharsets.US_ASCII));le(f,size);
        }
        return temp;
    }
    private static void le(RandomAccessFile f,int n)throws IOException {for(int i=0;i<4;i++)f.write((n>>>(8*i))&255);}
}
