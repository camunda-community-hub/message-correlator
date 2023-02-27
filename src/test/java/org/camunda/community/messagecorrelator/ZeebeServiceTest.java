package org.camunda.community.messagecorrelator;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.extension.testcontainer.ZeebeProcessTest;
import io.camunda.zeebe.process.test.inspections.InspectionUtility;
import io.camunda.zeebe.process.test.inspections.model.InspectedProcessInstance;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

@ZeebeProcessTest
public class ZeebeServiceTest {

  private ZeebeTestEngine engine;
  private ZeebeClient client;

  @Test
  public void test() throws Exception {
    ZeebeService zeebeService = new ZeebeService(client);
    String myId = "test";

    DeploymentEvent deploymentEvent =
        client
            .newDeployCommand()
            .addResourceFromClasspath("client_example/example_process.bpmn")
            .send()
            .join();
    BpmnAssert.assertThat(deploymentEvent);

    Map<String, MessageBody> messages = new HashMap<>();
    MessageBody start =
        new MessageBody().setDate(new Date()).setMessage("START").setSynthetic(false);
    messages.put(myId, start);

    zeebeService
        .startProcessViaMessage(
            "START", myId, new HashMap<>(Map.of("myId", myId, "messages", messages)))
        .join();

    engine.waitForIdleState(Duration.ofSeconds(5));
    InspectedProcessInstance processInstance =
        InspectionUtility.findProcessInstances().findLastProcessInstance().get();
    BpmnAssert.assertThat(processInstance).isStarted();
    BpmnAssert.assertThat(processInstance).isWaitingForMessages("A");
    BpmnAssert.assertThat(processInstance).isActive();

    MessageBody b = new MessageBody().setMessage("B").setSynthetic(true).setDate(new Date());
    zeebeService.sendArbitraryMessage(b, myId, new HashMap<>()).block();
    engine.waitForIdleState(Duration.ofSeconds(5));
    BpmnAssert.assertThat(processInstance).isActive();
    BpmnAssert.assertThat(processInstance).isWaitingAtElements("Event_C");

    MessageBody c = new MessageBody().setMessage("C").setSynthetic(true).setDate(new Date());
    zeebeService.sendArbitraryMessage(c, myId, new HashMap<>()).block();
    engine.waitForIdleState(Duration.ofSeconds(5));
    BpmnAssert.assertThat(processInstance).hasPassedElement("EndEvent_1");
    BpmnAssert.assertThat(processInstance).isCompleted();
  }

  @Test
  public void testMultiInstance() throws Exception {
    ZeebeService zeebeService = new ZeebeService(client);
    MyObject mo1 = new MyObject().setMyId("myId1").setMyValue("myValue1");
    MyObject mo2 = new MyObject().setMyId("myId2").setMyValue("myValue2");
    List<MyObject> myObjects = new ArrayList<>(List.of(mo1, mo2));

    DeploymentEvent deploymentEvent =
        client
            .newDeployCommand()
            .addResourceFromClasspath("client_example/example_process_with_multi_instance.bpmn")
            .send()
            .join();
    BpmnAssert.assertThat(deploymentEvent);

    Map<String, MessageBody> messages = new HashMap<>();
    MessageBody start =
        new MessageBody().setDate(new Date()).setMessage("START").setSynthetic(false);
    messages.put(mo1.getMyId(), start);
    messages.put(mo2.getMyId(), start);

    zeebeService
        .startProcessViaMessage(
            "START",
            "not_needed",
            new HashMap<>(Map.of("messages", messages, "myObjects", myObjects)))
        .join();

    engine.waitForIdleState(Duration.ofSeconds(5));
    InspectedProcessInstance processInstance =
        InspectionUtility.findProcessInstances().findLastProcessInstance().get();
    BpmnAssert.assertThat(processInstance).isStarted();
    BpmnAssert.assertThat(processInstance).isWaitingForMessages("A");
    BpmnAssert.assertThat(processInstance).isActive();

    // myObject1 -> B
    MessageBody b = new MessageBody().setMessage("B").setSynthetic(true).setDate(new Date());
    zeebeService.sendArbitraryMessage(b, mo1.getMyId(), new HashMap<>()).block();
    engine.waitForIdleState(Duration.ofSeconds(5));
    BpmnAssert.assertThat(processInstance).isActive();
    BpmnAssert.assertThat(processInstance).isWaitingAtElements("Event_C");

    // myObject2 -> C
    MessageBody c = new MessageBody().setMessage("C").setSynthetic(true).setDate(new Date());
    zeebeService.sendArbitraryMessage(c, mo2.getMyId(), new HashMap<>()).block();
    engine.waitForIdleState(Duration.ofSeconds(5));
    BpmnAssert.assertThat(processInstance).hasPassedElement("EndEvent_1");
    BpmnAssert.assertThat(processInstance).isNotCompleted(); // myObject1 is not yet completed

    // myObject1 -> C
    MessageBody c2 = new MessageBody().setMessage("C").setSynthetic(true).setDate(new Date());
    zeebeService.sendArbitraryMessage(c2, mo1.getMyId(), new HashMap<>()).block();
    engine.waitForIdleState(Duration.ofSeconds(5));
    BpmnAssert.assertThat(processInstance).isCompleted(); // now both multi-instances have finished
  }

  @Test
  public void testEventbasedSubprocesses() throws Exception {
    ZeebeService zeebeService = new ZeebeService(client);
    String myId = "test";

    DeploymentEvent deploymentEvent =
        client
            .newDeployCommand()
            .addResourceFromClasspath("client_example/example_process.bpmn")
            .send()
            .join();
    BpmnAssert.assertThat(deploymentEvent);

    Map<String, MessageBody> messages = new HashMap<>();
    MessageBody start =
        new MessageBody().setDate(new Date()).setMessage("START").setSynthetic(false);
    messages.put(myId, start);

    zeebeService
        .startProcessViaMessage(
            "START", myId, new HashMap<>(Map.of("myId", myId, "messages", messages)))
        .join();

    engine.waitForIdleState(Duration.ofSeconds(5));
    InspectedProcessInstance processInstance =
        InspectionUtility.findProcessInstances().findLastProcessInstance().get();
    BpmnAssert.assertThat(processInstance).isStarted();
    BpmnAssert.assertThat(processInstance).isWaitingForMessages("A");
    BpmnAssert.assertThat(processInstance).isActive();

    // non interrupting
    MessageBody esbNonInterrupting =
        new MessageBody()
            .setMessage("ESB_MESSAGE_NONINTERRUPTING")
            .setSynthetic(false)
            .setDate(new Date());
    zeebeService.sendArbitraryMessage(esbNonInterrupting, myId, new HashMap<>()).block();
    engine.waitForIdleState(Duration.ofSeconds(5));
    BpmnAssert.assertThat(processInstance)
        .hasPassedElementsInOrder(
            "Event_ESB_MESSAGE_NONINTERRUPTING", "Event_ESB_MESSAGE_NONINTERRUPTING_ENDEVENT");
    BpmnAssert.assertThat(processInstance).isWaitingAtElements("Event_A1");

    // interrupting
    MessageBody esb =
        new MessageBody().setMessage("ESB_MESSAGE").setSynthetic(false).setDate(new Date());
    zeebeService.sendArbitraryMessage(esb, myId, new HashMap<>()).block();

    engine.waitForIdleState(Duration.ofSeconds(5));
    BpmnAssert.assertThat(processInstance)
        .hasPassedElementsInOrder("Event_ESB_MESSAGE", "Event_ESB_MESSAGE_ENDEVENT");
    BpmnAssert.assertThat(processInstance).isCompleted();
  }

  static class MyObject {
    private String myId;
    private String myValue;

    public String getMyId() {
      return myId;
    }

    public MyObject setMyId(String myId) {
      this.myId = myId;
      return this;
    }

    public String getMyValue() {
      return myValue;
    }

    public MyObject setMyValue(String myValue) {
      this.myValue = myValue;
      return this;
    }
  }
}
