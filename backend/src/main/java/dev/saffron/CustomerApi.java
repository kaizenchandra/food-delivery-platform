package dev.saffron;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@RestController @RequestMapping("/api") class CustomerApi {
 private final Addresses addresses;private final Baskets baskets;private final JdbcTemplate db;private final Tracking tracking;
 CustomerApi(Addresses addresses,Baskets baskets,JdbcTemplate db,Tracking tracking){this.addresses=addresses;this.baskets=baskets;this.db=db;this.tracking=tracking;}
 private String customer(Authentication a){if(!Security.has(a,"CUSTOMER"))throw new ResponseStatusException(FORBIDDEN);return a.getName();}
 @GetMapping("/addresses")Object addresses(Authentication a){return addresses.list(customer(a));}
 @PostMapping("/addresses")Object create(Authentication a,@RequestBody Addresses.Input input){return addresses.create(customer(a),input);}
 record Edit(long version,Addresses.Input address){}
 @PostMapping("/addresses/{id}")Object edit(Authentication a,@PathVariable UUID id,@RequestBody Edit edit){return addresses.update(customer(a),id,edit.version,edit.address);}
 record Version(long version){}
 @PostMapping("/addresses/{id}/delete")void delete(Authentication a,@PathVariable UUID id,@RequestBody Version version){addresses.delete(customer(a),id,version.version);}
 @GetMapping("/cart")Object cart(Authentication a){return baskets.get(customer(a));}
 @PostMapping("/cart")Object save(Authentication a,@RequestBody Baskets.Change content){return baskets.put(customer(a),content);}
 @PostMapping("/cart/quote")Object quote(Authentication a,@RequestBody Version version){return baskets.quote(customer(a),version.version);}
 record Checkout(UUID quoteId){}
 @PostMapping("/cart/checkout")Object checkout(Authentication a,@RequestHeader("Idempotency-Key")String key,@RequestBody Checkout request){return baskets.checkout(customer(a),key,request.quoteId);}
 @GetMapping("/orders/{id}/lines")Object lines(Authentication a,@PathVariable UUID id){tracking.authorize(a,id);return db.queryForList("select id,item_id,quantity,unit_price,amount,snapshot::text snapshot from order_lines where order_id=? order by item_id,id",id);}
 @GetMapping("/orders/{id}/delivery-address")Object delivery(Authentication a,@PathVariable UUID id){var rows=db.queryForList("select delivery_address_snapshot::text snapshot from orders where id=? and (customer_name=? or (driver_name=? and state in ('ASSIGNED','PICKED_UP')))",id,Security.has(a,"CUSTOMER")?a.getName():"",Security.has(a,"DRIVER")?a.getName():"");if(rows.isEmpty())throw new ResponseStatusException(NOT_FOUND);return Map.of("address",Objects.toString(rows.getFirst().get("snapshot"),"{}"));}
}
