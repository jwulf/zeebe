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
package io.zeebe.db.impl;

import io.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class DbBufferView implements DbValue {

  private final MutableDirectBuffer value = new UnsafeBuffer(0, 0);

  public void wrapBuffer(DirectBuffer buffer, int offset, int length) {
    value.wrap(buffer, offset, length);
  }

  public void wrapBuffer(DirectBuffer buffer) {
    value.wrap(buffer);
  }

  public DirectBuffer getValue() {
    return value;
  }

  @Override
  public int getLength() {
    return value.capacity();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    buffer.putBytes(offset, value, 0, value.capacity());
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    value.wrap(buffer, offset, length);
  }
}
