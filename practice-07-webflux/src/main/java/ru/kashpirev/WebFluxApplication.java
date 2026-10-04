package ru.kashpirev;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
@SpringBootApplication @RestController @RequestMapping("/api/deliveries")
public class WebFluxApplication {
    record Delivery(long id,long orderId,String product,String address,String status){}
    record Input(long orderId,String product,String address,String status){}
    final ConcurrentMap<Long,Delivery> data=new ConcurrentHashMap<>();
    final ConcurrentMap<Long,Sinks.Many<Delivery>> updates=new ConcurrentHashMap<>();
    final AtomicLong ids=new AtomicLong(1);final WebClient orders;
    public WebFluxApplication(@Value("${orders.base-url}") String url){orders=WebClient.builder().baseUrl(url).build();data.put(1L,new Delivery(1,101,"SSD 1TB","Москва, ул. Академическая, 12","CREATED"));}
    Sinks.Many<Delivery> sink(long id){return updates.computeIfAbsent(id,k->Sinks.many().replay().limit(16));}
    Mono<Delivery> find(long id){return Mono.defer(()->Mono.justOrEmpty(data.get(id))).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,"Delivery not found")));}
    void valid(Input i){if(i.orderId()<=0||i.product()==null||i.product().isBlank()||i.address()==null||i.address().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid delivery");}
    @GetMapping public Flux<Delivery> list(){return Flux.defer(()->Flux.fromIterable(data.values())).filter(d->!d.status().equals("CANCELLED")).sort(Comparator.comparingLong(Delivery::id)).map(d->d);}
    @GetMapping("/{id}") public Mono<Delivery> get(@PathVariable long id){return find(id);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Mono<Delivery> create(@RequestBody Input input){return Mono.fromCallable(()->{valid(input);long id=ids.incrementAndGet();var d=new Delivery(id,input.orderId(),input.product(),input.address(),"CREATED");data.put(id,d);sink(id).tryEmitNext(d);return d;});}
    @PutMapping("/{id}") public Mono<Delivery> update(@PathVariable long id,@RequestBody Input input){
        return find(id).flatMap(old->Mono.fromCallable(()->{
            valid(input);String next=input.status();Map<String,String> transitions=Map.of("CREATED","ACCEPTED","ACCEPTED","IN_TRANSIT","IN_TRANSIT","DELIVERED");
            if(!Objects.equals(transitions.get(old.status()),next)&&!((old.status().equals("CREATED")||old.status().equals("ACCEPTED"))&&"CANCELLED".equals(next)))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid status transition");
            var d=new Delivery(id,input.orderId(),input.product(),input.address(),next);
            if(!data.replace(id,old,d))throw new ResponseStatusException(HttpStatus.CONFLICT,"Concurrent update");
            sink(id).tryEmitNext(d);return d;
        }));
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public Mono<Void> delete(@PathVariable long id){return find(id).doOnNext(d->{data.remove(id);Sinks.Many<Delivery> s=updates.remove(id);if(s!=null)s.tryEmitComplete();}).then();}
    @GetMapping(value="/{id}/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Delivery>> stream(@PathVariable long id){return find(id).flatMapMany(first->Flux.concat(Mono.just(first),sink(id).asFlux()).distinctUntilChanged())
        .map(d->ServerSentEvent.builder(d).id(d.id()+":"+d.status()).event("delivery-status").build());}
    @GetMapping("/{id}/order") public Mono<Map> order(@PathVariable long id){return find(id).flatMap(d->orders.get().uri("/api/orders/{id}",d.orderId()).retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(2)))
        .onErrorResume(e-> e instanceof ResponseStatusException?Mono.error(e):Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Order Service unavailable",e)));}
    public static void main(String[] args){SpringApplication.run(WebFluxApplication.class,args);}
}
