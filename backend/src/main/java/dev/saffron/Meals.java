package dev.saffron;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@Service class Meals {
 final JdbcTemplate db; Meals(JdbcTemplate db){this.db=db;}
 record Cart(long item,int quantity,List<Long> options,String instructions,boolean cutlery){}
 record Quote(long unitPrice,long total,List<Map<String,Object>> selections,String instructions,boolean cutlery,String fingerprint,String itemName,long basePrice){}
 @Transactional(readOnly=true) public List<Map<String,Object>> groups(long item){var groups=db.queryForList("select * from menu_option_groups where menu_id=? order by id",item);for(var g:groups)g.put("options",db.queryForList("select id,name,extra_price,available from menu_options where group_id=? order by id",g.get("id")));return groups;}
 @Transactional public Quote quote(Cart c){
 if(c==null)throw new IllegalArgumentException("Cart required");
 var options=c.options==null?List.<Long>of():c.options;
 if(options.size()>10||new HashSet<>(options).size()!=options.size()||options.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Invalid or duplicate options");
 String instructions=c.instructions==null?"":c.instructions.strip();if(instructions.length()>300||instructions.codePoints().anyMatch(x->Character.isISOControl(x)))throw new IllegalArgumentException("Instructions must be plain text, at most 300 characters");
 var items=db.queryForList("select m.* from menu m join restaurants r on r.id=m.restaurant_id where m.id=? and m.available and r.online for share of m,r",c.item);
 if(items.isEmpty())throw new ResponseStatusException(CONFLICT,"Meal no longer available");
 var groups=db.queryForList("select * from menu_option_groups where menu_id=? order by id for share",c.item);
 var valid=db.queryForList("select o.*,g.name group_name from menu_options o join menu_option_groups g on g.id=o.group_id where g.menu_id=? order by o.id for share of o",c.item);
 List<Map<String,Object>> selections=new ArrayList<>();long unit=((Number)items.getFirst().get("price")).longValue();
 for(Long id:options){var option=valid.stream().filter(o->((Number)o.get("id")).longValue()==id&&Boolean.TRUE.equals(o.get("available"))).findFirst().orElseThrow(()->new IllegalArgumentException("Option unavailable or belongs to another meal"));unit=Math.addExact(unit,((Number)option.get("extra_price")).longValue());selections.add(Map.of("id",id,"name",option.get("name"),"group",option.get("group_name"),"extraPrice",option.get("extra_price"),"groupId",option.get("group_id")));}
 for(var group:groups){long count=selections.stream().filter(x->x.get("groupId").equals(group.get("id"))).count();if(count<((Number)group.get("min_choices")).intValue()||count>((Number)group.get("max_choices")).intValue())throw new IllegalArgumentException("Choose "+group.get("min_choices")+"–"+group.get("max_choices")+" for "+group.get("name"));}
 selections.sort(Comparator.comparingLong(x->((Number)x.get("id")).longValue()));
 String fingerprint=fingerprint(c);
 return new Quote(unit,Workflow.quote(unit,c.quantity),selections,instructions,c.cutlery,fingerprint,Objects.toString(items.getFirst().get("name"),"Meal"),((Number)items.getFirst().get("price")).longValue());
 }
 static String fingerprint(Cart c){
 if(c==null)throw new IllegalArgumentException("Cart required");
 var options=c.options==null?List.<Long>of():c.options;
 if(options.size()>10||options.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Invalid options");
 String instructions=c.instructions==null?"":c.instructions.strip();if(instructions.length()>300)throw new IllegalArgumentException("Instructions too long");
 return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(List.of(c.item,c.quantity,options.stream().sorted().toList(),instructions,c.cutlery));
 }

}
