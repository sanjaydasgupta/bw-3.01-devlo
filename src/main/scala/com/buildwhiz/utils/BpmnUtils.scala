package com.buildwhiz.utils

import java.io.InputStream

import org.camunda.bpm.engine.ProcessEngines
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.repository.{ProcessDefinition, Resource}
import org.camunda.bpm.model.bpmn.BpmnModelInstance

import scala.collection.JavaConverters._

trait BpmnUtils {

  def getProcessDefinition(de: DelegateExecution): ProcessDefinition = {
    val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService
    val allProcessDefinitions: Seq[ProcessDefinition] =
      repositoryService.createProcessDefinitionQuery().latestVersion().list().asScala
    allProcessDefinitions.find(_.getId == de.getProcessDefinitionId).head
  }

  def getProcessDefinition(bpmnName: String): ProcessDefinition = {
    val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService
    val allProcessDefinitions: Seq[ProcessDefinition] =
      repositoryService.createProcessDefinitionQuery().latestVersion().list().asScala
    allProcessDefinitions.find(_.getKey == bpmnName).head
  }

  def getBpmnName(de: DelegateExecution): String = getProcessDefinition(de).getKey

  def getProcessModel(bpmnName: String): InputStream = {
    val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService
    repositoryService.getProcessModel(getProcessDefinition(bpmnName).getId)
  }

  def getProcessDiagram(bpmnName: String): InputStream = {
    val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService
    repositoryService.getProcessDiagram(getProcessDefinition(bpmnName).getId)
  }

  def bpmnModelInstance(bpmnName: String): BpmnModelInstance = {
    val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService
    repositoryService.getBpmnModelInstance(getProcessDefinition(bpmnName).getId)
  }

  def getDeployedResources: Seq[Resource] = {
    val processEngine = ProcessEngines.getDefaultProcessEngine
    val managementService = processEngine.getManagementService
    val deployments = managementService.getRegisteredDeployments
    val repositoryService = processEngine.getRepositoryService
    deployments.asScala.toIndexedSeq.flatMap(d => repositoryService.getDeploymentResources(d).asScala)
  }

  def hasActiveProcesses(projectId: String): Boolean = {
    val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
    val children = rts.createProcessInstanceQuery().variableValueEquals("project_id", projectId).list()
    !children.isEmpty
  }

}
