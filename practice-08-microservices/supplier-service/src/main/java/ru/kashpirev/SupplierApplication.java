package ru.kashpirev;
import org.springframework.boot.*;import org.springframework.boot.autoconfigure.*;
import org.springframework.web.bind.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@SpringBootApplication @RestController @RequestMapping("/api/suppliers")
public class SupplierApplication {
    final JdbcTemplate db;
    public SupplierApplication(JdbcTemplate db){this.db=db;}
    @GetMapping public List<Map<String,Object>> list(){return db.queryForList("select * from products order by id");}
    @GetMapping("/products/{id}") public Map<String,Object> product(@PathVariable long id,@RequestHeader(value="X-Request-ID",defaultValue="missing")String requestId){
        System.out.println("requestId="+requestId+" Supplier lookup product="+id);
        return db.queryForList("select * from products where id=?",id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));
    }
    public static void main(String[] args){SpringApplication.run(SupplierApplication.class,args);}
}
