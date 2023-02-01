package org.camunda.community.messagecorrelator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZeebeService {

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeService.class);
  private final ObjectMapper objectMapper;
  private final BpmnModelService bpmnModelService;
  private final ZeebeClientWrapper zeebeClientWrapper;

  public ZeebeService(
      ZeebeClientWrapper zeebeClientWrapper,
      BpmnModelService bpmnModelService,
      ObjectMapper objectMapper) {
    this.zeebeClientWrapper = zeebeClientWrapper;
    this.bpmnModelService = bpmnModelService;
    this.objectMapper = objectMapper;
  }

  public ZeebeFuture<PublishMessageResponse> startProcess(
      String correlationId, String message, Map<String, Object> processVariables) {
    return zeebeClientWrapper.publishMessage(correlationId, message, processVariables);
  }

  public void sendMessage(String parcelId, MessageBody messageBody) {
    // get already processed messages
    Map<String, Object> processVariables = zeebeClientWrapper.getProcessVariables(parcelId).block();

    // TODO: refactor collections handling
    Map<String, List<Map<String, Object>>> messagesMapAsMap =
        (Map<String, List<Map<String, Object>>>) processVariables.get("messages");
    Map<String, List<MessageBody>> messagesMap = new HashMap<>();

    messagesMapAsMap.forEach(
        (k, v) -> {
          v.forEach(
              messageAsObject -> {
                MessageBody asMessageBody =
                    objectMapper.convertValue(messageAsObject, MessageBody.class);

                List<MessageBody> messageBodies = messagesMap.get(k);
                if (messageBodies == null || messageBodies.isEmpty()) {
                  messagesMap.put(k, Arrays.asList(asMessageBody));
                } else {
                  List<MessageBody> alreadyKnownMessageBodies = new ArrayList<>(messagesMap.get(k));
                  alreadyKnownMessageBodies.add(asMessageBody);
                  messagesMap.put(k, alreadyKnownMessageBodies);
                }
              });
        });

    MessageBody mostRecent = getMostRecentMessage(parcelId, messagesMap);
    if (messageBody.getDate().before(mostRecent.getDate())) {
      // do nothing, if we have already processed a more recent message
      LOG.info(
          "Already have processed msg={} with date={}, so not gonna process msg={} with physicalDate={}",
          mostRecent.getMessage(),
          mostRecent.getDate(),
          messageBody.getMessage(),
          messageBody.getDate());
      return;
    }

    // get bpmnElementId of last completed element
    // we have: [0]: most recent message, [n-1]: first received message (PREADVICE)
    List<String> bpmnElementIdCandidates =
        bpmnModelService.determineBpmnElementIdsForMessage(mostRecent.getMessage());
    String lastCompletedBpmnElementId =
        bpmnElementIdCandidates.get(
            0); // as all bpmnElementIdCandidates will lead to the same following event, take first
    // (could be any)

    // get messages to-be-sent
    List<String> messagesToBeSent =
        bpmnModelService
            .determineMessagesToBeSent(lastCompletedBpmnElementId, messageBody.getMessage())
            .stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
    messagesToBeSent.remove(0); // remove first, as this has already been completed.
    LOG.info(
        "Going to send Messages ={}",
        StringUtils.join(
            messagesToBeSent.stream().map(String::valueOf).collect(Collectors.toList())));

    // actually send messages
    sendMessages(parcelId, messageBody, messagesToBeSent, processVariables);
  }

  /** Could also be done via Operate */
  MessageBody getMostRecentMessage(
      String parcelId, Map<String, List<MessageBody>> existingMessagesInProcessVars) {
    // always get the existing messages for ALL (messages before MI) and for parcelId
    List<MessageBody> existingMessagesInProcessVarsForALL =
        existingMessagesInProcessVars.get(ProcessConstants.ALL);

    List<MessageBody> messagesForAllAndForParcelId =
        new ArrayList<>(existingMessagesInProcessVarsForALL);
    // if messages related to parcelId already exist, pass them through
    if (existingMessagesInProcessVars.containsKey(parcelId)) {
      messagesForAllAndForParcelId.addAll(existingMessagesInProcessVars.get(parcelId));
    }

    Collections.sort(messagesForAllAndForParcelId);
    return messagesForAllAndForParcelId.get(0);
  }

  private void sendMessages(
      String parcelId,
      MessageBody messageBody,
      List<String> messagesToBeSent,
      Map<String, Object> processVariables) {
    int numberOfMessagesToBeSent = messagesToBeSent.size();
    int messageSentCounter = 0;
    for (String mb : messagesToBeSent) {
      if (messageSentCounter < numberOfMessagesToBeSent - 1) {
        MessageBody syntheticMessageBody = inferSyntheticMessageBody(messageBody, mb);
        addMessageToProcessVars(parcelId, syntheticMessageBody, processVariables);

        LOG.info("Publishing synthetic message={} without processVars", mb);
        zeebeClientWrapper.publishMessage(parcelId, mb);
      } else {
        addMessageToProcessVars(parcelId, messageBody, processVariables);

        LOG.info("Publishing message={} (last one) with processVars", mb);
        zeebeClientWrapper.publishMessage(parcelId, mb, processVariables);
      }
      messageSentCounter++;
    }
  }

  // TODO: refactor collections handling
  private void addMessageToProcessVars(
      String parcelId, MessageBody messageBody, Map<String, Object> processVariables) {
    Map<String, Object> messages = (Map<String, Object>) processVariables.get("messages");

    if (messages == null) {
      messages = new HashMap<>();
    }
    if (!messages.containsKey(parcelId)) {
      messages.put(parcelId, new ArrayList<>(Arrays.asList(messageBody)));
    } else {
      List<MessageBody> messageBodies = (List<MessageBody>) messages.get(parcelId);
      messageBodies.addAll(new ArrayList<>(Arrays.asList(messageBody)));
    }
  }

  /**
   * @param messageBody actual messageBody that has been received, that wil be used as template
   * @param message the Message that will be used to create a synthetic MessageBody
   * @return synthetic Message body with Message from message and dates from messageBody
   */
  private MessageBody inferSyntheticMessageBody(MessageBody messageBody, String message) {
    MessageBody syntheticMessageBody = new MessageBody();
    syntheticMessageBody.setMessage(message);
    // Synthetic Message should happen 1 ms before the received Message
    Date syntheticDate = new Date(messageBody.getDate().getTime() - 1L);
    syntheticMessageBody.setDate(syntheticDate);
    syntheticMessageBody.setSynthetic(true);
    return syntheticMessageBody;
  }
}
