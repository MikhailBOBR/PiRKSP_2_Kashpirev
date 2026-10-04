package ru.kashpirev;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.*;
@SpringBootApplication @RestController @RequestMapping("/api/deliveries")
public class DeliveryApplication {
    final JdbcTemplate db; final RestClient orders;
    @Value("${instance.id}") String instance;
    public DeliveryApplication(JdbcTemplate db,@Value("${orders.base-url}") String url){
        this.db=db;var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());factory.setReadTimeout(Duration.ofSeconds(3));
        orders=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    record Order(long id,String product,int quantity,java.math.BigDecimal total,String address){}
    record Request(long orderId){}
    @GetMapping public Map<String,Object> list(){return Map.of("instance",instance,"deliveries",db.queryForList("select * from deliveries order by order_id"));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(@RequestBody Request request){
        if(request.orderId()<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid orderId");
        Order order;
        try{order=orders.get().uri("/api/orders/{id}",request.orderId()).retrieve().body(Order.class);}
        catch(org.springframework.web.client.HttpClientErrorException.NotFound e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found");}
        catch(org.springframework.web.client.RestClientException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Order Service unavailable");}
        if(order==null)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Empty order");
        try{db.update("insert into deliveries(order_id,product,address,status) values(?,?,?,?)",order.id(),order.product(),order.address(),"CREATED");}
        catch(org.springframework.dao.DuplicateKeyException e){/* repeated request returns existing delivery */}
        return Map.of("instance",instance,"delivery",db.queryForMap("select * from deliveries where order_id=?",order.id()));
    }
    public static void main(String[] args){SpringApplication.run(DeliveryApplication.class,args);}
}
