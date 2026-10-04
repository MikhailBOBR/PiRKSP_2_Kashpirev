package ru.kashpirev;
import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
public class ReactiveSupplies {
    record Supply(int supplierId,String supplierName,String product,BigDecimal price,int quantity) {}
    record Valued(int supplierId,String product,BigDecimal value) {}
    static void log(String text) {System.out.printf("[%s] %s%n",Thread.currentThread().getName(),text);}
    static Observable<Valued> validate(Supply s) {
        return Observable.fromCallable(()->{
            if(s.price().signum()<0||s.quantity()<0||s.product().isBlank()) throw new IllegalArgumentException("Invalid "+s.product());
            return s;
        }).filter(v->v.quantity()>0)
          .map(v->new Valued(v.supplierId(),v.product(),v.price().multiply(BigDecimal.valueOf(v.quantity()))))
          .doOnNext(v->log("VALUED "+v))
          .onErrorResumeNext(error->{log("RECOVERED: "+error.getMessage());return Observable.empty();});
    }
    public static void main(String[] args) {
        log("Кашпирев Михаил Дмитриевич | ИКБО-11-23 | RxJava 3");
        List<Supply> data=List.of(new Supply(11,"Sever","SSD",new BigDecimal("7900"),8),new Supply(22,"Volna","Monitor",new BigDecimal("21900"),4),new Supply(11,"Sever","Router",new BigDecimal("4600"),0),new Supply(22,"Volna","BAD",new BigDecimal("-10"),2),new Supply(33,"Vector","Laptop",new BigDecimal("68500"),3),new Supply(11,"Sever","Dock",new BigDecimal("11900"),5));
        Observable.fromIterable(data).subscribeOn(Schedulers.io()).doOnNext(s->log("SOURCE "+s.product()))
          .observeOn(Schedulers.computation()).flatMap(ReactiveSupplies::validate)
          .groupBy(Valued::supplierId).flatMapSingle(group->group.map(Valued::value).reduce(BigDecimal.ZERO,BigDecimal::add).map(total->"supplier="+group.getKey()+" total="+total))
          .toList().blockingGet().stream().sorted().forEach(s->log("SUMMARY "+s));
        log("LIVE: новый объект каждые 180 ms; окна по 3 объекта");
        Observable.interval(180,TimeUnit.MILLISECONDS).take(data.size()).map(i->data.get(i.intValue()))
          .doOnNext(s->log("ARRIVAL "+s.product())).observeOn(Schedulers.io()).flatMap(ReactiveSupplies::validate)
          .buffer(3).blockingForEach(window->log("WINDOW count="+window.size()+" total="+window.stream().map(Valued::value).reduce(BigDecimal.ZERO,BigDecimal::add)));
        log("FLOWABLE: 500 событий / 1 ms, обработчик 12 ms, буфер 8, стратегия latest");
        AtomicInteger received=new AtomicInteger();
        Flowable.interval(1,TimeUnit.MILLISECONDS).take(500).doOnNext(i->{if(i%100==0)log("FAST SOURCE "+i);})
          .onBackpressureLatest().observeOn(Schedulers.single(),false,8)
          .doOnNext(i->{Thread.sleep(12);received.incrementAndGet();log("SLOW CONSUMER "+i);})
          .doOnError(e->log("Handled error "+e)).blockingSubscribe();
        log("BACKPRESSURE COMPLETE produced=500 received="+received.get()+" superseded="+(500-received.get()));
    }
}
