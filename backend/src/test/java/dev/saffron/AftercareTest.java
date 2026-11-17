package dev.saffron;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AftercareTest {
 @Test void ratingsAreBounded(){for(int n:new int[]{0,6,-1})assertThrows(IllegalArgumentException.class,()->Aftercare.reviewInput(new Aftercare.Review(n,"Meal")));Aftercare.reviewInput(new Aftercare.Review(5,"Good meal"));}
 @Test void rejectsEmptyAndOversizedText(){assertThrows(IllegalArgumentException.class,()->Aftercare.text("  ",3000));assertThrows(IllegalArgumentException.class,()->Aftercare.text("x".repeat(2001),2000));assertThrows(IllegalArgumentException.class,()->Aftercare.reviewInput(null));}
 @Test void acceptsPlainTextButRejectsControlCharacters(){assertEquals("<script>alert('x')</script>",Aftercare.text("<script>alert('x')</script>",2000));assertEquals("A\nB\tC",Aftercare.text("A\nB\tC",2000));assertThrows(IllegalArgumentException.class,()->Aftercare.text("bad\u0000text",2000));}
 @Test void validatesSupportCategoryAndMoney(){Aftercare.ticketInput(new Aftercare.Ticket("MISSING","One bowl missing",5000));assertThrows(IllegalArgumentException.class,()->Aftercare.ticketInput(new Aftercare.Ticket("OTHER","Details",-1)));assertThrows(IllegalArgumentException.class,()->Aftercare.ticketInput(new Aftercare.Ticket("FORGED","Details",0)));}
}
