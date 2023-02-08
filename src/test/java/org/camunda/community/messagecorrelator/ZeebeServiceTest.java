package org.camunda.community.messagecorrelator;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.extension.testcontainer.ZeebeProcessTest;
import io.camunda.zeebe.process.test.inspections.InspectionUtility;
import io.camunda.zeebe.process.test.inspections.model.InspectedProcessInstance;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

@ZeebeProcessTest
public class ZeebeServiceTest {

  private ZeebeTestEngine engine;
  private ZeebeClient client;

  // TODO: currently the ZeebeTestEngine has an issue if there is a message startEvent and a
  //  message subprocess. As a workaround in this test-process the main part of the process got
  // wrapped inside a
  //  subprocess
  @Test
  public void test() throws Exception {
    ZeebeService zeebeService = new ZeebeService(client);
    String myId = "test";

    DeploymentEvent deploymentEvent =
        client
            .newDeployCommand()
            .addResourceFromClasspath("client_example/example_process_for_test.bpmn")
            .send()
            .join();
    BpmnAssert.assertThat(deploymentEvent);

    zeebeService
        .startProcessViaMessage("START", myId, new Date(), new HashMap<>(Map.of("myId", myId)))
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
}
