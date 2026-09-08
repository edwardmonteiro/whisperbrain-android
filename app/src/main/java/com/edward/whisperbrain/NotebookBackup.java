package com.edward.whisperbrain;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Explicit portable export. The ZIP is readable, unlike the encrypted app storage. Never includes API credentials. */
public final class NotebookBackup {
    private NotebookBackup() {}
    public static void exportAll(Context context,OutputStream target) throws Exception {
        JSONObject data=NotebookStore.get(context).snapshot();GraphData.validate(data);
        try(ZipOutputStream out=new ZipOutputStream(target)) {
            entry(out,"notebook.json",data.toString(2));
            JSONArray maps=data.optJSONArray("daily_maps");
            if(maps!=null)for(int i=0;i<maps.length();i++){
                JSONObject map=maps.getJSONObject(i);StringBuilder md=new StringBuilder("# Meu dia · "+map.getString("day")+"\n\nTemas sugeridos pela IA a partir de notificações recebidas.\n\nFuso: "+map.getString("zone")+"\n\nMensagens selecionadas: "+map.getInt("selected")+" de "+map.getInt("total")+"\n\n");
                JSONArray topics=map.getJSONArray("topics");for(int j=0;j<topics.length();j++){JSONObject topic=topics.getJSONObject(j);md.append("## ").append(topic.getString("label")).append("\n\n").append(topic.getString("summary")).append("\n\n").append(topic.getString("reason")).append("\n\n");JSONArray refs=topic.getJSONArray("source_ids");for(int k=0;k<refs.length();k++)md.append("- [[").append(refs.getString(k)).append("]]\n");md.append("\n");}
                JSONArray links=map.getJSONArray("links");for(int j=0;j<links.length();j++){JSONObject link=links.getJSONObject(j);md.append("- ").append(link.getString("from")).append(" → ").append(link.getString("to")).append(": ").append(link.getString("label")).append(" — ").append(link.getString("reason")).append("\n");}
                entry(out,"days/"+map.getString("day")+"-"+i+".md",md.toString());
            }
            JSONArray sessions=data.getJSONArray("sessions"),nodes=data.getJSONArray("nodes"),edges=data.getJSONArray("edges");
            Set<String> copied=new HashSet<>();
            for(int i=0;i<sessions.length();i++) {
                JSONObject session=sessions.getJSONObject(i);JSONArray local=new JSONArray();
                for(int j=0;j<nodes.length();j++)if(nodes.getJSONObject(j).getString("session").equals(session.getString("id")))local.put(nodes.getJSONObject(j));
                entry(out,"sessions/"+session.getString("id")+".md",GraphData.markdown(session,local,GraphData.filterEdges(local,edges)));
            }
            for(int i=0;i<nodes.length();i++) {
                JSONObject n=nodes.getJSONObject(i);String id=n.getString("id"),a=n.optString("attachment");
                StringBuilder md=new StringBuilder("# "+n.getString("title")+"\n\nOrigem: "+n.getString("origin")+"\n\n"+n.getString("body")+"\n\n");
                for(int j=0;j<edges.length();j++){JSONObject e=edges.getJSONObject(j);if(e.getString("from").equals(id))md.append("- [[").append(e.getString("to")).append("]] · ").append(e.getString("relation")).append(" · ").append(e.getString("state")).append("\n");}
                if(!a.isEmpty())md.append("\n![[audio/").append(a).append(n.optString("format").equals("pcm24k")?".wav": ".m4a").append("]]\n");
                entry(out,"neurons/"+id+".md",md.toString());
                if(!a.isEmpty() && copied.add(a)) {
                    String format=n.optString("format","m4a");
                    File media=AudioArchive.playback(context,a,format);
                    try {out.putNextEntry(new ZipEntry("audio/"+a+(format.equals("pcm24k")?".wav":".m4a")));try(InputStream in=new FileInputStream(media)){copy(in,out,160_000_044);}out.closeEntry();}
                    finally {media.delete();}
                }
            }
        }
    }
    private static void entry(ZipOutputStream z,String path,String text)throws Exception {z.putNextEntry(new ZipEntry(path));z.write(text.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    private static long copy(InputStream in,OutputStream out,long max)throws Exception {byte[] b=new byte[16384];int n;long count=0;while((n=in.read(b))!=-1){count+=n;if(count>max)throw new IOException("Arquivo excede o limite.");out.write(b,0,n);}return count;}
    public static int importAll(Context context,InputStream source) throws Exception {
        JSONObject data=null;Map<String,String> attachments=new HashMap<>();Set<String> names=new HashSet<>();long total=0;
        try(ZipInputStream in=new ZipInputStream(source)) {
            ZipEntry e;
            while((e=in.getNextEntry())!=null) {
                String name=e.getName();if(!names.add(name))throw new IOException("Arquivo duplicado no backup.");
                if(name.equals("notebook.json")) {
                    ByteArrayOutputStream text=new ByteArrayOutputStream();total+=copy(in,text,32_000_000);data=new JSONObject(text.toString(StandardCharsets.UTF_8.name()));
                } else if(name.matches("audio/[a-f0-9-]{36}\\.(wav|m4a)")) {
                    if(name.endsWith(".wav")) {
                        byte[] header=new byte[44];new DataInputStream(in).readFully(header);total+=44;
                        if(header[0]!='R'||header[8]!='W'||header[20]!=1||header[22]!=1||header[24]!=(byte)0xc0||header[25]!=0x5d||header[34]!=16)throw new IOException("Formato WAV não reconhecido.");
                    }
                    AudioArchive.Writer writer=new AudioArchive.Writer(context);
                    try {byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){total+=n;if(total>500_000_000)throw new IOException("Backup excede 500 MB.");writer.write(b,n);}writer.close();attachments.put(name.substring(6,42),writer.id);}
                    catch(Exception x){writer.discard();throw x;}
                } else {byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){total+=n;if(total>500_000_000)throw new IOException("Backup excede 500 MB.");}}
                if(total>500_000_000)throw new IOException("Backup excede 500 MB.");in.closeEntry();
            }
            if(data==null)throw new IOException("notebook.json ausente.");GraphData.validate(data);
            Set<String> needed=new HashSet<>();JSONArray nodes=data.getJSONArray("nodes");for(int i=0;i<nodes.length();i++){String a=nodes.getJSONObject(i).optString("attachment");if(!a.isEmpty())needed.add(a);}
            if(!needed.equals(attachments.keySet()))throw new IOException("Anexos ausentes ou sem referência.");
            NotebookStore.get(context).importSnapshot(data,attachments);return data.getJSONArray("sessions").length();
        }catch(Exception e){for(String id:attachments.values())AudioArchive.delete(context,id);throw e;}
    }
}
