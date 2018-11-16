/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.processor.flownode;

import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutputBehavior;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.MsgPackMergeTool;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import org.agrona.DirectBuffer;

public class IOMappingHelper {

  public <T extends ExecutableFlowNode> void applyOutputMappings(
      WorkflowState state, BpmnStepContext<T> context) {
    final T element = context.getElement();
    final MsgPackMergeTool mergeTool = context.getMergeTool();
    final WorkflowInstanceRecord record = context.getValue();
    final ZeebeOutputBehavior outputBehavior = element.getOutputBehavior();

    final DirectBuffer scopePayload =
        state
            .getElementInstanceState()
            .getVariablesState()
            .getVariablesAsDocument(record.getScopeInstanceKey());
    mergeTool.reset();

    final DirectBuffer propagatedPayload;
    if (outputBehavior != ZeebeOutputBehavior.none) {
      if (element.getOutputBehavior() != ZeebeOutputBehavior.overwrite) {
        mergeTool.mergeDocument(scopePayload);
      }

      mergeTool.mergeDocumentStrictly(record.getPayload(), element.getOutputMappings());
      propagatedPayload = mergeTool.writeResultToBuffer();

    } else {
      propagatedPayload = scopePayload;
    }

    state
        .getElementInstanceState()
        .getVariablesState()
        .setVariablesFromDocument(record.getScopeInstanceKey(), propagatedPayload);

    record.setPayload(propagatedPayload);
  }

  public <T extends ExecutableFlowNode> void applyInputMappings(
      WorkflowState state, BpmnStepContext<T> context) {

    final WorkflowInstanceRecord value = context.getValue();
    final MsgPackMergeTool mergeTool = context.getMergeTool();
    final T element = context.getElement();
    final Mapping[] mappings = element.getInputMappings();

    if (mappings.length > 0) {
      mergeTool.reset();
      mergeTool.mergeDocumentStrictly(value.getPayload(), element.getInputMappings());
      final DirectBuffer mappedPayload = mergeTool.writeResultToBuffer();
      context.getValue().setPayload(mappedPayload);

      state
          .getElementInstanceState()
          .getVariablesState()
          .setVariablesLocalFromDocument(context.getRecord().getKey(), mappedPayload);
    }
  }
}
