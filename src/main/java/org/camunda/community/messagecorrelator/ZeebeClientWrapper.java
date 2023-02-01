package org.camunda.community.messagecorrelator;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.client.api.worker.JobWorker;
import java.util.Map;
import reactor.core.publisher.Mono;

/** Wrapper class used to encapsulate zeebeClient for easier testing/mocking */
public class ZeebeClientWrapper {
  private final ZeebeClient zeebeClient;

  public ZeebeClientWrapper(ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }

  public ZeebeFuture<PublishMessageResponse> publishMessage(
      String correlationId, String message, Map<String, Object>... processVariables) {
    //        LOG.debug("publishing Message={} for correlationId={} with processVariables={}",
    // processVariables.getMessage(), correlationId, processVariables);

    PublishMessageCommandStep1.PublishMessageCommandStep3 publishMessageStep =
        zeebeClient.newPublishMessageCommand().messageName(message).correlationKey(correlationId);
    if (processVariables != null && processVariables.length == 1) {
      publishMessageStep.variables(processVariables[0]);
    }

    return publishMessageStep.send();
  }

  public Mono<Map<String, Object>> getProcessVariables(String parcelId) {
    Mono<Map<String, Object>> processVariables =
        Mono.create(
            sink -> {
              String jobType = "processState_" + parcelId;
              JobWorker worker =
                  zeebeClient
                      .newWorker()
                      .jobType(jobType)
                      .handler(
                          (client, job) -> {
                            sink.success(job.getVariablesAsMap());
                            //                                job.getVariablesAsType(
                            //                                    ProcessVariables.class)); // TODO
                            // smaller scope
                            client.newCompleteCommand(job).send();
                          })
                      .fetchVariables()
                      .name(jobType)
                      .open();
              sink.onDispose(worker::close);
            });

    publishMessage(parcelId, "Msg_ProcessState").join();

    return processVariables;
  }
}
