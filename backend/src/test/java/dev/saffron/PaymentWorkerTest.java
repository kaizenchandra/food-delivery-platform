package dev.saffron;

import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.mockito.Mockito.*;

class PaymentWorkerTest {
 @Test void quarantinedSnapshotCannotClearIssuesThroughBalanceReconciliation() {
  var gateway=mock(PaymentGateway.class);var operations=mock(PaymentOperations.class);UUID payment=UUID.randomUUID();
  when(gateway.enabled()).thenReturn(true);
  when(operations.claimPayments()).thenReturn(List.of(Map.of("id",payment,"session_id","cs_test_invalid")));
  var snapshot=JsonMapper.builder().build().readTree("{\"id\":\"cs_test_invalid\",\"status\":\"complete\",\"payment_status\":\"paid\",\"payment_intent\":\"pi_untrusted\"}");
  when(gateway.session("cs_test_invalid")).thenReturn(snapshot);
  when(operations.session(payment,snapshot,"lookup")).thenReturn(false);
  new PaymentWorker(gateway,operations,true).tick();
  verify(operations).paymentError(payment);
  verify(operations,never()).report(any(),any(),any());
  verify(gateway,never()).refunds(anyString());
 }
 @Test void completedUnpaidSessionRemainsReconciliableAfterCancellation() {
  var gateway=mock(PaymentGateway.class);var operations=mock(PaymentOperations.class);UUID payment=UUID.randomUUID();
  when(gateway.enabled()).thenReturn(true);
  when(operations.claimPayments()).thenReturn(List.of(Map.of("id",payment,"session_id","cs_test_pending","cancel_requested",true)));
  var snapshot=JsonMapper.builder().build().readTree("{\"id\":\"cs_test_pending\",\"status\":\"complete\",\"payment_status\":\"unpaid\"}");
  when(gateway.session("cs_test_pending")).thenReturn(snapshot);
  when(operations.session(payment,snapshot,"lookup")).thenReturn(true);
  new PaymentWorker(gateway,operations,true).tick();
  verify(gateway,never()).expire(anyString(),any());
  verify(operations).report(payment,snapshot,null);
 }
}
