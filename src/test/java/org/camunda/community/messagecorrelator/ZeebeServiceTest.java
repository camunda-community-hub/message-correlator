package org.camunda.community.messagecorrelator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ZeebeServiceTest {

  private ZeebeService zeebeService;

  @BeforeEach
  public void initService() {}

  //    @Test
  //    public void testStartProcessViaBpmnProcessId() throws Exception {
  //        zeebeService.startProcessViaBpmnProcessId(null, null);
  //    }
  //
  //    @Test
  //    public void testStartProcessViaProcessDefinitionKey() throws Exception {
  //        zeebeService.startProcessViaProcessDefinitionKey(0, null);
  //    }
  //
  //    @Test
  //    public void testStartProcessViaMessage() throws Exception {
  //        zeebeService.startProcessViaMessage(null, null, null);
  //    }
  //
  //    @Test
  //    public void testSendMessage() {
  //        zeebeService.sendArbitraryMessage(null, null, null);
  //    }
}
