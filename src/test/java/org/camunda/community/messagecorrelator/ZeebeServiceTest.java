package org.camunda.community.messagecorrelator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ZeebeServiceTest {

  @InjectMocks private ZeebeService zeebeService;
  @Mock private ZeebeClientWrapper zeebeClientWrapper;
  @Mock private BpmnModelService bpmnModelService;

  @BeforeEach
  public void initService() {}

  @Test
  public void testGetMostRecentMessage() throws Exception {
    MessageBody preadvice = new MessageBody();
    preadvice.setMessage("PREADVICE");
    preadvice.setSynthetic(false);
    preadvice.setDate(DateUtils.parseDate("2020-01-01 12:00:00.000", MessageBody.DATE_PATTERN));

    MessageBody intransit = new MessageBody();
    intransit.setMessage("INTRANSIT");
    intransit.setSynthetic(false);
    intransit.setDate(DateUtils.parseDate("2020-01-01 12:00:00.001", MessageBody.DATE_PATTERN));

    Map<String, List<MessageBody>> messageBodies =
        Map.of("ALL", List.of(preadvice), "myParcelId", List.of(intransit));

    MessageBody mostRecentMessageBody =
        zeebeService.getMostRecentMessage("myParcelId", messageBodies);

    assertThat(mostRecentMessageBody).isEqualTo(intransit);
  }

  @Test
  public void testStartProcess() throws Exception {

    // TODO
    //          GatewayOuterClass.PublishMessageResponse y =
    //   GatewayOuterClass.PublishMessageResponse.getDefaultInstance();
    //          PublishMessageResponse x = new PublishMessageResponseImpl(y);
    //          when(zeebeClientWrapper.publishMessage(any(), any(), any()).join()).thenReturn(x);
    //
    //          StartProcessBody startProcessBody = new StartProcessBody();
    //          startProcessBody.setTransactionId("Jens-S0053");
    //          startProcessBody.setParcelIds(Set.of("Jens-P0053a", "Jens-P0053b"));
    //          startProcessBody.setProduct("BusinessParcel");
    //          startProcessBody.setService("CashService");
    //          startProcessBody.setPostalCode("04103");
    //          startProcessBody.setParcelStatus(Message.PREADVICE);
    //          startProcessBody.setPhysicalDate(DateUtils.parseDate("2020-01-01 12:00:00.000",
    //   MessageBody.DATE_PATTERN));
    //          startProcessBody.setLogicalDate(DateUtils.parseDate("2020-01-01 12:00:00.000",
    //   MessageBody.DATE_PATTERN));
    //
    //          ZeebeFuture<PublishMessageResponse> shipment =
    //         zeebeService.createShipment(startProcessBody);
  }

  @Test
  public void testSendMessage() {
    // TODO
    //        zeebeService.sendMessage();
  }
}
