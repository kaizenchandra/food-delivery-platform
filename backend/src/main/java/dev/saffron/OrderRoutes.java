package dev.saffron;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

@Service class OrderRoutes {
 record View(UUID orderId,long orderVersion,String phase,String originKind,String destinationKind,int etaMinutes,RoadRouter.Route route){}
 record Entry(View value,long expires){}
 private final RoadRouter router;private final Tracking tracking;private final JdbcTemplate db;
 private final ConcurrentHashMap<String,Entry> cache=new ConcurrentHashMap<>();
 OrderRoutes(RoadRouter router,Tracking tracking,JdbcTemplate db){this.router=router;this.tracking=tracking;this.db=db;}
 View forOrder(Authentication authentication,UUID id,boolean simulate){
 tracking.authorize(authentication,id);
 var row=db.queryForMap("select o.state,o.version,o.latitude,o.longitude,o.delivery_latitude,o.delivery_longitude,o.delivery_address_snapshot is not null has_saved_address,ST_Y(r.location::geometry) restaurant_latitude,ST_X(r.location::geometry) restaurant_longitude from orders o join restaurants r on r.id=o.restaurant_id where o.id=?",id);
 if(Boolean.TRUE.equals(row.get("has_saved_address"))&&!Security.has(authentication,"CUSTOMER")&&!Security.has(authentication,"DRIVER"))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
 String state=row.get("state").toString();long version=((Number)row.get("version")).longValue();String key=id+":"+version+":"+simulate;
 var existing=cache.get(key);if(existing!=null&&existing.expires>System.currentTimeMillis())return existing.value;
 boolean pickup=state.equals("ASSIGNED");boolean gps=row.get("latitude")!=null&&Set.of("ASSIGNED","PICKED_UP").contains(state);
 var origin=gps?new RoadRouter.Point(num(row,"longitude"),num(row,"latitude")):new RoadRouter.Point(num(row,"restaurant_longitude"),num(row,"restaurant_latitude"));
 var target=pickup?new RoadRouter.Point(num(row,"restaurant_longitude"),num(row,"restaurant_latitude")):new RoadRouter.Point(num(row,"delivery_longitude"),num(row,"delivery_latitude"));
 var route=Set.of("CANCELLED","DELIVERED").contains(state)?RoadRouter.simulator(target,target,"ORDER_TERMINAL"):simulate?RoadRouter.simulator(origin,target,"USER_SELECTED"):router.route(origin,target);
 int allowance=switch(state){case "PLACED"->20;case "ACCEPTED"->15;case "PREPARING"->10;case "READY"->5;default->0;};
 int eta=Set.of("CANCELLED","DELIVERED").contains(state)?0:Math.max(1,(int)Math.ceil(route.durationSeconds()/60)+allowance);
 var view=new View(id,version,pickup?"TO_PICKUP":"TO_CUSTOMER",gps?"PARTNER_GPS":"RESTAURANT_ESTIMATE",pickup?"RESTAURANT":Boolean.TRUE.equals(row.get("has_saved_address"))?"SAVED_CUSTOMER_ADDRESS":"DEMO_CUSTOMER_ADDRESS",eta,route);
 if(cache.size()>=256)cache.clear();cache.put(key,new Entry(view,System.currentTimeMillis()+(route.mode().equals("ROAD")?15_000:5_000)));
 return view;
 }
 private static double num(Map<String,Object> row,String key){return ((Number)row.get(key)).doubleValue();}
}
@RestController class OrderRouteApi {
 private final OrderRoutes routes;OrderRouteApi(OrderRoutes routes){this.routes=routes;}
 @GetMapping("/api/orders/{id}/route")OrderRoutes.View route(Authentication authentication,@PathVariable UUID id,@RequestParam(defaultValue="auto")String mode){if(!Set.of("auto","simulator").contains(mode))throw new IllegalArgumentException("Unknown routing mode");return routes.forOrder(authentication,id,mode.equals("simulator"));}
}
