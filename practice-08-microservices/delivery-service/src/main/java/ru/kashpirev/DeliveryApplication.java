package ru.kashpirev;
import org.springframework.boot.*;import org.springframework.boot.autoconfigure.*;import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.beans.factory.annotation.Value;import org.springframework.context.annotation.Bean;
import org.springframework.amqp.core.*;import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.http.*;import org.springframework.http.client.JdkClientHttpRequestFactory;import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;import java.time.*;
@SpringBootApplication @EnableScheduling @RestController @RequestMapping("/api/deliveries")
public class DeliveryApplication {
    final JdbcTemplate db;final TransactionTemplate tx;final RestClient orders;
    public DeliveryApplication(JdbcTemplate db,org.springframework.transaction.PlatformTransactionManager tm,@Value("${order.base-url}")String url){
        this.db=db;tx=new TransactionTemplate(tm);var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());factory.setReadTimeout(Duration.ofSeconds(2));orders=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    @Bean org.springframework.amqp.core.Queue orderQueue(){return new org.springframework.amqp.core.Queue("order.created",true);}
    record OrderEvent(String eventId,String orderId,long productId,int quantity,String address,String requestId){}
    @RabbitListener(queues="order.created") public void receive(String json)throws Exception{
        var e=new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,OrderEvent.class);
        try {
            tx.executeWithoutResult(s->{
                db.update("insert into processed_events(event_id) values(?)",e.eventId());
                db.update("insert into deliveries(order_id,event_id,address,status,request_id,synced) values(?,?,?,?,?,false)",e.orderId(),e.eventId(),e.address(),"CREATED",e.requestId());
            });
            System.out.println("requestId="+e.requestId()+" CONSUMED OrderCreated eventId="+e.eventId()+" delivery orderId="+e.orderId());
        }catch(org.springframework.dao.DuplicateKeyException duplicate){System.out.println("requestId="+e.requestId()+" DUPLICATE ignored eventId="+e.eventId());}
    }
    @Scheduled(fixedDelay=750) void syncStatuses(){for(var row:db.queryForList("select * from deliveries where synced=false"))try{
        String status=switch(row.get("STATUS").toString()){case "CREATED"->"DELIVERY_CREATED";default->row.get("STATUS").toString();};
        orders.put().uri("/api/orders/{id}/status",row.get("ORDER_ID")).header("X-Request-ID",row.get("REQUEST_ID").toString()).body(Map.of("status",status)).retrieve().toBodilessEntity();
        db.update("update deliveries set synced=true where order_id=? and status=?",row.get("ORDER_ID"),row.get("STATUS"));
        System.out.println("requestId="+row.get("REQUEST_ID")+" Delivery -> Order synchronized status="+status);
    }catch(Exception e){System.out.println("STATUS SYNC RETRY "+e.getClass().getSimpleName());}}
    @GetMapping public List<Map<String,Object>> list(){return db.queryForList("select * from deliveries order by order_id");}
    @PutMapping("/{orderId}/status") public Map<String,Object> update(@PathVariable String orderId,@RequestBody Map<String,String> input){
        var row=db.queryForList("select * from deliveries where order_id=?",orderId).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Delivery not found"));
        String current=row.get("STATUS").toString(),next=input.get("status");
        if(!Objects.equals(Map.of("CREATED","IN_TRANSIT","IN_TRANSIT","DELIVERED").get(current),next))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid transition");
        int changed=db.update("update deliveries set status=?,synced=false where order_id=? and status=?",next,orderId,current);
        if(changed==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Concurrent update");return db.queryForMap("select * from deliveries where order_id=?",orderId);
    }
    public static void main(String[] args){SpringApplication.run(DeliveryApplication.class,args);}
}
