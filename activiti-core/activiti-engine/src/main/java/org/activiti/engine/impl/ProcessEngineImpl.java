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

package org.activiti.engine.impl;

import java.util.Map;

import org.activiti.engine.DynamicBpmnService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.ManagementService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngines;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.engine.delegate.event.impl.ActivitiEventBuilder;
import org.activiti.engine.impl.asyncexecutor.AsyncExecutor;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.cfg.TransactionContextFactory;
import org.activiti.engine.impl.interceptor.CommandExecutor;
import org.activiti.engine.impl.interceptor.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ProcessEngineImpl implements ProcessEngine {

  private static Logger log = LoggerFactory.getLogger(ProcessEngineImpl.class);

  protected String name;
  protected RepositoryService repositoryService;
  protected RuntimeService runtimeService;
  protected HistoryService historicDataService;
  protected TaskService taskService;
  protected ManagementService managementService;
  protected DynamicBpmnService dynamicBpmnService;
  protected AsyncExecutor asyncExecutor;
  protected CommandExecutor commandExecutor;
  protected Map<Class<?>, SessionFactory> sessionFactories;
  protected TransactionContextFactory transactionContextFactory;
  protected ProcessEngineConfigurationImpl processEngineConfiguration;

  public ProcessEngineImpl(ProcessEngineConfigurationImpl processEngineConfiguration) {
      // CM: 流程引擎实例又绑定配置对象
    this.processEngineConfiguration = processEngineConfiguration;
    this.name = processEngineConfiguration.getProcessEngineName();
      // CM: 所有的服务类都是通过配置对象生成的，门面模式
    this.repositoryService = processEngineConfiguration.getRepositoryService();
    this.runtimeService = processEngineConfiguration.getRuntimeService();
    this.historicDataService = processEngineConfiguration.getHistoryService();
    this.taskService = processEngineConfiguration.getTaskService();
    this.managementService = processEngineConfiguration.getManagementService();
    this.dynamicBpmnService = processEngineConfiguration.getDynamicBpmnService();
    this.asyncExecutor = processEngineConfiguration.getAsyncExecutor();
    this.commandExecutor = processEngineConfiguration.getCommandExecutor();
    this.sessionFactories = processEngineConfiguration.getSessionFactories();
      // CM: 事务工厂上下文类
    this.transactionContextFactory = processEngineConfiguration.getTransactionContextFactory();

    if (processEngineConfiguration.isUsingRelationalDatabase() && processEngineConfiguration.getDatabaseSchemaUpdate() != null) {
      commandExecutor.execute(processEngineConfiguration.getSchemaCommandConfig(), new SchemaOperationsProcessEngineBuild());
    }

    if (name == null) {
      log.info("default activiti ProcessEngine created");
    } else {
      log.info("ProcessEngine {} created", name);
    }

      // CM: 注册流程引擎到ProcessEngines
    ProcessEngines.registerProcessEngine(this);

      // CM: 开启job执行器
    if (asyncExecutor != null && asyncExecutor.isAutoActivate()) {
      asyncExecutor.start();
    }

      // CM: 根据自己配置的DatabaseSchemaUpdate执行activiti的DDL
    if (processEngineConfiguration.getProcessEngineLifecycleListener() != null) {
      processEngineConfiguration.getProcessEngineLifecycleListener().onProcessEngineBuilt(this);
    }

      // CM: 发送流程启动信号
    processEngineConfiguration.getEventDispatcher().dispatchEvent(ActivitiEventBuilder.createGlobalEvent(ActivitiEventType.ENGINE_CREATED));
  }

  public void close() {
      // CM: 所有的执行流程和create完全相反，很清晰
    ProcessEngines.unregister(this);
    if (asyncExecutor != null && asyncExecutor.isActive()) {
      asyncExecutor.shutdown();
    }

    commandExecutor.execute(processEngineConfiguration.getSchemaCommandConfig(), new SchemaOperationProcessEngineClose());

    if (processEngineConfiguration.getProcessEngineLifecycleListener() != null) {
      processEngineConfiguration.getProcessEngineLifecycleListener().onProcessEngineClosed(this);
    }

    processEngineConfiguration.getEventDispatcher().dispatchEvent(ActivitiEventBuilder.createGlobalEvent(ActivitiEventType.ENGINE_CLOSED));
  }

  // getters and setters
  // //////////////////////////////////////////////////////

  public String getName() {
    return name;
  }

  public ManagementService getManagementService() {
    return managementService;
  }

  public TaskService getTaskService() {
    return taskService;
  }

  public HistoryService getHistoryService() {
    return historicDataService;
  }

  public RuntimeService getRuntimeService() {
    return runtimeService;
  }

  public RepositoryService getRepositoryService() {
    return repositoryService;
  }

  public DynamicBpmnService getDynamicBpmnService() {
    return dynamicBpmnService;
  }

  public ProcessEngineConfigurationImpl getProcessEngineConfiguration() {
    return processEngineConfiguration;
  }

}
