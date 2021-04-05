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

package org.activiti.engine.impl.cmd;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.engine.delegate.event.impl.ActivitiEventBuilder;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.DeploymentEntity;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.persistence.entity.ResourceEntity;
import org.activiti.engine.impl.repository.DeploymentBuilderImpl;
import org.activiti.engine.repository.Deployment;

/**
 */
public class DeployCmd<T> implements Command<Deployment>, Serializable {

  private static final long serialVersionUID = 1L;
  protected DeploymentBuilderImpl deploymentBuilder;

  public DeployCmd(DeploymentBuilderImpl deploymentBuilder) {
    this.deploymentBuilder = deploymentBuilder;
  }

  public Deployment execute(CommandContext commandContext) {
    return executeDeploy(commandContext);
  }

  protected Deployment executeDeploy(CommandContext commandContext) {
    DeploymentEntity deployment = deploymentBuilder.getDeployment();

    deployment.setDeploymentTime(commandContext.getProcessEngineConfiguration().getClock().getCurrentTime());

    setProjectReleaseVersion(deployment);
    deployment.setVersion(1);

      // CM: 是否开启了过滤重复文档的功能
    if (deploymentBuilder.isDuplicateFilterEnabled()) {

      List<Deployment> existingDeployments = new ArrayList<Deployment>();
        // CM: 根据deployment的name查找act_re_deployment表是否已有该记录
        if (deployment.getTenantId() == null || ProcessEngineConfiguration.NO_TENANT_ID.equals(deployment.getTenantId())) {
        DeploymentEntity existingDeployment = commandContext.getDeploymentEntityManager().findLatestDeploymentByName(deployment.getName());
        if (existingDeployment != null) {
          existingDeployments.add(existingDeployment);
        }
      } else {
        List<Deployment> deploymentList = commandContext.getProcessEngineConfiguration().getRepositoryService().createDeploymentQuery().deploymentName(deployment.getName())
            .deploymentTenantId(deployment.getTenantId()).orderByDeploymentId().desc().list();

        if (!deploymentList.isEmpty()) {
          existingDeployments.addAll(deploymentList);
        }
      }

      DeploymentEntity existingDeployment = null;
      if (!existingDeployments.isEmpty()) {
        existingDeployment = (DeploymentEntity) existingDeployments.get(0);
      }

        // CM: 如果已部署流程和现有流程一模一样，则无需重新部署，否则版本号+1并部署
      if (existingDeployment != null) {
          if(deploymentsDiffer(deployment, existingDeployment)){
              applyUpgradeLogic(deployment, existingDeployment);
          } else {
              return existingDeployment;
          }
        }
    }

    deployment.setNew(true);

      // Save the data
      // CM: 将deployment写到缓存里，后续插入db
    commandContext.getDeploymentEntityManager().insert(deployment);

      // CM: 事件转发
    if (commandContext.getProcessEngineConfiguration().getEventDispatcher().isEnabled()) {
      commandContext.getProcessEngineConfiguration().getEventDispatcher().dispatchEvent(ActivitiEventBuilder.createEntityEvent(ActivitiEventType.ENTITY_CREATED, deployment));
    }

    // Deployment settings
    Map<String, Object> deploymentSettings = new HashMap<String, Object>();
    deploymentSettings.put(DeploymentSettings.IS_BPMN20_XSD_VALIDATION_ENABLED, deploymentBuilder.isBpmn20XsdValidationEnabled());
    deploymentSettings.put(DeploymentSettings.IS_PROCESS_VALIDATION_ENABLED, deploymentBuilder.isProcessValidationEnabled());

    // Actually deploy
      // CM: 真正的部署流程文档
    commandContext.getProcessEngineConfiguration().getDeploymentManager().deploy(deployment, deploymentSettings);

      // CM: 流程可以定时开启，这里使用定时器出发流程的启用
    if (deploymentBuilder.getProcessDefinitionsActivationDate() != null) {
      scheduleProcessDefinitionActivation(commandContext, deployment);
    }

      // CM: 事件转发
    if (commandContext.getProcessEngineConfiguration().getEventDispatcher().isEnabled()) {
      commandContext.getProcessEngineConfiguration().getEventDispatcher().dispatchEvent(ActivitiEventBuilder.createEntityEvent(ActivitiEventType.ENTITY_INITIALIZED, deployment));
    }

    return deployment;
  }

    private void setProjectReleaseVersion(DeploymentEntity deployment) {
        if (deploymentBuilder.hasProjectManifestSet()) {
            deployment.setProjectReleaseVersion(deploymentBuilder.getProjectManifest().getVersion());
        }
    }

  private void applyUpgradeLogic(DeploymentEntity deployment,
                                 DeploymentEntity existingDeployment) {
      if (deploymentBuilder.hasEnforcedAppVersion()) {
          deployment.setVersion(deploymentBuilder.getEnforcedAppVersion());
      } else if (deploymentBuilder.hasProjectManifestSet()) {
          deployment.setVersion(existingDeployment.getVersion() + 1);
      }
  }

  protected boolean deploymentsDiffer(DeploymentEntity deployment,
                                      DeploymentEntity saved) {

      if (deploymentBuilder.hasEnforcedAppVersion()) {
          return deploymentsDifferWhenEnforcedAppVersionIsSet(saved);
      } else if (deploymentBuilder.hasProjectManifestSet()) {
          return deploymentsDifferWhenProjectManifestIsSet(deployment, saved);
      } else {
          return deploymentsDifferDefault(deployment, saved);
      }
  }

  private boolean deploymentsDifferWhenEnforcedAppVersionIsSet(DeploymentEntity saved){
      return !deploymentBuilder.getEnforcedAppVersion().equals(saved.getVersion());
  }

  private boolean deploymentsDifferWhenProjectManifestIsSet(DeploymentEntity deployment,
                                                            DeploymentEntity saved){
      return !deployment.getProjectReleaseVersion().equals(saved.getProjectReleaseVersion());
  }

  private boolean deploymentsDifferDefault(DeploymentEntity deployment, DeploymentEntity saved){
      if (deployment.getResources() == null || saved.getResources() == null) {
          return true;
      }

      Map<String, ResourceEntity> resources = deployment.getResources();
      Map<String, ResourceEntity> savedResources = saved.getResources();

      for (String resourceName : resources.keySet()) {
          ResourceEntity savedResource = savedResources.get(resourceName);

          if (savedResource == null) {
              return true;
          }

          if (!savedResource.isGenerated()) {
              ResourceEntity resource = resources.get(resourceName);

              byte[] bytes = resource.getBytes();
              byte[] savedBytes = savedResource.getBytes();
              if (!Arrays.equals(bytes, savedBytes)) {
                  return true;
              }
          }
      }
      return false;
  }



  protected void scheduleProcessDefinitionActivation(CommandContext commandContext, DeploymentEntity deployment) {
    for (ProcessDefinitionEntity processDefinitionEntity : deployment.getDeployedArtifacts(ProcessDefinitionEntity.class)) {

        // FIXME: 看看定时器怎么实现的
      // If activation date is set, we first suspend all the process
      // definition
      SuspendProcessDefinitionCmd suspendProcessDefinitionCmd = new SuspendProcessDefinitionCmd(processDefinitionEntity, false, null, deployment.getTenantId());
      suspendProcessDefinitionCmd.execute(commandContext);

      // And we schedule an activation at the provided date
      ActivateProcessDefinitionCmd activateProcessDefinitionCmd = new ActivateProcessDefinitionCmd(processDefinitionEntity, false, deploymentBuilder.getProcessDefinitionsActivationDate(),
          deployment.getTenantId());
      activateProcessDefinitionCmd.execute(commandContext);
    }
  }

}
