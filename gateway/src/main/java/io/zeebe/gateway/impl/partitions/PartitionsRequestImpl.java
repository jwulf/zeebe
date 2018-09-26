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
package io.zeebe.gateway.impl.partitions;

import io.zeebe.gateway.api.commands.Partitions;
import io.zeebe.gateway.api.commands.PartitionsRequestStep1;
import io.zeebe.gateway.impl.ControlMessageRequest;
import io.zeebe.gateway.impl.RequestManager;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import java.util.Collections;

public class PartitionsRequestImpl extends ControlMessageRequest<Partitions>
    implements PartitionsRequestStep1 {

  public PartitionsRequestImpl(final RequestManager client) {
    super(client, ControlMessageType.REQUEST_PARTITIONS, PartitionsImpl.class);

    setTargetPartition(Protocol.DEPLOYMENT_PARTITION);
  }

  @Override
  public Object getRequest() {
    return Collections.EMPTY_MAP;
  }
}