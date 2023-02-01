package org.camunda.community.messagecorrelator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.Test;

public class MessageBodyTest {

  @Test
  public void testSortAfterPhysicalDate() throws Exception {
    MessageBody firstMessage = new MessageBody();
    firstMessage.setDate(DateUtils.parseDate("2020-01-01 12:00:00.000", MessageBody.DATE_PATTERN));

    MessageBody secondMessage = new MessageBody();
    secondMessage.setDate(DateUtils.parseDate("2020-01-01 12:00:00.001", MessageBody.DATE_PATTERN));

    MessageBody thirdMessage = new MessageBody();
    thirdMessage.setDate(DateUtils.parseDate("2020-01-01 12:00:02.000", MessageBody.DATE_PATTERN));

    List<MessageBody> messageBodies = Arrays.asList(firstMessage, secondMessage, thirdMessage);
    assertThat(messageBodies.get(0)).isEqualTo(firstMessage);
    assertThat(messageBodies.get(1)).isEqualTo(secondMessage);
    assertThat(messageBodies.get(2)).isEqualTo(thirdMessage);

    Collections.sort(messageBodies);
    assertThat(messageBodies.get(0)).isEqualTo(thirdMessage);
    assertThat(messageBodies.get(1)).isEqualTo(secondMessage);
    assertThat(messageBodies.get(2)).isEqualTo(firstMessage);
  }
}
