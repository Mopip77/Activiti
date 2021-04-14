/*
 * Copyright 2010-2020 Alfresco Software, Ltd.
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

package org.activiti.engine.impl.delegate;

import java.io.Serializable;

import org.activiti.engine.api.internal.Internal;
import org.activiti.engine.delegate.DelegateExecution;

/**

 // CM：BPMN承载类行为，
 // BPMN6里是承载类实体（ActivityImpl），用于流程虚拟机，和bpmnModel完全一样，但是是完全独立的两个类，但是一个用于表示，一个用于运行调度
 // BPMN7里已经没有这个区分了，只有bpmnModel
 // 但是，具体的model的执行行为（userTask、serviceTask的实际执行）会变成一个个不同的行为，每个行为都有具体的execute方法，并且具有行为的model持有它自身的行为
 // 例如执行到scriptTask的时候，serviceTask.getBehavior().execute()会通过类似地方法去实际执行任务，并流转流程

 */
@Internal
public interface ActivityBehavior extends Serializable {

  void execute(DelegateExecution execution);
}
