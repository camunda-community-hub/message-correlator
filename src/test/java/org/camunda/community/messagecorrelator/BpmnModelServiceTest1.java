package org.camunda.community.messagecorrelator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class BpmnModelServiceTest1 {

  private BpmnModelService bpmnModelService;

  @BeforeEach
  public void initService() {
    this.bpmnModelService = new BpmnModelService("src/test/resources/models/testprocess_1.bpmn");
  }

  // tests: expected message in subprocess
  @Test
  public void whenProcessStateSTATE9andMessageSTATE9ArrivesThenSendMessageSTATE9() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE9", "STATE9");

    assertThat(messagesToBeSent.size()).isEqualTo(1);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE9");
  }

  // tests: out-of-order message from subprocess to outer process (starting at 'normal' event)
  @Test
  public void
      whenProcessStateSTATE9andMessageSTATE2ArrivesThenSendMessagesSTATE9andSTATE1andSTATE2() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE9", "STATE2");

    assertThat(messagesToBeSent.size()).isEqualTo(3);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE9");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE1");
    assertThat(messagesToBeSent.get(2)).isEqualTo("STATE2");
  }

  // tests: out-of-order message from subprocess to outer process (starting at event behind
  // 'event-based Gateay')
  @Test
  public void whenEventAfterSubProcess() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE10", "STATE6");

    assertThat(messagesToBeSent.size()).isEqualTo(7);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE10");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE9");
    assertThat(messagesToBeSent.get(2)).isEqualTo("STATE1");
    assertThat(messagesToBeSent.get(3)).isEqualTo("STATE2");
    assertThat(messagesToBeSent.get(4)).isEqualTo("STATE8");
    assertThat(messagesToBeSent.get(5)).isEqualTo("STATE7");
    assertThat(messagesToBeSent.get(6)).isEqualTo("STATE6");
  }

  // tests: out-of-order message in subprocess with 1 jumps
  @Test
  public void whenProcessStateSTATE9andMessageSTATE10ArrivesThenSendMessagesSTATE9andSTATE10() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE9", "STATE10");

    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE9");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE10");
  }

  // tests: out-of-order message in subprocess with 2 jumps
  @Test
  public void
      whenProcessStateSTATE10andMessageSTATE1ArrivesThenSendMessagesSTATE10andSTATE9andSTATE1() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE10", "STATE1");

    assertThat(messagesToBeSent.size()).isEqualTo(3);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE10");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE9");
    assertThat(messagesToBeSent.get(2)).isEqualTo("STATE1");
  }

  // tests: expected message in outer process with 1 candidate
  @Test
  public void whenProcessStateSTATE8andMessageSTATE8ArrivesThenSendMessageSTATE8() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE8", "STATE8");

    assertThat(messagesToBeSent.size()).isEqualTo(1);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE8");
  }

  // tests: expected message in outer process with 3 candidates
  @Test
  public void whenProcessStateSTATE1andMessageSTATE1ArrivesThenSendMessageSTATE1() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE1", "STATE1");

    assertThat(messagesToBeSent.size()).isEqualTo(1);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE1");
  }

  // tests: out-of-order message in outer process with 1 jump
  @Test
  public void whenProcessStateSTATE1andMessageSTATE2ArrivesThenSendMessagesSTATE1andSTATE2() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE1", "STATE2");

    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE1");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE2");
  }

  // tests: out-of-order message in outer process with 3 jumps
  @Test
  public void
      whenProcessStateSTATE7andMessageSTATE4ArrivesThenSendMessagesSTATE7andSTATE2andSTATE8andSTATE4() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE7", "STATE4");

    assertThat(messagesToBeSent.size()).isEqualTo(3);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE7");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE1");
    assertThat(messagesToBeSent.get(2)).isEqualTo("STATE4");
  }

  // tests: out-of-order message in outer process with 5 jumps
  @Test
  public void
      whenProcessStateSTATE9andMessageSTATE6ArrivesThenSendMessagesSTATE9andSTATE1andSTATE2andSTATE8andSTATE7andSTATE6() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE9", "STATE6");

    assertThat(messagesToBeSent.size()).isEqualTo(6);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE9");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE1");
    assertThat(messagesToBeSent.get(2)).isEqualTo("STATE2");
    assertThat(messagesToBeSent.get(3)).isEqualTo("STATE8");
    assertThat(messagesToBeSent.get(4)).isEqualTo("STATE7");
    assertThat(messagesToBeSent.get(5)).isEqualTo("STATE6");
  }

  @Test
  public void testMessageMapping() {
    // STATE1
    List<String> actualSTATE1 = bpmnModelService.determineBpmnElementIdsForMessage("STATE1");
    List<String> expectedSTATE1 =
        List.of("Event_STATE1", "Event_STATE12", "Event_STATE1SUBPROCESS");
    assertThat(actualSTATE1.containsAll(expectedSTATE1)).isTrue();

    // STATE3
    List<String> actualSTATE3 = bpmnModelService.determineBpmnElementIdsForMessage("STATE3");
    List<String> expectedSTATE3 = List.of("Event_STATE3", "Event_STATE32", "Event_STATE33");
    assertThat(actualSTATE3.containsAll(expectedSTATE3)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Event_STATE1SUBPROCESS", "Event_STATE1SUBPROCESS", "Event_STATE10"})
  public void testInterruptingBoundaryEventOnSubprocess(String startPosition) {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            startPosition, "STATE11");
    assertThat(messagesToBeSent.size()).isEqualTo(1);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE11");
  }

  @Test
  public void testInterruptingBoundaryEventOnTask() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE1", "STATE11");
    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE1");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE11");
  }

  @Test
  public void whenProcessStateSTATE1andMessageSTATE6arrivesThenSendMessages() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE1", "STATE6");

    assertThat(messagesToBeSent.size()).isEqualTo(5);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE1");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE2");
    assertThat(messagesToBeSent.get(2)).isEqualTo("STATE8");
    assertThat(messagesToBeSent.get(3)).isEqualTo("STATE7");
    assertThat(messagesToBeSent.get(4)).isEqualTo("STATE6");
  }
}
