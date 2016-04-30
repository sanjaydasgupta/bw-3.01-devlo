package com.buildwhiz

import java.io.InputStream

import org.camunda.bpm.engine.ProcessEngines
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.repository.{ProcessDefinition, Resource}

import scala.collection.JavaConversions._

trait BpmnUtils {

  def getProcessDefinition(de: DelegateExecution): ProcessDefinition = {
    val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService
    val allProcessDefinitions: Seq[ProcessDefinition] =
      repositoryService.createProcessDefinitionQuery()/*.latestVersion()*/.list()
    allProcessDefinitions.find(_.getId == de.getProcessDefinitionId).head
  }

  def getProcessDefinition(bpmnName: String): ProcessDefinition = {
    val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService
    val allProcessDefinitions: Seq[ProcessDefinition] =
      repositoryService.createProcessDefinitionQuery()/*.latestVersion()*/.list()
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

  def getDeployedResources: Seq[Resource] = {
    val processEngine = ProcessEngines.getDefaultProcessEngine
    val managementService = processEngine.getManagementService
    val deployments = managementService.getRegisteredDeployments
    val repositoryService = processEngine.getRepositoryService
    deployments.toIndexedSeq.flatMap(repositoryService.getDeploymentResources)
  }

  def hasActiveProcesses(projectId: String): Boolean = {
    val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
    val children = rts.createProcessInstanceQuery().variableValueEquals("project_id", projectId).list()
    children.nonEmpty
  }

}
