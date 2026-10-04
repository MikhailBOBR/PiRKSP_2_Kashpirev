package ru.kashpirev;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static java.nio.file.StandardWatchEventKinds.*;

/** Кашпирев Михаил Дмитриевич, ИКБО-11-23. Обмен АС Поставщиков. */
public class SupplierExchange {
    final Path incoming, processed, rejected;
    boolean channelUsed;
    SupplierExchange(Path base) throws Exception {
        incoming=base.resolve("incoming"); processed=base.resolve("processed"); rejected=base.resolve("rejected");
        System.out.println("Каталог существовал: "+Files.exists(incoming));
        for(Path dir:List.of(incoming,processed,rejected)) Files.createDirectories(dir);
    }
    void inspect() throws Exception {
        System.out.println("LIST incoming:");
        try(var list=Files.list(incoming)) {
            for(Path p:list.filter(Files::isRegularFile).sorted().toList())
                System.out.printf("%s | %d bytes | modified %s%n",p.getFileName(),Files.size(p),Files.getLastModifiedTime(p));
        }
    }
    boolean csv(Path p) { return Files.isRegularFile(p)&&p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"); }
    byte[] readChannel(Path p) throws Exception {
        var result=new java.io.ByteArrayOutputStream();
        try(FileChannel ch=FileChannel.open(p,StandardOpenOption.READ)) {
            ByteBuffer buffer=ByteBuffer.allocate(1024);
            while(ch.read(buffer)!=-1) { buffer.flip(); byte[] part=new byte[buffer.remaining()]; buffer.get(part); result.write(part); buffer.clear(); }
        }
        System.out.println("Reader: FileChannel + ByteBuffer (1024 bytes)");
        return result.toByteArray();
    }
    String sha(Path p) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var stream=Files.newInputStream(p)) { byte[] buf=new byte[4096]; int n; while((n=stream.read(buf))!=-1) digest.update(buf,0,n); }
        return HexFormat.of().formatHex(digest.digest());
    }
    void process(Path p) throws Exception {
        if(!csv(p)) return;
        String hash=sha(p); long size=Files.size(p);
        try {
            List<String> lines;
            if(!channelUsed) { channelUsed=true; lines=new String(readChannel(p),StandardCharsets.UTF_8).lines().toList(); }
            else { System.out.println("Reader: Files.readAllLines UTF-8"); lines=Files.readAllLines(p,StandardCharsets.UTF_8); }
            if(lines.size()<2 || !lines.getFirst().equals("supplierId;supplierName;product;price;quantity")) throw new IllegalArgumentException("Invalid CSV header/empty file");
            Map<Integer,BigDecimal> totals=new TreeMap<>(); int available=0;
            for(int i=1;i<lines.size();i++) {
                if(lines.get(i).isBlank()) continue;
                String[] f=lines.get(i).split(";",-1);
                if(f.length!=5) throw new IllegalArgumentException("Line "+(i+1)+": expected 5 fields");
                int id=Integer.parseInt(f[0]),qty=Integer.parseInt(f[4]); BigDecimal price=new BigDecimal(f[3]);
                if(id<=0||qty<0||price.signum()<0||f[1].isBlank()||f[2].isBlank()) throw new IllegalArgumentException("Invalid values at line "+(i+1));
                if(qty>0) {available++; totals.merge(id,price.multiply(BigDecimal.valueOf(qty)),BigDecimal::add);}
            }
            Path target=unique(processed,p.getFileName().toString()); Files.move(p,target);
            System.out.printf("FILE %s | Size: %d bytes | SHA-256: %s | Status: processed | available=%d | totals=%s%n",target.getFileName(),size,hash,available,totals);
        } catch(IllegalArgumentException ex) {
            Files.move(p,unique(rejected,p.getFileName().toString()));
            System.out.printf("FILE %s | SHA-256: %s | Status: rejected | reason=%s%n",p.getFileName(),hash,ex.getMessage());
        }
    }
    Path unique(Path dir,String name) { Path p=dir.resolve(name); return Files.exists(p)?dir.resolve(System.nanoTime()+"-"+name):p; }
    void scan() throws Exception { try(var files=Files.list(incoming)) {for(Path p:files.filter(this::csv).sorted().toList()) process(p);} }
    void watch(int seconds) throws Exception {
        try(WatchService watcher=FileSystems.getDefault().newWatchService()) {
            incoming.register(watcher,ENTRY_CREATE,ENTRY_MODIFY);
            System.out.println("WatchService START: "+incoming.toAbsolutePath());
            long deadline=seconds>0?System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds):Long.MAX_VALUE;
            while(System.nanoTime()<deadline) {
                WatchKey key=watcher.poll(500,TimeUnit.MILLISECONDS); if(key==null) continue;
                for(WatchEvent<?> event:key.pollEvents()) {
                    if(event.kind()==OVERFLOW) {scan(); continue;}
                    Path p=incoming.resolve((Path)event.context());
                    if(csv(p)) {
                        System.out.println("Обнаружен новый файл: "+p.getFileName()+" ["+event.kind()+"]");
                        // Для внешних копирований ждём несколько одинаковых размеров; наш producer использует atomic move.
                        long prev=-1; int stable=0;
                        for(int attempt=0;attempt<40&&Files.exists(p);attempt++) {
                            long size=Files.size(p); stable=size==prev?stable+1:0; prev=size;
                            if(stable>=3) {try { process(p); } catch(java.io.IOException ex) { System.out.println("IO retry: "+ex.getMessage()); stable=0; continue; } break;}
                            Thread.sleep(100);
                        }
                    }
                }
                if(!key.reset()) break;
            }
            System.out.println("WatchService STOP");
        }
    }
    public static void main(String[] args) throws Exception {
        var options=List.of(args); var app=new SupplierExchange(Path.of("data"));
        System.out.println("Кашпирев Михаил Дмитриевич | ИКБО-11-23 | Java NIO");
        if(options.contains("--seed")||options.contains("--demo")) try(var files=Files.list(Path.of("samples"))) {for(Path p:files.toList()) Files.copy(p,app.incoming.resolve(p.getFileName()),StandardCopyOption.REPLACE_EXISTING);}
        app.inspect(); app.scan();
        if(options.contains("--once")) return;
        ScheduledExecutorService producer=Executors.newSingleThreadScheduledExecutor();
        if(options.contains("--demo")) producer.schedule(()->{
            try {
                Path temp=app.incoming.resolve("live.part");
                Files.writeString(temp,"supplierId;supplierName;product;price;quantity\n44;Orion;Webcam;3500.00;7\n",StandardCharsets.UTF_8);
                Files.move(temp,app.unique(app.incoming,"supply-live.csv"),StandardCopyOption.ATOMIC_MOVE);
            } catch(Exception e) {e.printStackTrace();}
        },2,TimeUnit.SECONDS);
        try {app.watch(options.contains("--demo")?7:0);} finally {producer.shutdownNow();}
    }
}
