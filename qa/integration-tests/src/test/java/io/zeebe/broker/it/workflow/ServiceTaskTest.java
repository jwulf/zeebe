/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.it.workflow;

import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertJobCompleted;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCompleted;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCreated;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.response.JobHeaders;
import io.zeebe.client.api.subscription.JobHandler;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class ServiceTaskTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldCreateWorkflowInstanceWithServiceTask() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeTaskType("foo"))
            .endEvent("end")
            .done();

    final DeploymentEvent deploymentEvent =
        clientRule
            .getClient()
            .newDeployCommand()
            .addWorkflowModel(modelInstance, "workflow.bpmn")
            .send()
            .join();
    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());

    // when
    final WorkflowInstanceEvent workflowInstance =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    // then
    assertThat(workflowInstance.getWorkflowInstanceKey()).isGreaterThan(0);
    assertWorkflowInstanceCreated();
  }

  @Test
  public void shouldLockServiceTask() {
    // given
    final Map<String, String> customHeaders = new HashMap<>();
    customHeaders.put("cust1", "a");
    customHeaders.put("cust2", "b");
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeTaskType("foo")
                        .zeebeTaskHeader("cust1", "a")
                        .zeebeTaskHeader("cust2", "b"))
            .endEvent("end")
            .done();
    deploy(modelInstance);

    final WorkflowInstanceEvent workflowInstance =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    // when
    final RecordingJobHandler recordingJobHandler = new RecordingJobHandler();

    clientRule.getClient().newWorker().jobType("foo").handler(recordingJobHandler).open();

    // then
    waitUntil(() -> recordingJobHandler.getHandledJobs().size() >= 1);

    assertThat(recordingJobHandler.getHandledJobs()).hasSize(1);

    final Record<WorkflowInstanceRecordValue> record =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("task")
            .findFirst()
            .get();

    final ActivatedJob jobEvent = recordingJobHandler.getHandledJobs().get(0);
    final JobHeaders headers = jobEvent.getHeaders();
    assertThat(headers.getBpmnProcessId()).isEqualTo("process");
    assertThat(headers.getWorkflowDefinitionVersion()).isEqualTo(1);
    assertThat(headers.getWorkflowKey()).isEqualTo(workflowInstance.getWorkflowKey());
    assertThat(headers.getWorkflowInstanceKey())
        .isEqualTo(workflowInstance.getWorkflowInstanceKey());
    assertThat(headers.getElementId()).isEqualTo("task");
    assertThat(headers.getElementInstanceKey()).isEqualTo(record.getKey());

    assertThat(jobEvent.getCustomHeaders()).containsOnly(entry("cust1", "a"), entry("cust2", "b"));
  }

  @Test
  public void shouldCompleteServiceTask() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeTaskType("foo"))
            .endEvent("end")
            .done();

    deploy(modelInstance);

    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .send()
        .join();

    // when
    clientRule
        .getClient()
        .newWorker()
        .jobType("foo")
        .handler((client, job) -> client.newCompleteCommand(job.getKey()).send())
        .open();

    // then
    assertJobCompleted();
    assertWorkflowInstanceCompleted("process");
  }

  @Test
  public void shouldMapPayloadIntoTask() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeTaskType("foo").zeebeInput("$.foo", "$.bar"))
            .endEvent("end")
            .done();
    deploy(modelInstance);

    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .payload("{\"foo\":1}")
        .send()
        .join();

    // when
    final RecordingJobHandler recordingJobHandler = new RecordingJobHandler();

    clientRule.getClient().newWorker().jobType("foo").handler(recordingJobHandler).open();

    // then
    waitUntil(() -> recordingJobHandler.getHandledJobs().size() >= 1);

    final ActivatedJob jobEvent = recordingJobHandler.getHandledJobs().get(0);
    JsonUtil.assertEquality(jobEvent.getPayload(), "{'bar': 1, 'foo': 1}");
  }

  @Test
  public void shouldMapPayloadFromTask() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeTaskType("foo").zeebeOutput("$.foo", "$.bar"))
            .endEvent("end")
            .done();
    deploy(modelInstance);

    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .send()
        .join();

    // when
    clientRule
        .getClient()
        .newWorker()
        .jobType("foo")
        .handler(
            (client, job) -> client.newCompleteCommand(job.getKey()).payload("{\"foo\":2}").send())
        .open();

    // then
    assertWorkflowInstanceCompleted(
        "process",
        (workflowEvent) -> assertThat(workflowEvent.getPayload()).isEqualTo("{\"bar\":2}"));
  }

  @Test
  public void shouldModifyPayloadInTask() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeTaskType("foo")
                        .zeebeInput("$.foo", "$.foo")
                        .zeebeOutput("$.foo", "$.foo"))
            .endEvent("end")
            .done();
    deploy(modelInstance);

    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .payload("{\"foo\":1}")
        .send()
        .join();

    // when
    clientRule
        .getClient()
        .newWorker()
        .jobType("foo")
        .handler(
            (client, job) -> {
              final String modifiedPayload = job.getPayload().replaceAll("1", "2");
              client.newCompleteCommand(job.getKey()).payload(modifiedPayload).send();
            })
        .open();

    // then
    assertWorkflowInstanceCompleted(
        "process",
        (workflowEvent) -> assertThat(workflowEvent.getPayload()).isEqualTo("{\"foo\":2}"));
  }

  @Test
  public void shouldCompleteTasksAndMergePayload() throws Exception {

    // given
    clientRule
        .getClient()
        .newDeployCommand()
        .addResourceFile(getClass().getResource("/workflows/orderProcess.bpmn").getFile())
        .send()
        .join();

    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("order-process")
        .latestVersion()
        .payload("{\"foo\":1}")
        .send()
        .join();

    // when
    final JobHandler defaultHandler =
        new JobHandler() {
          @Override
          public void handle(JobClient client, ActivatedJob job) {
            client.newCompleteCommand(job.getKey()).payload("{}").send().join();
          }
        };
    clientRule.getClient().newWorker().jobType("collect-money").handler(defaultHandler).open();

    clientRule
        .getClient()
        .newWorker()
        .jobType("fetch-items")
        .handler(
            (client, job) -> {
              client.newCompleteCommand(job.getKey()).payload("{\"foo\":\"bar\"}").send().join();
            })
        .open();
    clientRule.getClient().newWorker().jobType("ship-parcel").handler(defaultHandler).open();

    // then

    assertWorkflowInstanceCompleted(
        "order-process",
        (workflowEvent) -> assertThat(workflowEvent.getPayload()).isEqualTo("{\"foo\":\"bar\"}"));
  }

  @Test
  public void shouldCompleteTasksFromMultipleProcesses() throws InterruptedException {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeTaskType("foo")
                        .zeebeInput("$.foo", "$.foo")
                        .zeebeOutput("$.foo", "$.foo"))
            .endEvent("end")
            .done();
    deploy(modelInstance);

    // when
    final int instances = 10;
    final List<Long> instanceKeys = new ArrayList<>();
    for (int i = 0; i < instances; i++) {
      final WorkflowInstanceEvent instanceEvent =
          clientRule
              .getClient()
              .newCreateInstanceCommand()
              .bpmnProcessId("process")
              .latestVersion()
              .payload("{\"foo\":1}")
              .send()
              .join();
      instanceKeys.add(instanceEvent.getWorkflowInstanceKey());
    }

    clientRule
        .getClient()
        .newWorker()
        .jobType("foo")
        .handler(
            (client, job) -> client.newCompleteCommand(job.getKey()).payload("{\"foo\":2}").send())
        .open();

    // then

    for (int i = 0; i < instances; i++) {
      assertWorkflowInstanceCompleted("process", instanceKeys.get(i));
    }

    // since we do this after we saw all wf instance completed events we can't miss
    // any completed job events
    assertThat(RecordingExporter.jobRecords(JobIntent.COMPLETED).limit(instances).count())
        .isEqualTo(instances);
  }

  private DeploymentEvent deploy(BpmnModelInstance modelInstance) {
    final DeploymentEvent deploymentEvent =
        clientRule
            .getClient()
            .newDeployCommand()
            .addWorkflowModel(modelInstance, "workflow.bpmn")
            .send()
            .join();
    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
    return deploymentEvent;
  }
}
