package ru.kashpirev;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import java.math.BigDecimal;
import java.util.*;
@SpringBootApplication
@RestController
@RequestMapping("/api/orders")
public class OrderApplication {
    final JdbcTemplate db;
    @Value("${instance.id}") String instance;
    public OrderApplication(JdbcTemplate db){this.db=db;}
    record Order(long id,String product,int quantity,BigDecimal total,String address){}
    record NewOrder(String product,int quantity,BigDecimal total,String address){}
    Order row(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new Order(r.getLong("id"),r.getString("product"),r.getInt("quantity"),r.getBigDecimal("total"),r.getString("address"));}
    @GetMapping public Map<String,Object> list(){return Map.of("instance",instance,"orders",db.query("select * from orders order by id",this::row));}
    @GetMapping("/{id}") public Order get(@PathVariable long id){return db.query("select * from orders where id=?",this::row,id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Order create(@RequestBody NewOrder o){
        if(o.quantity()<=0||o.total()==null||o.total().signum()<=0||o.product()==null||o.product().isBlank()||o.address()==null||o.address().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid order");
        long id=db.queryForObject("select nextval('order_seq')",Long.class);
        db.update("insert into orders(id,product,quantity,total,address) values(?,?,?,?,?)",id,o.product(),o.quantity(),o.total(),o.address());return get(id);
    }
    public static void main(String[] args){SpringApplication.run(OrderApplication.class,args);}
}
