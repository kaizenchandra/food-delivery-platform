package dev.saffron;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component class ChannelWorker {
 private final ChannelOperations operations;private final ChannelGateway gateway;
 private final java.util.concurrent.atomic.AtomicBoolean running=new java.util.concurrent.atomic.AtomicBoolean();
 private final java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor();
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ChannelWorker.class);
 ChannelWorker(ChannelOperations operations,ChannelGateway gateway){this.operations=operations;this.gateway=gateway;}
 @Scheduled(fixedDelay=1000) public void tick(){if(running.compareAndSet(false,true))executor.execute(()->{try{for(var job:operations.claim())if(operations.permitted(job))operations.result(job,job.get("provider_id")==null?gateway.send(job):gateway.poll(job));}catch(Exception e){log.warn("Channel worker interrupted; durable leases will recover");}finally{running.set(false);}});}
 @jakarta.annotation.PreDestroy void stop(){executor.shutdownNow();}
}
