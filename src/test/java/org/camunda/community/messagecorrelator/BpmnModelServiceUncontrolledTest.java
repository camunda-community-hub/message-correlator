package org.camunda.community.messagecorrelator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BpmnModelServiceUncontrolledTest {

  private BpmnModelService bpmnModelService;

  @BeforeEach
  public void initService() {
    this.bpmnModelService =
        new BpmnModelService("src/test/resources/models/deliver-parcel-uncontrolled-flow.bpmn");
  }

  @Test
  public void testDirectMessageInProcessWithUncontrolledFlow() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_DELIVEREDPS", "DELIVEREDPS");

    assertThat(messagesToBeSent.size()).isEqualTo(1);
    assertThat(messagesToBeSent.get(0)).isEqualTo("DELIVEREDPS");
  }

  @Test
  public void testStartingFromEventBeforeSubProcess() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_FinalBeforeSub", "INTRANSIT");

    assertThat(messagesToBeSent.size()).isEqualTo(3);
    assertThat(messagesToBeSent.get(0)).isEqualTo("FINAL");
    assertThat(messagesToBeSent.get(1)).isEqualTo("INPICKUP");
    assertThat(messagesToBeSent.get(2)).isEqualTo("INTRANSIT");
  }

  @Test
  public void testStartingFromEventBeforeSubProcess2() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_FinalBeforeSub2", "INTRANSIT");

    assertThat(messagesToBeSent.size()).isEqualTo(3);
    assertThat(messagesToBeSent.get(0)).isEqualTo("FINAL");
    assertThat(messagesToBeSent.get(1)).isEqualTo("INPICKUP");
    assertThat(messagesToBeSent.get(2)).isEqualTo("INTRANSIT");
  }

  @Test
  public void testStartingBeforeEventWithMultipleIncomingFlows() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_INTRANSIT", "INDELIVERY");
    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("INTRANSIT");
    assertThat(messagesToBeSent.get(1)).isEqualTo("INDELIVERY");

    messagesToBeSent = bpmnModelService.determineMessagesToBeSent("Event_INTRANSIT2", "INDELIVERY");
    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("INTRANSIT");
    assertThat(messagesToBeSent.get(1)).isEqualTo("INDELIVERY");
  }

  @Test
  public void testSubprocessWithMultipleEndEvents() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_INTRANSITSUBPROCESS", "INDELIVERY");
    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("INTRANSIT");
    assertThat(messagesToBeSent.get(1)).isEqualTo("INDELIVERY");

    messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_INTRANSITSUBPROCESS2", "INDELIVERY");
    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("INTRANSIT");
    assertThat(messagesToBeSent.get(1)).isEqualTo("INDELIVERY");
  }

  @Test
  public void testSubprocessWithEndEventWithMultipleSequenceFlows() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_DELIVEREDPS", "DELIVERED");
    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("DELIVEREDPS");
    assertThat(messagesToBeSent.get(1)).isEqualTo("DELIVERED");

    messagesToBeSent = bpmnModelService.determineMessagesToBeSent("Event_INWAREHOUSE", "DELIVERED");
    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("INWAREHOUSE");
    assertThat(messagesToBeSent.get(1)).isEqualTo("DELIVERED");
  }
}
