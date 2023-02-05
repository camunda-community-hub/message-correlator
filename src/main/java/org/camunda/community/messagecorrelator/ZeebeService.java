package org.camunda.community.messagecorrelator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
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

    this.syncMessage = config.getString("bpmn.syncMessage");
    this.jobTypePrefix = config.getString("bpmn.jobTypePrefix");
    String messagesProcessVarFromConfig = config.getString("bpmn.messagesProcessVar");
    this.messagesProcessVar = Objects.requireNonNullElse(messagesProcessVarFromConfig, "messages");

    String bpmnPath = config.getString("bpmn.path");
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

  public ZeebeFuture<ProcessInstanceEvent> startProcessViaProcessDefinitionKey(
      long processDefinitionKey, Map<String, Object> additionalProcessVariables) {
    return zeebeClient
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .variables(additionalProcessVariables)
        .send();
  }

  public ZeebeFuture<ProcessInstanceEvent> startProcessViaBpmnProcessId(
      String bpmnProcessId, int version, Map<String, Object> additionalProcessVariables) {
    return zeebeClient
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .version(version)
        .variables(additionalProcessVariables)
        .send();
  }

  /** starts latest version */
  public ZeebeFuture<ProcessInstanceEvent> startProcessViaBpmnProcessId(
      String bpmnProcessId, Map<String, Object> additionalProcessVariables) {
    return zeebeClient
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .variables(additionalProcessVariables)
        .send();
  }

  /**
   * @param messageBody
   * @param id
   * @param additionalProcessVars
   * @return
   */
  public ZeebeFuture<PublishMessageResponse> sendArbitraryMessage(
      MessageBody messageBody, String id, Map<String, Object> additionalProcessVars) {
    // get process variables to be able to infer current state in the engine
    Map<String, Object> processVariables = getProcessVariables(id).block();
    MessageBody lastProcessedMessageForId = extractLastProcessedMessageForId(id, processVariables);

    if (messageBody.getDate().before(lastProcessedMessageForId.getDate())) {
      // do nothing, if we have already processed a more recent message
      LOG.info(
          "Already have processed msg={} with date={}, so not gonna process msg={} with date={}",
          lastProcessedMessageForId.getMessage(),
          lastProcessedMessageForId.getDate(),
          messageBody.getMessage(),
          messageBody.getDate());
      return null;
    }

    // determine messages to send
    List<String> messagesSend =
        bpmnModelService
            .determineMessagesToSend(lastProcessedMessageForId, messageBody.getMessage())
            .stream()
            .map(String::valueOf)
            .collect(Collectors.toList());

    LOG.debug("Going to send Messages ={}", messagesSend);

    // pass additionalProcessVars through
    processVariables.putAll(additionalProcessVars);

    ZeebeFuture<PublishMessageResponse> response = null;
    for (int index = 0; index < messagesSend.size(); index++) {
      String messageString = messagesSend.get(index);
      if (index < messagesSend.size() - 1) {
        MessageBody syntheticMessageBody = inferSyntheticMessageBody(messageBody, messageString);
        addMessageToProcessVars(id, syntheticMessageBody, processVariables);
      } else {
        addMessageToProcessVars(id, messageBody, processVariables);
      }
      LOG.info("Publishing message={} with processVars={}", messageString, processVariables);
      response =
          zeebeClient
              .newPublishMessageCommand()
              .messageName(messageString)
              .correlationKey(id)
              .variables(processVariables)
              .send();
    }
    return response;
  }

  private Mono<Map<String, Object>> getProcessVariables(String parcelId) {
    Mono<Map<String, Object>> processVariables =
        Mono.create(
            sink -> {
              String jobType = jobTypePrefix + "_" + parcelId;
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

    zeebeClient.newPublishMessageCommand().messageName(syncMessage).correlationKey(parcelId).send();

    return processVariables;
  }

  // if process vars == null or messages can't be found or message for id can't be found ->
  // something is wrong
  private MessageBody extractLastProcessedMessageForId(
      String parcelId, Map<String, Object> processVariables) {
    Map<String, LinkedHashMap<String, String>> messagesMap =
        (Map<String, LinkedHashMap<String, String>>) processVariables.get(messagesProcessVar);
    LinkedHashMap stringStringLinkedHashMap = messagesMap.get(parcelId);
    MessageBody lastProcessedMessageForParcelId =
        objectMapper.convertValue(stringStringLinkedHashMap, MessageBody.class);
    return lastProcessedMessageForParcelId;
  }

  private void addMessageToProcessVars(
      String parcelId, MessageBody messageBody, Map<String, Object> processVariables) {
    Map<String, MessageBody> messages =
        (Map<String, MessageBody>) processVariables.get(messagesProcessVar);
    if (messages == null) {
      messages = new HashMap<>();
    }
    messages.put(parcelId, messageBody);
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
