package dev.saffron;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
class RoadRouterTest {
 private final RoadRouter.Point start=new RoadRouter.Point(77.5946,12.9716),end=new RoadRouter.Point(77.606,12.978);
 @Test void noProviderIsClearlySimulated(){var route=new RoadRouter("",true).route(start,end);assertEquals("SIMULATOR",route.mode());assertEquals("NOT_CONFIGURED",route.reason());assertEquals(2,route.geometry().size());assertTrue(route.distanceMeters()>1000);}
 @Test void invalidGeometryCannotReachMap(){assertThrows(IllegalArgumentException.class,()->new RoadRouter.Point(Double.NaN,12));assertThrows(IllegalArgumentException.class,()->new RoadRouter.Point(181,12));}
 @Test void rejectsArbitraryOrInsecureProviderUrls(){assertThrows(IllegalStateException.class,()->new RoadRouter("http://example.org",false));assertThrows(IllegalStateException.class,()->new RoadRouter("http://example.org",true));assertThrows(IllegalStateException.class,()->new RoadRouter("https://user:pass@example.org",false));}
 @Test void rejectsNoRouteAndOversize(){assertThrows(IllegalArgumentException.class,()->RoadRouter.parse("{\"code\":\"NoRoute\"}".getBytes(),start,end));assertThrows(IllegalArgumentException.class,()->RoadRouter.parse(new byte[1048577],start,end));}
 @Test void providerOutageFallsBackAndBacksOff()throws Exception{var count=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",exchange->{count.incrementAndGet();exchange.sendResponseHeaders(503,-1);exchange.close();});server.start();try{var router=new RoadRouter("http://127.0.0.1:"+server.getAddress().getPort(),true);assertEquals("PROVIDER_UNAVAILABLE",router.route(start,end).reason());assertEquals("PROVIDER_BACKOFF",router.route(start,end).reason());assertEquals(1,count.get());}finally{server.stop(0);}}
 @Test void malformedProviderDataFallsBack()throws Exception{var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",exchange->{byte[] body="{invalid".getBytes();exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();try{assertEquals("SIMULATOR",new RoadRouter("http://127.0.0.1:"+server.getAddress().getPort(),true).route(start,end).mode());}finally{server.stop(0);}}
}
