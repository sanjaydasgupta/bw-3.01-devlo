package com.buildwhiz.baf

import java.util
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3, Utils}
import com.sun.org.apache.xerces.internal.parsers.DOMParser
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines
import org.camunda.bpm.engine.repository.ProcessDefinition
import org.w3c.dom
import org.w3c.dom.{Element, Node, NodeList}
import org.xml.sax.InputSource

import scala.collection.JavaConversions._
import scala.language.implicitConversions

class PhaseAdd extends HttpServlet with Utils {

  private implicit def nodeList2nodeSeq(nl: NodeList): Seq[Node] = (0 until nl.getLength).map(nl.item)

  private def getVariableDefinitions(processNameAndDocument: (String, dom.Document)):
      Seq[(String, String, String, Any, String)] = { // bpmn-name, variable-name, type, default-value, label
    BWLogger.log(getClass.getName, "getVariableDefinitions", "ENTRY")
    try {

      val converters: Map[String, String => Any] =
        Map("B" -> (s => s.toBoolean), "L" -> (s => s.toLong), "D" -> (s => s.toDouble))

      def getVariableNameAndType(variableNode: Node): (String, String, String, Any, String) = {
        // bpmn-name, variable-name, type, default-value, label
        val nameAndType = variableNode.getAttributes.getNamedItem("value").getTextContent
        val parts = nameAndType.split(":")
        // variable-name, type, default-value, label
        if (parts.length != 4)
          throw new IllegalArgumentException(s"BAD Variable Specification: $nameAndType")
        if (!converters.contains(parts(1)))
          throw new IllegalArgumentException(s"BAD Variable Type: ${parts(1)}")
        (processNameAndDocument._1, parts(0), parts(1), converters(parts(1))(parts(2)), parts(3))
      }

      val phaseVariableNodes: Seq[Node] = processNameAndDocument._2.getElementsByTagName("camunda:property").
        filter(_.getAttributes.getNamedItem("name").getTextContent == "bw-variable")
      val variableNamesAndTypes = phaseVariableNodes.map(getVariableNameAndType)
      BWLogger.log(getClass.getName, "getVariableDefinitions", s"""EXIT-OK (${variableNamesAndTypes.mkString(", ")})""")
      variableNamesAndTypes
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "getVariableDefinitions", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        t.printStackTrace()
        throw t
    }
  }

  private def getTimerDefinitions(processNameAndDocument: (String, dom.Document)): Seq[(String, String, String)] = {
    BWLogger.log(getClass.getName, "getTimerDefinitions", "ENTRY")
    try {

      def getNameAndVariableName(timerNode: Node, prefix: String): (String, String, String) = {
        // bpmn, name, process-variable
        val name = timerNode.getAttributes.getNamedItem("name").getTextContent
        val processVariableName = timerNode.asInstanceOf[Element].getElementsByTagName(s"$prefix:timeDuration").
          find(n => n.getAttributes.getNamedItem("xsi:type").getTextContent == s"$prefix:tFormalExpression" &&
          n.getTextContent.matches("\\$\\{.+\\}")).map(_.getTextContent.replaceAll("[\\$\\{\\}]", "")).head
        (processNameAndDocument._1, name, processVariableName)
      }

      val prefix = processNameAndDocument._2.getDocumentElement.getTagName.split(":")(0)
      val phaseTimerNodes: Seq[Node] = processNameAndDocument._2.getElementsByTagName(s"$prefix:intermediateCatchEvent").
        filter(_.getChildNodes.exists(_.getLocalName == "timerEventDefinition"))
      val timerNamesAndVariables = phaseTimerNodes.map(n => getNameAndVariableName(n, prefix))
      BWLogger.log(getClass.getName, "getTimerDefinitions", s"""EXIT-OK (${timerNamesAndVariables.mkString(", ")})""")
      timerNamesAndVariables
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "getTimerDefinitions", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        t.printStackTrace()
        throw t
    }
  }

  private def getActivityNamesAndRoles(processNameAndDocument: (String, dom.Document)): Seq[(String, String, String)] = {
    // bpmn, activity-name, role
    BWLogger.log(getClass.getName, "getActivityNamesAndRoles", "ENTRY")
    try {
      def getNameAndRole(callActivity: Node): (String, String, String) = {
        //val name = callActivity.getAttributes.getNamedItem("name").getTextContent.replaceAll("[\\s-]+", "")
        val name = callActivity.getAttributes.getNamedItem("name").getTextContent.replaceAll("[\\s]+", " ")
        val role = callActivity.asInstanceOf[Element].getElementsByTagName("camunda:property").
          find(_.getAttributes.getNamedItem("name").getTextContent == "bw-role").
          map(_.getAttributes.getNamedItem("value").getTextContent) match {
          case Some(r) => r
          case None => "phase-manager"
        }
        (processNameAndDocument._1, name, role)
      }
      val prefix = processNameAndDocument._2.getDocumentElement.getTagName.split(":")(0)
      val callActivities: Seq[Node] = processNameAndDocument._2.getElementsByTagName(s"$prefix:callActivity")
      val phaseActivityNodes = callActivities.filter(_.getAttributes.getNamedItem("calledElement").
        getTextContent == "Infra-Activity-Handler")
      val activityNamesAndRoles = phaseActivityNodes.map(getNameAndRole)
      BWLogger.log(getClass.getName, "getActivityNamesAndRoles", s"""EXIT-OK (${activityNamesAndRoles.mkString(", ")})""")
      activityNamesAndRoles
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "getActivityNamesAndRoles", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        t.printStackTrace()
        throw t
    }
  }

  private def getProcessesByName(processName: String, processDocuments: Seq[(String, dom.Document)] = Seq.empty):
      Seq[(String, dom.Document)] = {

    def nameAndDocument(bpmnName: String): (String, dom.Document) = {
      val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService
      val allProcessDefinitions: Seq[ProcessDefinition] =
        repositoryService.createProcessDefinitionQuery().latestVersion().list()
      val processDefinition = allProcessDefinitions.find(_.getKey == bpmnName).head
      val modelInputStream = repositoryService.getProcessModel(processDefinition.getId)
      val domParser = new DOMParser()
      domParser.parse(new InputSource(modelInputStream))
      (bpmnName, domParser.getDocument)
    }

    BWLogger.log(getClass.getName, "getInvolvedProcesses", "ENTRY")
    try {
      val processNameAndDocument = nameAndDocument(processName)
      val prefix = processNameAndDocument._2.getDocumentElement.getTagName.split(":")(0)
      val callActivities: Seq[Node] = processNameAndDocument._2.getElementsByTagName(s"$prefix:callActivity")
      val calledElementNames = callActivities.map(_.getAttributes.getNamedItem("calledElement").getTextContent)
      val subProcessNames = calledElementNames.filterNot(_ == "Infra-Activity-Handler")
      val allProcessDocuments = subProcessNames.foldLeft(processDocuments)((docs, name) => getProcessesByName(name, docs))
      BWLogger.log(getClass.getName, "getInvolvedProcesses", s"""EXIT-OK (${subProcessNames.mkString(", ")})""")
      processNameAndDocument +: allProcessDocuments
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "getInvolvedProcesses", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        t.printStackTrace()
        throw t
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val phaseName = parameters("phase_name")
      val projectOid = new ObjectId(parameters("project_id"))
      val adminPersonOid = new ObjectId(parameters("admin_person_id"))
      val processNamesAndDocuments = getProcessesByName(s"Phase-$phaseName")
      val variables: DocumentList = processNamesAndDocuments.flatMap(getVariableDefinitions).map(kv =>
      {val doc: Document = Map("bpmn_name" -> kv._1, "name" -> kv._2, "type" -> kv._3, "value" -> kv._4, "label" -> kv._5); doc})
      val timers: DocumentList = processNamesAndDocuments.flatMap(getTimerDefinitions).map(kv =>
        {val doc: Document = Map("bpmn_name" -> kv._1, "name" -> kv._2, "variable" -> kv._3, "duration" -> "00:00:00"); doc})
      val newPhase: Document = Map("name" -> phaseName, "status" -> "defined", "bpmn_name" -> s"Phase-$phaseName",
        "activity_ids" -> new util.ArrayList[ObjectId], "admin_person_id" -> adminPersonOid,
        "timestamps" -> Map("created" -> System.currentTimeMillis), "timers" -> timers, "variables" -> variables)
      //BWLogger.log(getClass.getName, "doPost", s"""Timers: ${timers.map(_.name[String]).mkString("[", ", ", "]")}""", request)
      BWMongoDB3.phases.insertOne(newPhase)
      val phaseOid = newPhase.y._id[ObjectId]
      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
        Map("$push" -> Map("phase_ids" -> phaseOid)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      val namesAndRoles = processNamesAndDocuments.flatMap(getActivityNamesAndRoles)
      //BWLogger.log(getClass.getName, "doPost", s"""Activity-Names-n-Roles: ${namesAndRoles.mkString("[", ", ", "]")}""", request)
      for ((bpmn, activityName, activityRole) <- namesAndRoles) {
        val availableDocumentList = Seq("56f124dfd5d8ad25b1325b42", "56f124dfd5d8ad25b1325b3a",
          "56f124dfd5d8ad25b1325b3b").map(id => new ObjectId(id))
        val inbox = new java.util.ArrayList[ObjectId]
        inbox.addAll(availableDocumentList)
        val action: Document = Map("bpmn_name" -> bpmn, "name" -> activityName, "type" -> "main", "status" -> "defined",
          "inbox" -> inbox, "outbox" -> new java.util.ArrayList[ObjectId],
          "assignee_person_id" -> adminPersonOid, "duration" -> "00:00:00")
        val actions = new java.util.ArrayList[Document]
        actions.append(action)
        val activity: Document = Map("bpmn_name" -> bpmn, "name" -> activityName, "actions" -> actions, "status" -> "defined",
          "role" -> activityRole, "description" -> s"This is activity '$activityName'. Placeholder for description")
        BWMongoDB3.activities.insertOne(activity)
        val activityOid = activity.getObjectId("_id")
        val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
          Map("$push" -> Map("activity_ids" -> activityOid)))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      }
      val newPhaseDocument = OwnedPhases.processPhase(newPhase, adminPersonOid).asDoc
      response.getWriter.println(bson2json(newPhaseDocument))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

}
