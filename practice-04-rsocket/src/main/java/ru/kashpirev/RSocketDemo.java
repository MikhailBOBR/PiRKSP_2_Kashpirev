package ru.kashpirev;
import io.rsocket.*;
import io.rsocket.core.*;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import org.reactivestreams.Publisher;
import reactor.core.publisher.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
public class RSocketDemo {
    record Delivery(long id,long orderId,String product,String address,String status) {}
    static final ObjectMapper json=new ObjectMapper();
    static final Delivery item=new Delivery(1,101,"SSD 1TB","Москва, ул. Академическая, 12","CREATED");
    static void log(String msg) {System.out.println(Instant.now()+" ["+Thread.currentThread().getName()+"] "+msg);}
    static String consume(Payload p) {try{return p.getDataUtf8();}finally{p.release();}}
    static Payload payload(Object value) {try{return DefaultPayload.create(json.writeValueAsString(value));}catch(Exception e){throw new IllegalStateException(e);}}
    static RSocket responder() {return new RSocket() {
        @Override public Mono<Payload> requestResponse(Payload p) {
            String id=consume(p); log("SERVER request-response id="+id);
            return Mono.defer(()->id.equals("1")?Mono.just(payload(item)):Mono.error(new IllegalArgumentException("Delivery not found: "+id)));
        }
        @Override public Flux<Payload> requestStream(Payload p) {
            String id=consume(p); log("SERVER request-stream id="+id);
            if(!id.equals("1")) return Flux.error(new IllegalArgumentException("Delivery not found"));
            return Flux.fromIterable(List.of("CREATED","ACCEPTED","IN_TRANSIT","DELIVERED"))
                .delayElements(Duration.ofMillis(350)).map(s->payload(new Delivery(1,101,item.product(),item.address(),s)));
        }
        @Override public Mono<Void> fireAndForget(Payload p) {
            String event=consume(p); return Mono.fromRunnable(()->log("SERVER fire-and-forget event="+event));
        }
        @Override public Flux<Payload> requestChannel(Publisher<Payload> incoming) {
            return Flux.from(incoming).map(RSocketDemo::consume).doOnNext(s->log("SERVER channel received="+s))
                .map(s->DefaultPayload.create("ack:"+s));
        }
    };}
    public static void main(String[] args) {
        int port=Integer.parseInt(System.getenv().getOrDefault("RSOCKET_PORT","7104"));
        String mode=args.length==0?"demo":args[0];
        log("Кашпирев Михаил Дмитриевич | ИКБО-11-23 | RSocket");
        var server=mode.equals("client")?null:RSocketServer.create(SocketAcceptor.with(responder())).bind(TcpServerTransport.create("127.0.0.1",port)).block();
        if(mode.equals("server")) {log("SERVER READY port="+port);server.onClose().block();return;}
        RSocket client=RSocketConnector.create().connect(TcpClientTransport.create("127.0.0.1",port)).block();
        try {
            client.requestResponse(DefaultPayload.create("1")).map(RSocketDemo::consume).doOnNext(s->log("CLIENT RR "+s)).block();
            client.requestResponse(DefaultPayload.create("999")).map(RSocketDemo::consume).onErrorResume(e->{log("CLIENT HANDLED "+e.getMessage());return Mono.empty();}).block();
            client.requestStream(DefaultPayload.create("1")).map(RSocketDemo::consume).filter(s->!s.isBlank()).map(String::trim).doOnNext(s->log("CLIENT STREAM "+s)).blockLast();
            client.fireAndForget(DefaultPayload.create("DeliveryViewed:1")).block();
            client.requestChannel(Flux.just("courier-ready","position:55.75,37.61","delivered").delayElements(Duration.ofMillis(180)).map(DefaultPayload::create))
              .map(RSocketDemo::consume).doOnNext(s->log("CLIENT CHANNEL "+s)).blockLast();
            log("ALL FOUR MODELS COMPLETE");
        } finally {client.dispose();if(server!=null)server.dispose();}
    }
}
