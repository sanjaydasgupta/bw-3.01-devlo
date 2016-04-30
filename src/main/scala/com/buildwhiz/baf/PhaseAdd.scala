package com.buildwhiz.baf

import java.util
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.{BpmnUtils, HttpUtils}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.sun.org.apache.xerces.internal.parsers.DOMParser
import org.bson.Document
import org.bson.types.ObjectId
import org.w3c.dom
import org.w3c.dom.{Element, Node, NodeList}
import org.xml.sax.InputSource

import scala.collection.JavaConversions._
import scala.language.implicitConversions

class PhaseAdd extends HttpServlet with HttpUtils with BpmnUtils {

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

      def getNameAndVariableName(timerNode: Element, prefix: String): (String, String, String) = {
        // bpmn, name, process-variable
        val name = timerNode.getAttributes.getNamedItem("name").getTextContent
        val processVariableName = timerNode/*.asInstanceOf[Element]*/.getElementsByTagName(s"$prefix:timeDuration").
          find(n => n.getAttributes.getNamedItem("xsi:type").getTextContent == s"$prefix:tFormalExpression" &&
          n.getTextContent.matches("\\$\\{.+\\}")).map(_.getTextContent.replaceAll("[\\$\\{\\}]", "")).head
        (processNameAndDocument._1, name, processVariableName)
      }

      val prefix = processNameAndDocument._2.getDocumentElement.getTagName.split(":")(0)
      val phaseTimerNodes: Seq[Element] = processNameAndDocument._2.getElementsByTagName(s"$prefix:intermediateCatchEvent").
        filter(_.getChildNodes.exists(_.getLocalName == "timerEventDefinition")).map(_.asInstanceOf[Element])
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

  private def getActivityNamesRolesAndDescriptions(processNameAndDocument: (String, dom.Document)):
      Seq[(String, String, String, String)] = {
    // bpmn, activity-name, role, description
    BWLogger.log(getClass.getName, "getActivityNamesAndRoles", "ENTRY")
    try {
      def sequence(callActivity: Element): Int = callActivity.getElementsByTagName("camunda:property").
        find(_.getAttributes.getNamedItem("name").getTextContent == "bw-sequence").
        map(_.getAttributes.getNamedItem("value").getTextContent).head.toInt
      def getNameRoleAndDescription(callActivity: Element): (String, String, String, String) = {
        //val name = callActivity.getAttributes.getNamedItem("name").getTextContent.replaceAll("[\\s-]+", "")
        val name = callActivity.getAttributes.getNamedItem("name").getTextContent.replaceAll("[\\s]+", " ")
        val role = callActivity/*.asInstanceOf[Element]*/.getElementsByTagName("camunda:property").
          find(_.getAttributes.getNamedItem("name").getTextContent == "bw-role").
          map(_.getAttributes.getNamedItem("value").getTextContent) match {
          case Some(r) => r
          case None => "phase-manager"
        }
        val description = callActivity/*.asInstanceOf[Element]*/.getElementsByTagName("camunda:property").
          find(_.getAttributes.getNamedItem("name").getTextContent == "bw-description").
          map(_.getAttributes.getNamedItem("value").getTextContent) match {
          case Some(d) => d
          case None => s"no description provided ($name)"
        }
        (processNameAndDocument._1, name, role, description)
      }
      val prefix = processNameAndDocument._2.getDocumentElement.getTagName.split(":")(0)
      val bpmnCallActivities: Seq[Element] = processNameAndDocument._2.getElementsByTagName(s"$prefix:callActivity").
        map(_.asInstanceOf[Element])
      val buildWhizActivities = bpmnCallActivities.filter(_.getAttributes.getNamedItem("calledElement").
        getTextContent == "Infra-Activity-Handler")
      val activityNamesRolesAndDescriptions = buildWhizActivities.sortWith((a, b) => sequence(a) < sequence(b)).
        map(getNameRoleAndDescription)
      BWLogger.log(getClass.getName, "getActivityNamesAndRoles", s"""EXIT-OK (${activityNamesRolesAndDescriptions.mkString(", ")})""")
      activityNamesRolesAndDescriptions
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
      val modelInputStream = getProcessModel(bpmnName)
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
      val namesRolesAndDescriptions = processNamesAndDocuments.flatMap(getActivityNamesRolesAndDescriptions)
      //BWLogger.log(getClass.getName, "doPost", s"""Activity-Names-n-Roles: ${namesAndRoles.mkString("[", ", ", "]")}""", request)
      for ((bpmn, activityName, activityRole, activityDescription) <- namesRolesAndDescriptions) {
        //val availableDocumentList = Seq("56f124dfd5d8ad25b1325b42", "56f124dfd5d8ad25b1325b3a",
        //  "56f124dfd5d8ad25b1325b3b").map(id => new ObjectId(id))
        val availableDocumentList = Seq("57207549d5d8ad331d2ea699", "57207549d5d8ad331d2ea69a",
          "57207549d5d8ad331d2ea69b").map(id => new ObjectId(id))
        val inbox = new java.util.ArrayList[ObjectId]
        inbox.addAll(availableDocumentList)
        val action: Document = Map("bpmn_name" -> bpmn, "name" -> activityName, "type" -> "main", "status" -> "defined",
          "inbox" -> inbox, "outbox" -> new java.util.ArrayList[ObjectId],
          "assignee_person_id" -> adminPersonOid, "duration" -> "00:00:00")
        val actions = new java.util.ArrayList[Document]
        actions.append(action)
        val activity: Document = Map("bpmn_name" -> bpmn, "name" -> activityName, "actions" -> actions, "status" -> "defined",
          "role" -> activityRole, "description" -> activityDescription)
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
