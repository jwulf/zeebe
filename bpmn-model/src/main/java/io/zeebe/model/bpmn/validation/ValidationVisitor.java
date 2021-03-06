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
package io.zeebe.model.bpmn.validation;

import io.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.zeebe.model.bpmn.traversal.TypeHierarchyVisitor;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.model.xml.impl.validation.ValidationResultsCollectorImpl;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResults;

public class ValidationVisitor extends TypeHierarchyVisitor {

  private final Map<Class<?>, ModelElementValidator<?>> validators;

  private ValidationResultsCollectorImpl resultCollector;

  public ValidationVisitor(Collection<ModelElementValidator<?>> validators) {
    this.validators = new HashMap<>();
    validators.forEach(v -> this.validators.put(v.getElementType(), v));
    resultCollector = new ValidationResultsCollectorImpl();
  }

  @Override
  protected void visit(ModelElementType implementedType, BpmnModelElementInstance instance) {

    resultCollector.setCurrentElement(instance);

    final ModelElementValidator validator = validators.get(implementedType.getInstanceType());
    if (validator != null) {
      validator.validate(instance, resultCollector);
    }
  }

  public void reset() {
    this.resultCollector = new ValidationResultsCollectorImpl();
  }

  public ValidationResults getValidationResult() {
    return resultCollector.getResults();
  }
}
