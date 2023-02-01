package org.camunda.community.messagecorrelator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class BpmnModelServiceBoundaryEventTest {

  private BpmnModelService bpmnModelService;

  @BeforeEach
  public void initService() {
    this.bpmnModelService =
        new BpmnModelService("src/test/resources/models/deliver-parcel-boundary-events.bpmn");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Event_12p30uq", "Event_1fws5ez", "Event_1infddy", "Event_1s50gmf"})
  public void testInterruptingBoundaryEventOnSubprocess(String startPosition) {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent(startPosition, "CANCELED");
    assertThat(messagesToBeSent.size()).isEqualTo(1);
    assertThat(messagesToBeSent.get(0)).isEqualTo("CANCELED");
  }

  @Test
  public void testInterruptingBoundaryEventOnTask() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("Event_0vxlyed", "CANCELED");
    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("INTRANSIT");
    assertThat(messagesToBeSent.get(1)).isEqualTo("CANCELED");
  }
}
