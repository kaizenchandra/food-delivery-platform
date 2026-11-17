package dev.saffron;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WorkflowTest {
 @Test void lifecycle(){String s="PLACED";for(String a:new String[]{"accept","prepare","ready","assign","pickup","deliver"})s=Workflow.next(s,a);assertEquals("DELIVERED",s);}
 @Test void rejectsSkip(){assertThrows(IllegalArgumentException.class,()->Workflow.next("PLACED","deliver"));}
 @Test void noRefundAfterPickup(){assertThrows(IllegalArgumentException.class,()->Workflow.next("PICKED_UP","cancel"));}
 @Test void release(){assertEquals("READY",Workflow.next("ASSIGNED","release"));}
 @Test void pricing(){assertEquals(61700,Workflow.quote(28900,2));assertThrows(IllegalArgumentException.class,()->Workflow.quote(1,0));assertThrows(ArithmeticException.class,()->Workflow.quote(Long.MAX_VALUE,2));}
}
