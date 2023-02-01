package org.camunda.community.messagecorrelator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class BpmnModelServiceTest {

  @Autowired private BpmnModelService bpmnModelService;

  @BeforeEach
  public void initService() {
    this.bpmnModelService =
        new BpmnModelService("src/test/resources/models/deliver-parcel_cut.bpmn");
  }

  // tests: expected message in subprocess
  @Test
  public void whenProcessStateINPICKUPandMessageINPICKUPArrivesThenSendMessageINPICKUP() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_INPICKUP", "INPICKUP");

    assertThat(messagesToBeSent.size()).isEqualTo(1);
    assertThat(messagesToBeSent.get(0)).isEqualTo("INPICKUP");
  }

  // tests: out-of-order message from subprocess to outer process (starting at 'normal' event)
  @Test
  public void
      whenProcessStateINPICKUPandMessageINDELIVERYArrivesThenSendMessagesINPICKUPandINTRANSITandINDELIVERY() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_INPICKUP", "INDELIVERY");

    assertThat(messagesToBeSent.size()).isEqualTo(3);
    assertThat(messagesToBeSent.get(0)).isEqualTo("INPICKUP");
    assertThat(messagesToBeSent.get(1)).isEqualTo("INTRANSIT");
    assertThat(messagesToBeSent.get(2)).isEqualTo("INDELIVERY");
  }

  // tests: out-of-order message from subprocess to outer process (starting at event behind
  // 'event-based Gateay')
  @Test
  public void whenEventAfterSubProcess() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_NOTPICKEDUP", "FINAL");

    assertThat(messagesToBeSent.size()).isEqualTo(7);
    assertThat(messagesToBeSent.get(0)).isEqualTo("NOTPICKEDUP");
    assertThat(messagesToBeSent.get(1)).isEqualTo("INPICKUP");
    assertThat(messagesToBeSent.get(2)).isEqualTo("INTRANSIT");
    assertThat(messagesToBeSent.get(3)).isEqualTo("INDELIVERY");
    assertThat(messagesToBeSent.get(4)).isEqualTo("NOTDELIVERED");
    assertThat(messagesToBeSent.get(5)).isEqualTo("INWAREHOUSE");
    assertThat(messagesToBeSent.get(6)).isEqualTo("FINAL");
  }

  // tests: out-of-order message in subprocess with 1 jumps
  @Test
  public void
      whenProcessStateINPICKUPandMessageNOTPICKEDUPArrivesThenSendMessagesINPICKUPandNOTPICKEDUP() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_INPICKUP", "NOTPICKEDUP");

    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("INPICKUP");
    assertThat(messagesToBeSent.get(1)).isEqualTo("NOTPICKEDUP");
  }

  // tests: out-of-order message in subprocess with 2 jumps
  @Test
  public void
      whenProcessStateNOTPICKEDUPandMessageINTRANSITArrivesThenSendMessagesNOTPICKEDUPandINPICKUPandINTRANSIT() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_NOTPICKEDUP", "INTRANSIT");

    assertThat(messagesToBeSent.size()).isEqualTo(3);
    assertThat(messagesToBeSent.get(0)).isEqualTo("NOTPICKEDUP");
    assertThat(messagesToBeSent.get(1)).isEqualTo("INPICKUP");
    assertThat(messagesToBeSent.get(2)).isEqualTo("INTRANSIT");
  }

  // tests: expected message in outer process with 1 candidate
  @Test
  public void
      whenProcessStateNOTDELIVEREDandMessageNOTDELIVEREDArrivesThenSendMessageNOTDELIVERED() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_NOTDELIVERED", "NOTDELIVERED");

    assertThat(messagesToBeSent.size()).isEqualTo(1);
    assertThat(messagesToBeSent.get(0)).isEqualTo("NOTDELIVERED");
  }

  // tests: expected message in outer process with 3 candidates
  @Test
  public void whenProcessStateINTRANSITandMessageINTRANSITArrivesThenSendMessageINTRANSIT() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_INTRANSIT", "INTRANSIT");

    assertThat(messagesToBeSent.size()).isEqualTo(1);
    assertThat(messagesToBeSent.get(0)).isEqualTo("INTRANSIT");
  }

  // tests: out-of-order message in outer process with 1 jump
  @Test
  public void
      whenProcessStateINTRANSITandMessageINDELIVERYArrivesThenSendMessagesINTRANSITandINDELIVERY() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_INTRANSIT", "INDELIVERY");

    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("INTRANSIT");
    assertThat(messagesToBeSent.get(1)).isEqualTo("INDELIVERY");
  }

  // tests: out-of-order message in outer process with 3 jumps
  @Test
  public void
      whenProcessStateINWAREHOUSEandMessageDELIVEREDPSArrivesThenSendMessagesINWAREHOUSEandINDELIVERYandNOTDELIVEREDandDELIVEREDPS() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_INWAREHOUSE", "DELIVEREDPS");

    assertThat(messagesToBeSent.size()).isEqualTo(4);
    assertThat(messagesToBeSent.get(0)).isEqualTo("INWAREHOUSE");
    assertThat(messagesToBeSent.get(1)).isEqualTo("INDELIVERY");
    assertThat(messagesToBeSent.get(2)).isEqualTo("NOTDELIVERED");
    assertThat(messagesToBeSent.get(3)).isEqualTo("DELIVEREDPS");
  }

  // tests: out-of-order message in outer process with 5 jumps
  @Test
  public void
      whenProcessStateINPICKUPandMessageFINALArrivesThenSendMessagesINPICKUPandINTRANSITandINDELIVERYandNOTDELIVEREDandINWAREHOUSEandFINAL() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_INPICKUP", "FINAL");

    assertThat(messagesToBeSent.size()).isEqualTo(6);
    assertThat(messagesToBeSent.get(0)).isEqualTo("INPICKUP");
    assertThat(messagesToBeSent.get(1)).isEqualTo("INTRANSIT");
    assertThat(messagesToBeSent.get(2)).isEqualTo("INDELIVERY");
    assertThat(messagesToBeSent.get(3)).isEqualTo("NOTDELIVERED");
    assertThat(messagesToBeSent.get(4)).isEqualTo("INWAREHOUSE");
    assertThat(messagesToBeSent.get(5)).isEqualTo("FINAL");
  }

  @Test
  public void testMessageMapping() {
    // INTRANSIT
    List<String> actualINTRANSIT = bpmnModelService.determineBpmnElementIdsForMessage("INTRANSIT");
    List<String> expectedINTRANSIT =
        List.of("Event_INTRANSIT", "Event_INTRANSIT2", "Event_INTRANSITSUBPROCESS");
    assertThat(actualINTRANSIT.containsAll(expectedINTRANSIT)).isTrue();

    // DELIVERED
    List<String> actualDELIVERED = bpmnModelService.determineBpmnElementIdsForMessage("DELIVERED");
    List<String> expectedDELIVERED =
        List.of("Event_DELIVERED", "Event_DELIVERED2", "Event_DELIVERED3");
    assertThat(actualDELIVERED.containsAll(expectedDELIVERED)).isTrue();
  }
}
