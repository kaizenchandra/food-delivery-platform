package dev.saffron;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class MealsTest {
 @Test void rejectsDuplicateChoicesBeforeDatabase(){var db=mock(JdbcTemplate.class);assertThrows(IllegalArgumentException.class,()->new Meals(db).quote(new Meals.Cart(1,1,List.of(1L,1L),"",false)));verifyNoInteractions(db);}
 @Test void canonicalFingerprint(){assertEquals(Meals.fingerprint(new Meals.Cart(1,2,List.of(3L,1L),"  mild  ",true)),Meals.fingerprint(new Meals.Cart(1,2,List.of(1L,3L),"mild",true)));assertNotEquals(Meals.fingerprint(new Meals.Cart(1,2,List.of(1L),"mild",true)),Meals.fingerprint(new Meals.Cart(1,2,List.of(1L),"mild",false)));}
 @Test void emptyOptionsReachRequiredChoiceValidation(){var db=mock(JdbcTemplate.class);when(db.queryForList(startsWith("select m."),eq(1L))).thenReturn(List.of(Map.of("price",28900L)));when(db.queryForList(startsWith("select * from menu_option_groups"),eq(1L))).thenReturn(List.of(Map.of("id",1L,"name","Portion","min_choices",1,"max_choices",1)));assertThrows(IllegalArgumentException.class,()->new Meals(db).quote(new Meals.Cart(1,1,null,"",false)));}
 @Test void authoritativePriceIncludesChoicesPerQuantity(){var db=mock(JdbcTemplate.class);when(db.queryForList(startsWith("select m."),eq(1L))).thenReturn(List.of(Map.of("price",28900L)));when(db.queryForList(startsWith("select * from menu_option_groups"),eq(1L))).thenReturn(List.of(Map.of("id",1L,"name","Portion","min_choices",1,"max_choices",1)));when(db.queryForList(startsWith("select o."),eq(1L))).thenReturn(List.of(Map.of("id",5L,"group_id",1L,"name","Large","group_name","Portion","extra_price",8000L,"available",true)));var quote=new Meals(db).quote(new Meals.Cart(1,2,List.of(5L),"Mild",true));assertEquals(77700,quote.total());assertEquals("Large",quote.selections().getFirst().get("name"));}
 @Test void rolesNeverComeFromUsername(){assertEquals("customer",Security.role(new TestingAuthenticationToken("admin",null,"ROLE_CUSTOMER")));assertFalse(Security.has(new TestingAuthenticationToken("restaurant",null,"ROLE_CUSTOMER"),"RESTAURANT"));}
}
