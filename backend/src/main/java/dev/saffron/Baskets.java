package dev.saffron;

import java.util.*;
import java.time.Instant;
import java.sql.Timestamp;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.MapperFeature;
import static org.springframework.http.HttpStatus.*;
@Service class Baskets {
 private final JdbcTemplate db;private final Meals meals;private final Addresses addresses;private final Marketplace marketplace;private final PaymentOperations payments;
 private final JsonMapper json=JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).build();
 Baskets(JdbcTemplate db,Meals meals,Addresses addresses,Marketplace marketplace,PaymentOperations payments){this.payments=payments;this.db=db;this.meals=meals;this.addresses=addresses;this.marketplace=marketplace;}
 record Content(List<Meals.Cart> lines,UUID addressId,String coupon,boolean cutlery){}
 record State(long version,Content content,Map<Long,String> itemNames){}
 record Change(long version,Content content){}
 record Line(long item,String name,int quantity,long unitPrice,long amount,List<Map<String,Object>> selections,String instructions){}
 record Pricing(long restaurantId,List<Line> lines,long subtotal,long discount,long tax,long delivery,long total,String coupon,boolean cutlery,Addresses.Snapshot address,String policyVersion){}
 record Quote(UUID id,long cartVersion,Instant expiresAt,Pricing pricing){}
 private static final Content EMPTY=new Content(List.of(),null,"",false);
 private String encode(Object value){return json.writeValueAsString(value);}
 static long percentage(long amount,int bps){if(amount<0||bps<0||bps>10000)throw new IllegalArgumentException("Invalid pricing parameters");return Math.floorDiv(Math.addExact(Math.multiplyExact(amount,bps),5000),10000);}
 static String coupon(String value){String code=value==null?"":value.strip().toUpperCase(Locale.ROOT);if(!code.matches("[A-Z0-9-]{0,30}"))throw new IllegalArgumentException("Invalid coupon code");return code;}
 private Content valid(Content c){if(c==null||c.lines==null||c.lines.size()>20||c.lines.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Cart must have at most 20 lines");int count=0;for(var line:c.lines){if(line.quantity()<1||line.quantity()>20)throw new IllegalArgumentException("Quantity must be 1–20");count=Math.addExact(count,line.quantity());Meals.fingerprint(line);}if(count>100)throw new IllegalArgumentException("At most 100 items per cart");return new Content(List.copyOf(c.lines),c.addressId,coupon(c.coupon),c.cutlery);}
 private State locked(String user,boolean lock){db.update("insert into customer_carts(customer_subject,payload) values(?,?) on conflict do nothing",user,encode(EMPTY));var row=db.queryForMap("select version,payload from customer_carts where customer_subject=?"+(lock?" for update":""),user);Content content=json.readValue(row.get("payload").toString(),Content.class);return state(((Number)row.get("version")).longValue(),content);}
 private State state(long version,Content content){Map<Long,String> names=new HashMap<>();if(!content.lines.isEmpty()){var ids=content.lines.stream().map(Meals.Cart::item).distinct().toList();for(var row:db.queryForList("select id,name from menu where id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",ids.toArray()))names.put(((Number)row.get("id")).longValue(),row.get("name").toString());}return new State(version,content,Map.copyOf(names));}
 @Transactional public State get(String user){return locked(user,false);}
 @Transactional public State put(String user,Change change){if(change==null)throw new IllegalArgumentException("Cart required");Content c=valid(change.content);State current=locked(user,true);if(current.version!=change.version)throw new ResponseStatusException(CONFLICT,"Cart changed in another tab; refresh before editing");validateRestaurant(c.lines);if(c.addressId!=null)addresses.owned(user,c.addressId,false);db.update("update customer_carts set payload=?,version=version+1,updated_at=now() where customer_subject=?",encode(c),user);return state(current.version+1,c);}
 private long validateRestaurant(List<Meals.Cart> lines){Long restaurant=null;for(var line:lines){var rows=db.queryForList("select restaurant_id from menu where id=?",line.item());if(rows.isEmpty())throw new IllegalArgumentException("Unknown menu item");long id=((Number)rows.getFirst().get("restaurant_id")).longValue();if(restaurant!=null&&restaurant!=id)throw new ResponseStatusException(CONFLICT,"A cart can contain food from one restaurant only");restaurant=id;}return restaurant==null?0:restaurant;}
 private Pricing price(String user,Content c,boolean redeemLock){if(c.lines.isEmpty()||c.addressId==null)throw new IllegalArgumentException("Select items and a saved delivery address");long restaurant=validateRestaurant(c.lines);var address=addresses.owned(user,c.addressId,true);Boolean serviceable=db.queryForObject("select online and ST_DWithin(location,ST_SetSRID(ST_Point(?,?),4326)::geography,8000) from restaurants where id=?",Boolean.class,address.longitude(),address.latitude(),restaurant);if(!Boolean.TRUE.equals(serviceable))throw new ResponseStatusException(CONFLICT,"Restaurant does not serve this address or is offline");
 List<Line> lines=new ArrayList<>();long subtotal=0;
 // Consistent catalog lock ordering across concurrent baskets.
 var sorted=new ArrayList<>(c.lines);sorted.sort(Comparator.comparingLong(Meals.Cart::item));
 for(var line:sorted){var q=meals.quote(line);long amount=Math.multiplyExact(q.unitPrice(),line.quantity());subtotal=Math.addExact(subtotal,amount);lines.add(new Line(line.item(),q.itemName(),line.quantity(),q.unitPrice(),amount,q.selections(),q.instructions()));}
 long discount=0;
 if(!c.coupon.isBlank()){var rows=db.queryForList("select * from coupons where code=? and enabled and starts_at<=now() and ends_at>now()"+(redeemLock?" for update":""),c.coupon);if(rows.isEmpty())throw new ResponseStatusException(CONFLICT,"Coupon is invalid or expired");var promo=rows.getFirst();if(subtotal<((Number)promo.get("minimum_subtotal")).longValue()||((Number)promo.get("redeemed")).intValue()>=((Number)promo.get("max_redemptions")).intValue()||db.queryForObject("select count(*) from coupon_redemptions where coupon_code=? and customer_subject=?",Integer.class,c.coupon,user)>0)throw new ResponseStatusException(CONFLICT,"Coupon limit reached or minimum spend not met");discount=Math.min(((Number)promo.get("max_discount")).longValue(),percentage(subtotal,((Number)promo.get("discount_bps")).intValue()));}
 long tax=percentage(subtotal-discount,500),delivery=3900,total=Math.addExact(Math.addExact(subtotal-discount,tax),delivery);
 return new Pricing(restaurant,List.copyOf(lines),subtotal,discount,tax,delivery,total,c.coupon,c.cutlery,address,"LOCAL-DEMO-TAX-5PCT-V1");
 }
 @Transactional public Quote quote(String user,long expectedVersion){State cart=locked(user,true);if(cart.version!=expectedVersion)throw new ResponseStatusException(CONFLICT,"Cart changed; refresh before quoting");Pricing pricing=price(user,valid(cart.content),false);UUID id=UUID.randomUUID();Instant expiry=Instant.now().plusSeconds(600);db.update("insert into checkout_quotes(id,customer_subject,cart_version,payload,expires_at) values(?,?,?,?,?)",id,user,cart.version,encode(pricing),Timestamp.from(expiry));return new Quote(id,cart.version,expiry,pricing);}
 @Transactional public Map<String,Object> checkout(String user,String key,UUID quoteId){if(key==null||!key.matches("[a-zA-Z0-9-]{8,100}")||quoteId==null)throw new IllegalArgumentException("Quote and idempotency key required");db.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,user+":"+key);
 String fingerprint="basket-quote:"+quoteId;var previous=db.queryForList("select * from orders where customer_name=? and idem=?",user,key);if(!previous.isEmpty()){if(!fingerprint.equals(previous.getFirst().get("fingerprint")))throw new ResponseStatusException(CONFLICT,"Idempotency key belongs to another checkout");return previous.getFirst();}
 var quotes=db.queryForList("select * from checkout_quotes where id=? and customer_subject=?",quoteId,user);if(quotes.isEmpty())throw new ResponseStatusException(NOT_FOUND,"Quote not found");
 State cart=locked(user,true);var consumed=db.queryForList("select * from orders where checkout_quote_id=? and customer_name=?",quoteId,user);if(!consumed.isEmpty())throw new ResponseStatusException(CONFLICT,"Quote already consumed; retry the original checkout key");var stored=quotes.getFirst();if(((Timestamp)stored.get("expires_at")).toInstant().isBefore(Instant.now())||((Number)stored.get("cart_version")).longValue()!=cart.version)throw new ResponseStatusException(CONFLICT,"Quote expired or cart changed; review a new quote");
 Pricing pricing=price(user,valid(cart.content),true);if(!encode(pricing).equals(stored.get("payload")))throw new ResponseStatusException(CONFLICT,"Menu, price or address changed; review a new quote");
 UUID id=UUID.randomUUID();Line first=pricing.lines.getFirst();
 db.update("insert into orders(id,customer_name,restaurant_id,item_id,quantity,total,state,payment,otp,idem,fingerprint,checkout_quote_id,delivery_address_snapshot,delivery_latitude,delivery_longitude,customization,pricing_version,item_subtotal,discount_amount,tax_amount,delivery_fee) values(?,?,?,?,?,?,'AWAITING_PAYMENT','PENDING','246810',?,?,?,?::jsonb,?,?,?::jsonb,?,?,?,?,?)",id,user,pricing.restaurantId,first.item,first.quantity,pricing.total,key,fingerprint,quoteId,encode(pricing.address),pricing.address.latitude(),pricing.address.longitude(),encode(Map.of("lines",pricing.lines,"cutlery",pricing.cutlery,"subtotal",pricing.subtotal,"discount",pricing.discount,"tax",pricing.tax,"delivery",pricing.delivery,"total",pricing.total,"policyVersion",pricing.policyVersion)),pricing.policyVersion,pricing.subtotal,pricing.discount,pricing.tax,pricing.delivery);
 for(Line line:pricing.lines)db.update("insert into order_lines(id,order_id,item_id,quantity,unit_price,amount,snapshot) values(?,?,?,?,?,?,?::jsonb)",UUID.randomUUID(),id,line.item,line.quantity,line.unitPrice,line.amount,encode(line));
 if(!pricing.coupon.isBlank()){if(db.update("update coupons set redeemed=redeemed+1 where code=? and redeemed<max_redemptions",pricing.coupon)!=1)throw new ResponseStatusException(CONFLICT,"Coupon exhausted");db.update("insert into coupon_redemptions(coupon_code,customer_subject,order_id,amount) values(?,?,?,?)",pricing.coupon,user,id,pricing.discount);}
 payments.register(id,pricing.total);db.update("insert into audit(actor,action,resource) values(?,'BASKET_CHECKOUT',?)",user,id.toString());marketplace.event(id,"CheckoutStarted");db.update("update customer_carts set version=version+1,payload=?,updated_at=now() where customer_subject=?",encode(EMPTY),user);return db.queryForMap("select * from orders where id=?",id);
 }
}
