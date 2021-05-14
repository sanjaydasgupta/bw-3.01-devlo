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
      repositoryService.createProcessDefinitionQuery().list().asScala
    allProcessDefinitions.find(_.getId == de.getProcessDefinitionId).head
  }

  def getProcessDefinition(bpmnName: String, version: Int = -1): ProcessDefinition = {
    //BWLogger.log(getClass.getName, s"getProcessDefinition($bpmnName)", "ENTRY")
    val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService
    //BWLogger.log(getClass.getName, "getProcessDefinition", "Got RepositoryService")
    val allProcessDefinitions: Seq[ProcessDefinition] =
      repositoryService.createProcessDefinitionQuery().list().asScala.filter(_.getKey == bpmnName)
    val expectedVersion = if (version == -1)
      allProcessDefinitions.map(_.getVersion).max
    else
      version
    //BWLogger.log(getClass.getName, "getProcessDefinition", "Got allProcessDefinitions")
    val processDefinition = allProcessDefinitions.find(_.getVersion == expectedVersion) match {
      case Some(pd) => pd
      case None =>
        val message = s"process '$bpmnName' not found among ${allProcessDefinitions.map(_.getKey).mkString(", ")}"
        throw new IllegalArgumentException(message)
    }
    //BWLogger.log(getClass.getName, "getProcessDefinition", "EXIT")
    processDefinition
  }

  def getBpmnName(de: DelegateExecution): String = getProcessDefinition(de).getKey

  def getProcessModel(bpmnName: String, version: Int = -1): InputStream = {
    //BWLogger.log(getClass.getName, s"getProcessModel($bpmnName)", "ENTRY")
    val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService
    //BWLogger.log(getClass.getName, "getProcessModel", "Got RepositoryService")
    val inputStream = repositoryService.getProcessModel(getProcessDefinition(bpmnName, version).getId)
    //BWLogger.log(getClass.getName, "getProcessModel", "EXIT")
    inputStream
  }

  def getProcessDiagram(bpmnName: String, version: Int = -1): InputStream = {
    val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService
    repositoryService.getProcessDiagram(getProcessDefinition(bpmnName, version).getId)
  }

  def bpmnModelInstance(bpmnName: String, version: Int = -1): BpmnModelInstance = {
    val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService
    repositoryService.getBpmnModelInstance(getProcessDefinition(bpmnName, version).getId)
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
