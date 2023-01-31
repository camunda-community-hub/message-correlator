package org.camunda.community.messagecorrelator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BpmnModelServiceTest2 {

  private BpmnModelService bpmnModelService;

  @BeforeEach
  public void initService() {
    this.bpmnModelService = new BpmnModelService("src/test/resources/models/testprocess_2.bpmn");
  }

  @Test
  public void testDirectMessageInTestProcess2() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE4", "STATE4");

    assertThat(messagesToBeSent.size()).isEqualTo(1);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE4");
  }

  @Test
  public void testStartingFromEventBeforeSubProcess() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE6BeforeSub", "STATE1");

    assertThat(messagesToBeSent.size()).isEqualTo(3);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE6");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE9");
    assertThat(messagesToBeSent.get(2)).isEqualTo("STATE1");
  }

  @Test
  public void testStartingFromEventBeforeSubProcess2() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE6BeforeSub2", "STATE1");

    assertThat(messagesToBeSent.size()).isEqualTo(3);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE6");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE9");
    assertThat(messagesToBeSent.get(2)).isEqualTo("STATE1");
  }

  @Test
  public void testStartingBeforeEventWithMultipleIncomingFlows() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE1", "STATE2");
    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE1");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE2");

    messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE12", "STATE2");
    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE1");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE2");
  }

  @Test
  public void testSubprocessWithMultipleEndEvents() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE1SUBPROCESS", "STATE2");
    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE1");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE2");

    messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE1SUBPROCESS2", "STATE2");
    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE1");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE2");
  }

  @Test
  public void testSubprocessWithEndEventWithMultipleSequenceFlows() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE4", "STATE3");
    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE4");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE3");

    messagesToBeSent =
        bpmnModelService.determineMessagesFromBpmnElementIdToTargetMessage(
            "Event_STATE7", "STATE3");
    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("STATE7");
    assertThat(messagesToBeSent.get(1)).isEqualTo("STATE3");
  }
}
