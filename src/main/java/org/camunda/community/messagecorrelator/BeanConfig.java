package org.camunda.community.messagecorrelator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

  @Value("${bpmn.path:notProvided}")
  private String bpmnPath;

  @Bean
  public BpmnModelService bpmnModelService() {
    return new BpmnModelService(bpmnPath);
  }

  @Bean
  public ZeebeService zeebeService(
      ZeebeClientWrapper zeebeClientWrapper,
      BpmnModelService bpmnModelService,
      ObjectMapper objectMapper) {
    return new ZeebeService(zeebeClientWrapper, bpmnModelService, objectMapper);
  }

  @Bean
  public ZeebeClientWrapper zeebeClientWrapper(ZeebeClient zeebeClient) {
    return new ZeebeClientWrapper(zeebeClient);
  }

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
