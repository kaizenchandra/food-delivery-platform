package dev.saffron;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
class RefundAllocationTest {
 @Test void fullRefundReversesEveryPaise(){assertArrayEquals(new long[]{28900,1445,3900},PaymentOperations.allocation(new long[]{28900,1445,3900},34245,34245));}
 @Test void partialRefundPolicyIsExactAndMonotone(){long[] original={13,3,4},before={0,0,0};for(long amount=0;amount<=20;amount++){var allocation=PaymentOperations.allocation(original,amount,20);assertEquals(amount,Arrays.stream(allocation).sum());for(int i=0;i<3;i++){assertTrue(allocation[i]>=before[i]);assertTrue(allocation[i]<=original[i]);}before=allocation;}}
 @Test void overRefundCannotAllocate(){assertThrows(IllegalArgumentException.class,()->PaymentOperations.allocation(new long[]{10,1,1},13,12));assertThrows(IllegalArgumentException.class,()->PaymentOperations.allocation(new long[]{-1,2},1,1));}
 @Test void fulfillmentCannotSkipPaymentState(){assertThrows(IllegalArgumentException.class,()->Workflow.next("AWAITING_PAYMENT","accept"));assertEquals("CANCELLED",Workflow.next("AWAITING_PAYMENT","cancel"));}
}
