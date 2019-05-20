/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor;

import io.zeebe.util.metrics.Metric;
import io.zeebe.util.metrics.MetricsManager;

public class StreamProcessorMetrics {
  private final Metric eventsProcessedCountMetric;
  private final Metric eventsWrittenCountMetric;
  private final Metric eventsSkippedCountMetric;
  private final SnapshotMetrics snapshotMetrics;

  public StreamProcessorMetrics(
      final MetricsManager metricsManager, final String processorName, final String partitionId) {
    eventsProcessedCountMetric =
        metricsManager
            .newMetric("streamprocessor_events_count")
            .type("counter")
            .label("processor", processorName)
            .label("action", "processed")
            .label("partition", partitionId)
            .create();

    eventsWrittenCountMetric =
        metricsManager
            .newMetric("streamprocessor_events_count")
            .type("counter")
            .label("processor", processorName)
            .label("action", "written")
            .label("partition", partitionId)
            .create();

    eventsSkippedCountMetric =
        metricsManager
            .newMetric("streamprocessor_events_count")
            .type("counter")
            .label("processor", processorName)
            .label("action", "skipped")
            .label("partition", partitionId)
            .create();

    snapshotMetrics = new SnapshotMetrics(metricsManager, processorName, partitionId);
  }

  public void close() {
    eventsProcessedCountMetric.close();
    eventsSkippedCountMetric.close();
    eventsWrittenCountMetric.close();
    snapshotMetrics.close();
  }

  public void incrementEventsProcessedCount() {
    eventsProcessedCountMetric.incrementOrdered();
  }

  public void incrementEventsSkippedCount() {
    eventsSkippedCountMetric.incrementOrdered();
  }

  public void incrementEventsWrittenCount() {
    eventsWrittenCountMetric.incrementOrdered();
  }

  public SnapshotMetrics getSnapshotMetrics() {
    return snapshotMetrics;
  }
}
