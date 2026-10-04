package ru.kashpirev;
import org.springframework.boot.*;import org.springframework.boot.autoconfigure.*;
import org.springframework.web.bind.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;import org.springframework.context.annotation.Bean;
import org.springframework.amqp.core.*;import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.*;import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.*;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.ServerSentEvent;import org.springframework.web.reactive.function.client.*;import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.*;import reactor.core.scheduler.Schedulers;import reactor.util.retry.Retry;
import io.github.resilience4j.circuitbreaker.*;import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import java.util.*;import java.time.*;import java.math.BigDecimal;import java.util.concurrent.ConcurrentHashMap;
@SpringBootApplication @EnableScheduling @RestController @RequestMapping("/api/orders")
public class OrderApplication {
    final JdbcTemplate db;final TransactionTemplate transactions;final RabbitTemplate rabbit;final WebClient suppliers;
    final CircuitBreaker breaker=CircuitBreaker.of("supplier",CircuitBreakerConfig.custom().slidingWindowSize(4).minimumNumberOfCalls(2).failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(5)).permittedNumberOfCallsInHalfOpenState(1).build());
    final ConcurrentHashMap<String,Sinks.Many<Map<String,Object>>> events=new ConcurrentHashMap<>();
    public OrderApplication(JdbcTemplate db,org.springframework.transaction.PlatformTransactionManager tm,RabbitTemplate rabbit,@Value("${supplier.base-url}")String url){this.db=db;this.transactions=new TransactionTemplate(tm);this.rabbit=rabbit;this.suppliers=WebClient.builder().baseUrl(url).build();}
    @Bean org.springframework.amqp.core.Queue orderQueue(){return new org.springframework.amqp.core.Queue("order.created",true);}
    record Request(long productId,int quantity,String address){}
    record OrderEvent(String eventId,String orderId,long productId,int quantity,String address,String requestId){}
    Mono<Map<String,Object>> find(String id){return Mono.fromCallable(()->db.queryForList("select * from orders where id=?",id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"))).subscribeOn(Schedulers.boundedElastic());}
    @GetMapping public Flux<Map<String,Object>> list(){return Mono.fromCallable(()->db.queryForList("select * from orders order by created_at")).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable);}
    @GetMapping("/{id}") public Mono<Map<String,Object>> get(@PathVariable String id){return find(id);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Mono<Map<String,Object>> create(@RequestBody Request r,@RequestHeader(value="X-Request-ID",defaultValue="missing")String requestId){
        if(r.quantity()<=0||r.productId()<=0||r.address()==null||r.address().isBlank())return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid order"));
        System.out.println("requestId="+requestId+" Order calling Supplier breaker="+breaker.getState());
        return suppliers.get().uri("/api/suppliers/products/{id}",r.productId()).header("X-Request-ID",requestId).retrieve().bodyToMono(Map.class)
          .timeout(Duration.ofSeconds(2)).retryWhen(Retry.backoff(1,Duration.ofMillis(150)).filter(e->e instanceof WebClientRequestException||e instanceof java.util.concurrent.TimeoutException||e instanceof WebClientResponseException w&&w.getStatusCode().is5xxServerError()).onRetryExhaustedThrow((spec,signal)->signal.failure()))
          .transformDeferred(CircuitBreakerOperator.of(breaker)).flatMap(product->Mono.fromCallable(()->{
              if(((Number)product.get("QUANTITY")).intValue()<r.quantity())throw new ResponseStatusException(HttpStatus.CONFLICT,"Insufficient stock");
              String id=UUID.randomUUID().toString(),eid=UUID.randomUUID().toString();BigDecimal total=new BigDecimal(product.get("PRICE").toString()).multiply(BigDecimal.valueOf(r.quantity()));
              transactions.executeWithoutResult(tx->{
                  db.update("insert into orders(id,product_id,quantity,address,total,status,request_id,created_at) values(?,?,?,?,?,?,?,?)",id,r.productId(),r.quantity(),r.address(),total,"CREATED",requestId,Instant.now().toString());
                  db.update("insert into outbox(event_id,order_id,product_id,quantity,address,request_id,sent) values(?,?,?,?,?,?,false)",eid,id,r.productId(),r.quantity(),r.address(),requestId);
              });
              System.out.println("requestId="+requestId+" OrderCreated saved outbox eventId="+eid+" orderId="+id);
              return db.queryForMap("select * from orders where id=?",id);
          }).subscribeOn(Schedulers.boundedElastic()))
          .onErrorResume(e->{
              if(e instanceof ResponseStatusException)return Mono.error(e);
              if(e instanceof WebClientResponseException w&&w.getStatusCode().value()==404)return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));
              System.out.println("requestId="+requestId+" Supplier failure breaker="+breaker.getState()+" "+e.getClass().getSimpleName());
              return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Supplier temporarily unavailable; retry later"));
          });
    }
    @Scheduled(fixedDelay=750) void publishOutbox(){
        for(var row:db.queryForList("select * from outbox where sent=false")){
            try{
                var event=new OrderEvent(row.get("EVENT_ID").toString(),row.get("ORDER_ID").toString(),((Number)row.get("PRODUCT_ID")).longValue(),((Number)row.get("QUANTITY")).intValue(),row.get("ADDRESS").toString(),row.get("REQUEST_ID").toString());
                String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(event);
                rabbit.convertAndSend("","order.created",json);
                db.update("update outbox set sent=true where event_id=?",event.eventId());
                System.out.println("requestId="+event.requestId()+" PUBLISHED OrderCreated eventId="+event.eventId()+" queue=order.created");
            }catch(Exception e){System.out.println("OUTBOX RETRY "+e.getClass().getSimpleName());break;}
        }
    }
    @GetMapping(value="/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String,Object>>> stream(@PathVariable String id){
        return find(id).flatMapMany(initial->Flux.interval(Duration.ZERO,Duration.ofMillis(350)).flatMap(t->find(id)).distinctUntilChanged(m->m.get("STATUS")))
            .map(m->ServerSentEvent.builder(m).event("order-status").id(id+":"+m.get("STATUS")).build());
    }
    @PutMapping("/{id}/status") public Mono<Map<String,Object>> status(@PathVariable String id,@RequestBody Map<String,String> input,@RequestHeader(value="X-Request-ID",defaultValue="missing")String requestId){
        return find(id).flatMap(old->Mono.fromCallable(()->{
            String current=old.get("STATUS").toString(),next=input.get("status");
            if(Objects.equals(current,next))return old;
            Map<String,String> transitions=Map.of("CREATED","DELIVERY_CREATED","DELIVERY_CREATED","IN_TRANSIT","IN_TRANSIT","DELIVERED");
            if(!Objects.equals(transitions.get(current),next))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid transition");
            int changed=db.update("update orders set status=? where id=? and status=?",next,id,current);
            if(changed==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Concurrent update");
            System.out.println("requestId="+requestId+" Order status="+next+" id="+id);return db.queryForMap("select * from orders where id=?",id);
        }).subscribeOn(Schedulers.boundedElastic()));
    }
    public static void main(String[] args){SpringApplication.run(OrderApplication.class,args);}
}
