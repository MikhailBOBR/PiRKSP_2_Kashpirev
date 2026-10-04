package ru.kashpirev;
import org.springframework.boot.*;import org.springframework.boot.autoconfigure.*;import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;import org.springframework.http.*;import org.springframework.web.server.*;
import org.springframework.web.reactive.function.client.*;import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;import java.time.Duration;import java.util.*;
@SpringBootApplication @RestController
public class GatewayApplication {
    final WebClient http=WebClient.create();final Map<String,String> routes;
    public GatewayApplication(@Value("${supplier.base-url}")String supplier,@Value("${order.base-url}")String orders,@Value("${delivery.base-url}")String deliveries){routes=Map.of("suppliers",supplier,"orders",orders,"deliveries",deliveries);}
    @RequestMapping("/api/{service}/**") public Mono<Void> route(@PathVariable String service,ServerWebExchange exchange){
        String base=routes.get(service);if(base==null)return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,"Route not found"));
        String supplied=exchange.getRequest().getHeaders().getFirst("X-Request-ID");
        String requestId=supplied!=null&&supplied.matches("[A-Za-z0-9._-]{1,80}")?supplied:UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set("X-Request-ID",requestId);
        String path=exchange.getRequest().getURI().getRawPath();String query=exchange.getRequest().getURI().getRawQuery();
        System.out.println("requestId="+requestId+" Gateway -> "+service+" "+exchange.getRequest().getMethod()+" "+path);
        var req=http.method(exchange.getRequest().getMethod()).uri(base+path+(query==null?"":"?"+query)).header("X-Request-ID",requestId);
        if(exchange.getRequest().getHeaders().getContentType()!=null)req.contentType(exchange.getRequest().getHeaders().getContentType());
        req.headers(h->{var accept=exchange.getRequest().getHeaders().getAccept();if(!accept.isEmpty())h.setAccept(accept);});
        return req.body(BodyInserters.fromDataBuffers(exchange.getRequest().getBody())).exchangeToMono(response->{
            exchange.getResponse().setStatusCode(response.statusCode());response.headers().contentType().ifPresent(t->exchange.getResponse().getHeaders().setContentType(t));
            return exchange.getResponse().writeWith(response.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class));
        }).timeout(path.endsWith("/events")?Duration.ofHours(1):Duration.ofSeconds(7)).onErrorResume(e->{
            if(exchange.getResponse().isCommitted())return Mono.empty();exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] body=("{\"error\":\"Service unavailable\",\"requestId\":\""+requestId+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        });
    }
    public static void main(String[] args){SpringApplication.run(GatewayApplication.class,args);}
}
