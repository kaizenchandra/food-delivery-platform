package dev.saffron;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AddressPricingTest {
 private Addresses.Input input(Double lat,Double lon,String street){return new Addresses.Input(" Home "," Example customer ",street,"","Bengaluru","560001",lat,lon);}
 @Test void normalizedAddress(){var address=Addresses.validate(input(12.97,77.59," 12 Demo Street "));assertEquals("Home",address.label());assertEquals("12 Demo Street",address.line1());}
 @Test void rejectMissingOrInvalidGps(){assertThrows(IllegalArgumentException.class,()->Addresses.validate(input(null,77.59,"12 Demo Street")));assertThrows(IllegalArgumentException.class,()->Addresses.validate(input(Double.NaN,77.59,"12 Demo Street")));assertThrows(IllegalArgumentException.class,()->Addresses.validate(input(91.0,77.59,"12 Demo Street")));}
 @Test void rejectsControlCharacters(){assertThrows(IllegalArgumentException.class,()->Addresses.validate(input(12.97,77.59,"Line one\nLine two")));}
 @Test void moneyRoundsHalfUpInPaise(){assertEquals(4172,Baskets.percentage(83430,500));assertEquals(5335,Baskets.percentage(106700,500));assertEquals(10000,Math.min(10000,Baskets.percentage(116700,1000)));}
 @Test void rejectsInvalidMoneyAndOverflow(){assertThrows(IllegalArgumentException.class,()->Baskets.percentage(-1,500));assertThrows(IllegalArgumentException.class,()->Baskets.percentage(100,10001));assertThrows(ArithmeticException.class,()->Baskets.percentage(Long.MAX_VALUE,500));}
 @Test void couponCodeIsCanonicalAndBounded(){assertEquals("HELLO10",Baskets.coupon(" hello10 "));assertEquals("",Baskets.coupon(null));assertThrows(IllegalArgumentException.class,()->Baskets.coupon("' OR 1=1"));}
}
