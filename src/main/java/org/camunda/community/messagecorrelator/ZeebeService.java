package org.camunda.community.messagecorrelator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.client.api.worker.JobWorker;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class ZeebeService {

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeService.class);
  private final BpmnModelService bpmnModelService;
  private final ObjectMapper objectMapper;
  private final ZeebeClient zeebeClient;
  private final String jobTypePrefix;
  private final String syncMessage;
  private final String messagesProcessVar;

  public ZeebeService(ZeebeClient zeebeClient) throws ConfigurationException {
    // resolve properties
    PropertiesConfiguration config = new PropertiesConfiguration();
    config.load("application.properties");

    this.syncMessage = config.getString("message-correlator.syncMessage");
    this.jobTypePrefix = config.getString("message-correlator.syncTaskTypePrefix");
    String messagesProcessVarFromConfig = config.getString("message-correlator.messagesProcessVar");
    this.messagesProcessVar = Objects.requireNonNullElse(messagesProcessVarFromConfig, "messages");

    String bpmnPath = config.getString("message-correlator.path");
    this.bpmnModelService = new BpmnModelService(bpmnPath);
    this.zeebeClient = zeebeClient;
    this.objectMapper = new ObjectMapper();
  }

  public ZeebeFuture<PublishMessageResponse> startProcessViaMessage(
      String message, String correlationId, Map<String, Object> additionalProcessVariables) {

    return zeebeClient
        .newPublishMessageCommand()
        .messageName(message)
        .correlationKey(correlationId)
        .variables(additionalProcessVariables)
        .send();
  }

  /**
   * @param messageBody
   * @param id
   * @param additionalProcessVars
   * @return
   */
  public Mono<Map<String, Object>> sendArbitraryMessage(
      MessageBody messageBody, String id, Map<String, Object> additionalProcessVars) {

    // if targetMessage is in event-based subprocess -> send directly
    if (bpmnModelService.isEventbasedSubProcessStartEvent(messageBody.getMessage())) {
      zeebeClient
          .newPublishMessageCommand()
          .messageName(messageBody.getMessage())
          .correlationKey(id)
          .variables(additionalProcessVars)
          .send();

      return Mono.empty();
    }

    // get process variables to be able to infer current state in the engine
    return getProcessVariables(id)
        .doOnSuccess(
            processVariables -> {
              MessageBody lastProcessedMessageForId =
                  extractLastProcessedMessageForId(id, processVariables);

              if (messageBody.getDate().before(lastProcessedMessageForId.getDate())) {
                // do nothing, if we have already processed a more recent message
                LOG.info(
                    "Already have processed msg={} with date={}, so not gonna process msg={} with date={}",
                    lastProcessedMessageForId.getMessage(),
                    lastProcessedMessageForId.getDate(),
                    messageBody.getMessage(),
                    messageBody.getDate());
                return;
              }

              // determine messages to send
              List<String> messagesToSend =
                  bpmnModelService
                      .determineMessagesToSend(lastProcessedMessageForId, messageBody.getMessage())
                      .stream()
                      .map(String::valueOf)
                      .collect(Collectors.toList());
              LOG.debug("Going to send Messages={}", messagesToSend);

              // pass additionalProcessVars through
              processVariables.putAll(additionalProcessVars);

              ZeebeFuture<PublishMessageResponse> response = null;
              for (int index = 0; index < messagesToSend.size(); index++) {
                String messageString = messagesToSend.get(index);
                if (index < messagesToSend.size() - 1) {
                  MessageBody syntheticMessageBody =
                      inferSyntheticMessageBody(messageBody, messageString);
                  addMessageToProcessVars(id, syntheticMessageBody, processVariables);
                } else {
                  addMessageToProcessVars(id, messageBody, processVariables);
                }
                LOG.info("Publishing message={}", messageString);
                LOG.debug("with processVars={}", processVariables);
                response =
                    zeebeClient
                        .newPublishMessageCommand()
                        .messageName(messageString)
                        .correlationKey(id)
                        .variables(processVariables)
                        .send();
                LOG.debug("Published message={}", response);
              }
            });
  }

  private Mono<Map<String, Object>> getProcessVariables(String correlationKey) {
    Mono<Map<String, Object>> processVariables =
        Mono.create(
            sink -> {
              String jobType = jobTypePrefix + correlationKey;
              JobWorker worker =
                  zeebeClient
                      .newWorker()
                      .jobType(jobType)
                      .handler(
                          (client, job) -> {
                            sink.success(job.getVariablesAsMap()); // TODO smaller scope
                            client.newCompleteCommand(job).send();
                          })
                      .fetchVariables()
                      .name(jobType)
                      .open();
              sink.onDispose(worker::close);
            });

    zeebeClient
        .newPublishMessageCommand()
        .messageName(syncMessage)
        .correlationKey(correlationKey)
        .send();

    return processVariables;
  }

  // if process vars == null or messages can't be found or message for id can't be found ->
  // something is wrong
  private MessageBody extractLastProcessedMessageForId(
      String correlationKey, Map<String, Object> processVariables) {
    Map<String, LinkedHashMap<String, String>> messagesMap =
        (Map<String, LinkedHashMap<String, String>>) processVariables.get(messagesProcessVar);
    LinkedHashMap stringStringLinkedHashMap = messagesMap.get(correlationKey);
    MessageBody lastProcessedMessageForcorrelationKey =
        objectMapper.convertValue(stringStringLinkedHashMap, MessageBody.class);
    return lastProcessedMessageForcorrelationKey;
  }

  private void addMessageToProcessVars(
      String correlationKey, MessageBody messageBody, Map<String, Object> processVariables) {
    Map<String, MessageBody> messages =
        (Map<String, MessageBody>) processVariables.get(messagesProcessVar);
    if (messages == null) {
      messages = new HashMap<>();
    }
    messages.put(correlationKey, messageBody);
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
