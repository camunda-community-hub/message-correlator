package org.camunda.community.messagecorrelator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class BpmnModelServiceRealProcessTest {

  @Autowired private BpmnModelService bpmnModelService;

  @BeforeEach
  public void initService() {
    this.bpmnModelService =
        new BpmnModelService("src/test/resources/models/shipment-tracking-for-redirection.bpmn");
  }

  @Test
  public void
      whenProcessStatePREADVICEandMessageINTRANSITarrivesThenSendMessagesPREADVICEandINTRANSIT() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("StartEvent_Preadvice", "INTRANSIT");

    assertThat(messagesToBeSent.size()).isEqualTo(2);
    assertThat(messagesToBeSent.get(0)).isEqualTo("PREADVICE");
    assertThat(messagesToBeSent.get(1)).isEqualTo("INTRANSIT");
  }

  @Test
  public void whenProcessStatePREADVICEandMessageFINALarrivesThenSendMessages() {
    List<String> messagesToBeSent =
        bpmnModelService.determineMessagesToBeSent("StartEvent_Preadvice", "FINAL");

    assertThat(messagesToBeSent.size()).isEqualTo(6);
    assertThat(messagesToBeSent.get(0)).isEqualTo("PREADVICE");
    assertThat(messagesToBeSent.get(1)).isEqualTo("INTRANSIT");
    assertThat(messagesToBeSent.get(2)).isEqualTo("INDELIVERY");
    assertThat(messagesToBeSent.get(3)).isEqualTo("NOTDELIVERED");
    assertThat(messagesToBeSent.get(4)).isEqualTo("INWAREHOUSE");
    assertThat(messagesToBeSent.get(5)).isEqualTo("FINAL");
  }
}
