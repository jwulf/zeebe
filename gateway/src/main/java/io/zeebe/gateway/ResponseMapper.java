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
package io.zeebe.gateway;

import static io.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.gateway.cmd.ClientException;
import io.zeebe.gateway.impl.data.MsgPackConverter;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.zeebe.gateway.protocol.GatewayOuterClass.BrokerInfo;
import io.zeebe.gateway.protocol.GatewayOuterClass.BrokerInfo.Builder;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelWorkflowInstanceResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceInfo;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.GetWorkflowResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.JobHeaders;
import io.zeebe.gateway.protocol.GatewayOuterClass.ListWorkflowsResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.Partition;
import io.zeebe.gateway.protocol.GatewayOuterClass.Partition.PartitionBrokerRole;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.TopologyInfo;
import io.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateWorkflowInstancePayloadResponse;
import io.zeebe.msgpack.value.LongValue;
import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto;
import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto.BrokerDto;
import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto.PartitionDto;
import io.zeebe.protocol.impl.data.repository.WorkflowMetadataAndResource;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import java.util.ArrayList;
import java.util.Iterator;
import org.agrona.DirectBuffer;

public class ResponseMapper {

  private static final MsgPackConverter MSG_PACK_CONVERTER = new MsgPackConverter();

  private static PartitionBrokerRole remapPartitionBrokerRoleEnum(
      final BrokerDto brokerDto, final PartitionDto partition) {
    switch (partition.getState()) {
      case LEADER:
        return PartitionBrokerRole.LEADER;
      case FOLLOWER:
        return PartitionBrokerRole.FOLLOWER;
      default:
        throw new ClientException(
            "Unknown broker role in response for partition "
                + partition
                + " on broker "
                + brokerDto);
    }
  }

  public static TopologyResponse toTopologyResponse(long key, TopologyResponseDto brokerResponse) {
    final TopologyInfo.Builder topologyInfoBuilder =
        TopologyInfo.newBuilder()
            .setClusterSize(brokerResponse.getClusterSize())
            .setPartitionsCount(brokerResponse.getPartitionsCount())
            .setReplicationFactor(brokerResponse.getReplicationFactor());
    final TopologyResponse.Builder topologyResponseBuilder = TopologyResponse.newBuilder();

    final ArrayList<BrokerInfo> infos = new ArrayList<>();

    brokerResponse
        .brokers()
        .forEach(
            broker -> {
              final Builder brokerInfo =
                  BrokerInfo.newBuilder()
                      .setNodeId(broker.getNodeId())
                      .setHost(bufferAsString(broker.getHost()))
                      .setPort(broker.getPort());

              broker
                  .partitionStates()
                  .forEach(
                      partition -> {
                        final Partition.Builder partitionBuilder = Partition.newBuilder();
                        partitionBuilder.setPartitionId(partition.getPartitionId());
                        partitionBuilder.setRole(remapPartitionBrokerRoleEnum(broker, partition));
                        brokerInfo.addPartitions(partitionBuilder);
                      });

              infos.add(brokerInfo.build());
            });

    topologyInfoBuilder.addAllBrokers(infos);
    return TopologyResponse.newBuilder().setInfo(topologyInfoBuilder.build()).build();
  }

  public static DeployWorkflowResponse toDeployWorkflowResponse(
      long key, DeploymentRecord brokerResponse) {
    final DeployWorkflowResponse.Builder responseBuilder =
        DeployWorkflowResponse.newBuilder().setKey(key);

    brokerResponse
        .workflows()
        .forEach(
            workflow ->
                responseBuilder
                    .addWorkflowsBuilder()
                    .setBpmnProcessId(bufferAsString(workflow.getBpmnProcessId()))
                    .setVersion(workflow.getVersion())
                    .setWorkflowKey(workflow.getKey())
                    .setResourceName(bufferAsString(workflow.getResourceName())));

    return responseBuilder.build();
  }

  public static PublishMessageResponse toPublishMessageResponse(long key, Object brokerResponse) {
    return PublishMessageResponse.getDefaultInstance();
  }

  public static UpdateJobRetriesResponse toUpdateJobRetriesResponse(
      long key, JobRecord brokerResponse) {
    return UpdateJobRetriesResponse.getDefaultInstance();
  }

  public static FailJobResponse toFailJobResponse(long key, JobRecord brokerResponse) {
    return FailJobResponse.getDefaultInstance();
  }

  public static CompleteJobResponse toCompleteJobResponse(long key, JobRecord brokerResponse) {
    return CompleteJobResponse.getDefaultInstance();
  }

  public static CreateWorkflowInstanceResponse toCreateWorkflowInstanceResponse(
      long key, WorkflowInstanceRecord brokerResponse) {
    final CreateWorkflowInstanceInfo info =
        CreateWorkflowInstanceInfo.newBuilder()
            .setWorkflowKey(brokerResponse.getWorkflowKey())
            .setBpmnProcessId(bufferAsString(brokerResponse.getBpmnProcessId()))
            .setVersion(brokerResponse.getVersion())
            .setWorkflowInstanceKey(brokerResponse.getWorkflowInstanceKey())
            .build();

    return CreateWorkflowInstanceResponse.newBuilder().setInfo(info).build();
  }

  public static CancelWorkflowInstanceResponse toCancelWorkflowInstanceResponse(
      long key, WorkflowInstanceRecord brokerResponse) {
    return CancelWorkflowInstanceResponse.getDefaultInstance();
  }

  public static UpdateWorkflowInstancePayloadResponse toUpdateWorkflowInstancePayloadResponse(
      long key, WorkflowInstanceRecord brokerResponse) {
    return UpdateWorkflowInstancePayloadResponse.getDefaultInstance();
  }

  public static ListWorkflowsResponse toListWorkflowsResponse(
      long key, io.zeebe.protocol.impl.data.repository.ListWorkflowsResponse brokerResponse) {
    final ListWorkflowsResponse.Builder builder = ListWorkflowsResponse.newBuilder();
    brokerResponse
        .getWorkflows()
        .forEach(
            workflowMetadata ->
                builder
                    .addWorkflowsBuilder()
                    .setBpmnProcessId(bufferAsString(workflowMetadata.getBpmnProcessId()))
                    .setVersion(workflowMetadata.getVersion())
                    .setWorkflowKey(workflowMetadata.getWorkflowKey())
                    .setResourceName(bufferAsString(workflowMetadata.getResourceName()))
                    .build());

    return builder.build();
  }

  public static GetWorkflowResponse toGetWorkflowResponse(
      long key, WorkflowMetadataAndResource brokerResponse) {
    return GetWorkflowResponse.newBuilder()
        .setBpmnProcessId(bufferAsString(brokerResponse.getBpmnProcessId()))
        .setVersion(brokerResponse.getVersion())
        .setWorkflowKey(brokerResponse.getWorkflowKey())
        .setResourceName(bufferAsString(brokerResponse.getResourceName()))
        .setBpmnXml(bufferAsString(brokerResponse.getBpmnXml()))
        .build();
  }

  public static ActivateJobsResponse toActivateJobsResponse(
      long key, JobBatchRecord brokerResponse) {
    final ActivateJobsResponse.Builder responseBuilder = ActivateJobsResponse.newBuilder();

    final Iterator<LongValue> jobKeys = brokerResponse.jobKeys().iterator();
    final Iterator<JobRecord> jobs = brokerResponse.jobs().iterator();

    while (jobKeys.hasNext() && jobs.hasNext()) {
      final LongValue jobKey = jobKeys.next();
      final JobRecord job = jobs.next();
      final ActivatedJob activatedJob =
          ActivatedJob.newBuilder()
              .setKey(jobKey.getValue())
              .setType(bufferAsString(job.getType()))
              .setJobHeaders(fromBrokerJobHeaders(job.getHeaders()))
              .setCustomHeaders(bufferAsJson(job.getCustomHeaders()))
              .setWorker(bufferAsString(job.getWorker()))
              .setRetries(job.getRetries())
              .setDeadline(job.getDeadline())
              .setPayload(bufferAsJson(job.getPayload()))
              .build();

      responseBuilder.addJobs(activatedJob);
    }

    return responseBuilder.build();
  }

  public static ResolveIncidentResponse toResolveIncidentResponse(
      long key, IncidentRecord incident) {
    return ResolveIncidentResponse.getDefaultInstance();
  }

  private static JobHeaders fromBrokerJobHeaders(
      io.zeebe.protocol.impl.record.value.job.JobHeaders headers) {
    return JobHeaders.newBuilder()
        .setWorkflowInstanceKey(headers.getWorkflowInstanceKey())
        .setBpmnProcessId(bufferAsString(headers.getBpmnProcessId()))
        .setWorkflowDefinitionVersion(headers.getWorkflowDefinitionVersion())
        .setWorkflowKey(headers.getWorkflowKey())
        .setElementId(bufferAsString(headers.getElementId()))
        .setElementInstanceKey(headers.getElementInstanceKey())
        .build();
  }

  private static String bufferAsJson(DirectBuffer customHeaders) {
    return MSG_PACK_CONVERTER.convertToJson(bufferAsArray(customHeaders));
  }

  @FunctionalInterface
  public interface BrokerResponseMapper<BrokerResponseDto, GrpcResponse> {
    GrpcResponse apply(long key, BrokerResponseDto responseDto);
  }
}
