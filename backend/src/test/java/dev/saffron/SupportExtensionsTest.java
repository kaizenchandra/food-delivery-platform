package dev.saffron;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;
class SupportExtensionsTest {
 @Test void deadlinesAreBounded(){assertEquals(Duration.ofHours(24),Aftercare.escalationDuration("PT24H"));assertThrows(IllegalArgumentException.class,()->Aftercare.escalationDuration("PT1S"));assertThrows(IllegalArgumentException.class,()->Aftercare.escalationDuration("P8D"));}
 @Test void financialNotificationsDoNotClaimPrematureRefunds(){assertTrue(NotificationInbox.supportTitle("SUPPORT_REFUND").contains("pending"));assertTrue(NotificationInbox.orderTitle("RefundCompleted").contains("confirmed"));assertNull(NotificationInbox.orderTitle("UntrustedProviderText"));}
 @Test void supportTemplatesRejectUnknownTypes(){assertThrows(IllegalArgumentException.class,()->NotificationInbox.supportTitle("<script>"));}
 @Test void racingEscalationDoesNotPreventTheNextTicket(){var service=mock(Aftercare.class);var a=UUID.randomUUID();var b=UUID.randomUUID();when(service.overdue()).thenReturn(List.of(Map.of("id",a,"version",0L),Map.of("id",b,"version",1L)));when(service.escalate(eq("system"),eq(true),eq(a),eq(0L),anyString(),eq(true))).thenThrow(new ResponseStatusException(CONFLICT));new SupportEscalationWorker(service).tick();verify(service).escalate(eq("system"),eq(true),eq(b),eq(1L),anyString(),eq(true));}
 @Test void workerDoesNotHideUnexpectedAuthorizationFailures(){var service=mock(Aftercare.class);var id=UUID.randomUUID();when(service.overdue()).thenReturn(List.of(Map.of("id",id,"version",0L)));when(service.escalate(eq("system"),eq(true),eq(id),eq(0L),anyString(),eq(true))).thenThrow(new ResponseStatusException(FORBIDDEN));assertThrows(ResponseStatusException.class,()->new SupportEscalationWorker(service).tick());}
}
