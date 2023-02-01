package org.camunda.community.messagecorrelator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BpmnModelServiceComplexTest {

  private BpmnModelService bpmnModelService;

  @BeforeEach
  public void initService() {
    this.bpmnModelService =
        new BpmnModelService("src/test/resources/models/deliver-parcel-supercomplex.bpmn");
  }

  @Test
  public void testDirectMessageInComplexProcess() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_DeliveredPSComplex1", "DELIVEREDPS");

    assertThat(messagesToBeSent.size()).isEqualTo(1);
    assertThat(messagesToBeSent.get(0)).isEqualTo("DELIVEREDPS");
  }

  @Test
  public void testOutOfOrderMessageInComplexProcess() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_DeliveredPSComplex1", "CANCELED");

    assertThat(messagesToBeSent.size()).isEqualTo(4);
    assertThat(messagesToBeSent.get(0)).isEqualTo("DELIVEREDPS");
    assertThat(messagesToBeSent.get(1)).isEqualTo("PREADVICE");
    assertThat(messagesToBeSent.get(2)).isEqualTo("DELIVEREDPS");
    assertThat(messagesToBeSent.get(3)).isEqualTo("CANCELED");
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
  public void testStartingFromEventBeforeSubProcessToEventBehindSubProcess() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_FinalBeforeSub", "INDELIVERY");

    assertThat(messagesToBeSent.size()).isEqualTo(4);
    assertThat(messagesToBeSent.get(0)).isEqualTo("FINAL");
    assertThat(messagesToBeSent.get(1)).isEqualTo("INPICKUP");
    assertThat(messagesToBeSent.get(2)).isEqualTo("INTRANSIT");
    assertThat(messagesToBeSent.get(3)).isEqualTo("INDELIVERY");
  }

  @Test
  public void testStartingFromEventBeforeSubProcess2() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_CanceledComplex3", "NOTDELIVERED");

    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("CANCELED");
    assertThat(messagesToBeSent.get(1)).isEqualTo("NOTDELIVERED");
  }

  @Test
  public void testStartingFromEventBeforeSubProcessToEventBehindSubProcess2() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_CanceledComplex3", "DELIVEREDPS");

    assertThat(messagesToBeSent.size()).isEqualTo(3);
    assertThat(messagesToBeSent.get(0)).isEqualTo("CANCELED");
    assertThat(messagesToBeSent.get(1)).isEqualTo("NOTDELIVERED");
    assertThat(messagesToBeSent.get(2)).isEqualTo("DELIVEREDPS");
  }

  @Test
  public void testComplexPaths() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_INWAREHOUSE", "INDELIVERY");

    assertThat(messagesToBeSent.size()).isEqualTo(10);
    assertThat(messagesToBeSent.get(0)).isEqualTo("INWAREHOUSE");
    assertThat(messagesToBeSent.get(1)).isEqualTo("INTRANSIT");
    assertThat(messagesToBeSent.get(2)).isEqualTo("DELIVEREDPS");
    assertThat(messagesToBeSent.get(3)).isEqualTo("PREADVICE");
    assertThat(messagesToBeSent.get(4)).isEqualTo("DELIVEREDPS");
    assertThat(messagesToBeSent.get(5)).isEqualTo("CANCELED");
    assertThat(messagesToBeSent.get(6)).isEqualTo("CANCELED");
    assertThat(messagesToBeSent.get(7)).isEqualTo("NOTDELIVERED");
    assertThat(messagesToBeSent.get(8)).isEqualTo("DELIVEREDPS");
    assertThat(messagesToBeSent.get(9)).isEqualTo("INDELIVERY");
  }
}
