package com.buildwhiz.baf3

import com.buildwhiz.baf2.{PersonApi, PhaseApi, ProjectApi}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, BpmnUtils, HttpUtils}
import com.buildwhiz.infra.DynDoc._

import java.io.PrintWriter
import java.util.TimeZone
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty
import org.camunda.bpm.model.bpmn.instance.{CallActivity, EndEvent, FlowElement, IntermediateCatchEvent,
    IntermediateThrowEvent, MultiInstanceLoopCharacteristics, StartEvent, Task, TimeDuration, TimerEventDefinition,
    UserTask}

class PhaseAdd extends HttpServlet with HttpUtils with BpmnUtils {
//  private implicit def nodeList2nodeSeq(nl: NodeList): Seq[Node] = (0 until nl.getLength).map(nl.item)

//  private def extensionProperties(e: Element, name: String): Seq[Node] = e.getElementsByTagName("camunda:property").
//      filter(nameAttribute(_) == name)
//
  private def extensionProperties2(e: FlowElement, name: String): Seq[CamundaProperty] =
    e.getChildElementsByType(classOf[CamundaProperty]).asScala.toSeq.filter(_.getAttributeValue("name") == name)

  private def cleanText(txt: String): String = txt.replaceAll("\\s+", " ").replaceAll("&#10;", " ")

//  private def getAttribute(node: Node, attributeName: String): String = {
//    node.getAttributes match {
//      case null => ""
//      case attributes: NamedNodeMap => attributes.getNamedItem(attributeName) match {
//        case null => ""
//        case valueItem: Node => valueItem.getTextContent match {
//          case null => ""
//          case textContent: String => textContent
//        }
//      }
//    }
//  }
//
//  private def valueAttribute(node: Node): String = {
//    getAttribute(node, "value")
//  }
//
  private def valueAttribute2(node: CamundaProperty): String = {
    node.getAttributeValue("value")
  }

//  private def nameAttribute(node: Node): String = {
//    getAttribute(node, "name")
//  }
//
//  case class CallerBpmnDom(callerName: String, bpmnName: String, theDom: dom.Document)

//  def bpmnDom(bpmnName: String): dom.Document = {
//    val modelInputStream = getProcessModel(bpmnName)
//    val domParser = new DOMParser()
//    domParser.parse(new InputSource(modelInputStream))
//    domParser.getDocument
//  }
//
  private def date2long(date: String, timeZone: String): Long = {
    if (date.isEmpty)
      -1L
    else {
      val Array(year, month, day) = date.split("-").map(_.toInt)
      val calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone(timeZone))
      calendar.set(year, month - 1, day)
      calendar.getTimeInMillis
    }
  }

  private def addTimer(timerNode: IntermediateCatchEvent, bpmnName: String, namePath: String, idPath: String,
      timerBuffer: mutable.Buffer[Document]): Unit = {
    val name = cleanText(timerNode.getName)
    val bpmnId = timerNode.getId
    val timeDuration: TimeDuration = timerNode.getEventDefinitions.asScala.headOption match {
      case Some(ted: TimerEventDefinition) => ted.getTimeDuration
      case _ =>
        val textContent = timerNode.getTextContent
        val elementType = timerNode.getElementType.getTypeName
        val baseType = timerNode.getElementType.getBaseType.getTypeName
        val timerInfo = s"elementType: $elementType, baseType: $baseType, /text: $textContent"
        throw new IllegalArgumentException(s"Not found TimeDuration($bpmnName, '$name', $bpmnId): $timerInfo")
    }

    val timerVariableName = if (timeDuration.getTextContent.matches("\\$\\{.+\\}")) {
      timeDuration.getTextContent.replaceAll("[${}]", "")
    } else {
      val textContent = timeDuration.getTextContent
      val elementType = timeDuration.getElementType.getTypeName
      val baseType = timeDuration.getElementType.getBaseType.getTypeName
      val timerInfo = s"elementType: $elementType, baseType: $baseType, /text: $textContent"
      throw new IllegalArgumentException(s"Bad TimeDuration($bpmnName, '$name', $bpmnId): $timerInfo")
    }
    val duration = extensionProperties2(timerNode, "bw-duration") match {
      case dur +: _ => valueAttribute2(dur)
      case Nil => "00:00:00"
    }
    val fullBpmnName = if (idPath == ".") {
      bpmnName
    } else {
      idPath.substring(2) + "/" + bpmnName
    }
    val timer: Document = Map("bpmn_name_full" -> fullBpmnName, "bpmn_name" -> bpmnName, "name" -> name,
        "variable" -> timerVariableName,
        "bpmn_id" -> bpmnId, "duration" -> duration, "start" -> "00:00:00", "end" -> "00:00:00", "status" -> "defined",
        "full_path_name" -> s"$namePath/$name", "full_path_id" -> s"$idPath/$bpmnId")
    timerBuffer.append(timer)
  }

  private def addMilestone(milestoneNode: IntermediateThrowEvent, bpmnName: String, namePath: String, idPath: String,
      milestoneBuffer: mutable.Buffer[Document]): Unit = {
    val name = cleanText(milestoneNode.getName)
    val bpmnId = milestoneNode.getId
    val fullBpmnName = if (idPath == ".") {
      bpmnName
    } else {
      idPath.substring(2) + "/" + bpmnName
    }
    val milestone: Document = Map("bpmn_name_full" -> fullBpmnName, "bpmn_name" -> bpmnName, "name" -> name,
      "bpmn_id" -> bpmnId,
      "start" -> "00:00:00", "end" -> "00:00:00", "status" -> "defined",
      "full_path_name" -> s"$namePath/$name", "full_path_id" -> s"$idPath/$bpmnId")
    milestoneBuffer.append(milestone)
  }

  private def addEndNode(endNode: EndEvent, bpmnName: String, namePath: String, idPath: String,
      endNodeBuffer: mutable.Buffer[Document]): Unit = {
    val name = cleanText(endNode.getName)
    val bpmnId = endNode.getId
    val fullBpmnName = if (idPath == ".") {
      bpmnName
    } else {
      idPath.substring(2) + "/" + bpmnName
    }
    val end: Document = Map("bpmn_name_full" -> fullBpmnName, "bpmn_name" -> bpmnName, "name" -> name,
      "bpmn_id" -> bpmnId,
      "start" -> "00:00:00", "end" -> "00:00:00", "status" -> "defined",
      "full_path_name" -> s"$namePath/$name", "full_path_id" -> s"$idPath/$bpmnId")
    endNodeBuffer.append(end)
  }

  private def addStartNode(startNode: StartEvent, bpmnName: String, namePath: String, idPath: String,
      startNodeBuffer: mutable.Buffer[Document]): Unit = {
    val name = cleanText(startNode.getName)
    val bpmnId = startNode.getId
    val fullBpmnName = if (idPath == ".") {
      bpmnName
    } else {
      idPath.substring(2) + "/" + bpmnName
    }
    val start: Document = Map("bpmn_name_full" -> fullBpmnName, "bpmn_name" -> bpmnName, "name" -> name,
      "bpmn_id" -> bpmnId,
      "start" -> "00:00:00", "end" -> "00:00:00", "status" -> "defined",
      "full_path_name" -> s"$namePath/$name", "full_path_id" -> s"$idPath/$bpmnId")
    startNodeBuffer.append(start)
  }

  private def addActivity(activityNode: Task, bpmnName: String, namePath: String, idPath: String,
      timeZone: String, isTakt: Boolean, activityBuffer: mutable.Buffer[Document]): Unit = {
    val name = cleanText(activityNode.getName)
    val bpmnId = activityNode.getId
    val role = extensionProperties2(activityNode, "bw-role") match {
      case r +: _ => valueAttribute2(r)
      //case Nil | null => "phase-manager"
      case Nil | null => "none"
    }
    val description = extensionProperties2(activityNode, "bw-description") match {
      case d +: _ => valueAttribute2(d).replaceAll("\"", "\'")
      case Nil | null => s"$name (no description provided)"
    }
    val bpmnDuration = extensionProperties2(activityNode, "bw-duration") match {
      case dur +: _ => valueAttribute2(dur)
      case Nil | null => "00:00:00"
    }
    val bpmnActualStart = extensionProperties2(activityNode, "bw-actual-start") match {
      case Nil | null => ""
      case start +: _ => valueAttribute2(start)
    }
    val bpmnActualEnd = extensionProperties2(activityNode, "bw-actual-end") match {
      case Nil | null => ""
      case end +: _ => valueAttribute2(end)
    }
    val bpmnScheduledStart = extensionProperties2(activityNode, "bw-scheduled-start") match {
      case Nil | null => ""
      case start +: _ => valueAttribute2(start)
    }
    val bpmnScheduledEnd = extensionProperties2(activityNode, "bw-scheduled-end") match {
      case Nil | null => ""
      case end +: _ => valueAttribute2(end)
    }
    val durations: Document = Map("optimistic" -> -1, "pessimistic" -> -1, "likely" -> -1, "actual" -> -1)
    val fullBpmnName = if (idPath == ".") {
      bpmnName
    } else {
      idPath.substring(2) + "/" + bpmnName
    }
    val atEnd: Boolean = activityNode.getSucceedingNodes.filterByType(classOf[EndEvent]).count() > 0
    val activity: Document = Map("status" -> "defined", "name" -> name, "bpmn_id" -> bpmnId, "at_end" -> atEnd,
        "full_path_name" -> s"$namePath/$name", "full_path_id" -> s"$idPath/$bpmnId", "is_takt" -> isTakt,
        "bpmn_name_full" -> fullBpmnName, "bpmn_name" -> bpmnName, "role" -> role, "description" -> description,
        "start" -> "00:00:00", "end" -> "00:00:00", "duration" -> bpmnDuration, "durations" -> durations,
        "bpmn_scheduled_start_date" -> date2long(bpmnScheduledStart, timeZone),
        "bpmn_scheduled_end_date" -> date2long(bpmnScheduledEnd, timeZone),
        "bpmn_actual_start_date" -> date2long(bpmnActualStart, timeZone), "takt_unit_no" -> 1,
        "bpmn_actual_end_date" -> date2long(bpmnActualEnd, timeZone), "on_critical_path" -> false)
    activityBuffer.append(activity)
  }

  private def addVariable(variableNode: CamundaProperty, bpmnName: String, idPath: String,
      variableBuffer: mutable.Buffer[Document]): Unit = {
    val nameAndType = variableNode.getAttributeValue("value")
    val parts = nameAndType.split(":")
    val converters: Map[String, String => Any] =
      Map("B" -> (s => s.toBoolean), "L" -> (s => s.toLong), "D" -> (s => s.toDouble), "S" -> (s => s))
    val (variableName, variableType, defaultValue, label) =
        (parts(0), parts(1), converters(parts(1))(parts(2)), parts(3))
    val fullBpmnName = if (idPath == ".") {
      bpmnName
    } else {
      idPath.substring(2) + "/" + bpmnName
    }
    val variable: Document = Map("bpmn_name_full" -> fullBpmnName, "bpmn_name" -> bpmnName, "name" -> variableName,
      "type" -> variableType,
      "value" -> defaultValue, "label" -> label)
    variableBuffer.append(variable)
  }

  private def addCallElement(callElementNode: CallActivity, bpmnName: String, idPath: String,
      callElementBuffer: mutable.Buffer[Document]): Unit = {
    val isTakt = callElementNode.getLoopCharacteristics match {
      case _: MultiInstanceLoopCharacteristics => true
      case _ => false
    }
    val callee = callElementNode.getCalledElement
    val callerElementId = callElementNode.getId
    val callerElementName = cleanText(callElementNode.getName)
    val fullBpmnName = if (idPath == ".") {
      callerElementId + "/" + callee
    } else {
      idPath.substring(2) + "/" + callerElementId + "/" + callee
    }
    val fullBpmnName2 = if (idPath == ".") {
      bpmnName
    } else {
      idPath.substring(2) + "/" + bpmnName
    }
    val callDetails: Document = Map("name" -> callee, "bpmn_name_full" -> fullBpmnName, "parent_name" -> bpmnName,
      "parent_activity_id" -> callerElementId, "offset" -> Map("start" -> "00:00:00", "end" -> "00:00:00"),
      "status" -> "defined", "is_takt" -> isTakt, "parent_activity_name" -> callerElementName,
      "bpmn_name_full2" -> fullBpmnName2)
    callElementBuffer.append(callDetails)
  }

  private def analyzeBpmn(bpmnName: String, namePath: String, idPath: String, responseWriter: PrintWriter,
      activityBuffer: mutable.Buffer[Document], timerBuffer: mutable.Buffer[Document],
      milestoneBuffer: mutable.Buffer[Document], endNodeBuffer: mutable.Buffer[Document],
      startNodeBuffer: mutable.Buffer[Document],
      variableBuffer: mutable.Buffer[Document], callElementBuffer: mutable.Buffer[Document],
      timeZone: String, isTakt: Boolean, request: HttpServletRequest): Unit = {
    val level = idPath.split("/").length
    val margin = "&nbsp;&nbsp;&nbsp;|" * level
    //val theDom = bpmnDom(bpmnName)
    //val prefix = theDom.getDocumentElement.getTagName.split(":")(0)
    val bpmnModel = bpmnModelInstance(bpmnName)
    //val bpmnPrefix = bpmnModel.getDocument.getRootElement.getPrefix
    //BWLogger.log(getClass.getName, request.getMethod, s"bpmnPrefix: '$bpmnPrefix'", request)
    val bpmnTasks: Seq[Task] = bpmnModel.getModelElementsByType(classOf[Task]).asScala.toSeq
    val bpmnUserTasks: Seq[UserTask] = bpmnModel.getModelElementsByType(classOf[UserTask]).asScala.toSeq
    val allTasks = bpmnTasks ++ bpmnUserTasks

    //val activityNodes: Seq[Element] = (theDom.getElementsByTagName(s"$prefix:userTask") ++
    //    theDom.getElementsByTagName(s"$prefix:task")).map(_.asInstanceOf[Element])
    if (allTasks.nonEmpty) {
      for (activityNode <- allTasks) {
        val name = cleanText(activityNode.getAttributeValue("name"))
        //val name = cleanText(nameAttribute(activityNode))
        val bpmnId = activityNode.getAttributeValue("id")
        //val bpmnId = activityNode.getAttributes.getNamedItem("id").getTextContent
        if (responseWriter != null) {
          responseWriter.println(s"""$margin$namePath($bpmnName) ACTIVITY:$name[$bpmnId]<br/>""")
        }
        addActivity(activityNode, bpmnName, namePath, idPath, timeZone, isTakt, activityBuffer)
      }
    } else {
      if (responseWriter != null) {
        responseWriter.println(s"""$margin$namePath($bpmnName) NO-ACTIVITIES<br/>""")
      }
    }

    val timerNodes: Seq[IntermediateCatchEvent] = bpmnModel.getModelElementsByType(classOf[IntermediateCatchEvent]).asScala.toSeq
    if (timerNodes.nonEmpty) {
      for (timerNode <- timerNodes) {
        val name = cleanText(timerNode.getAttributeValue("name"))
        val bpmnId = timerNode.getAttributeValue("id")
        if (responseWriter != null) {
          responseWriter.println(s"""$margin$namePath($bpmnName) TIMER:$name[$bpmnId]<br/>""")
        }
        addTimer(timerNode, bpmnName, namePath, idPath, timerBuffer)
      }
    } else {
      if (responseWriter != null) {
        responseWriter.println(s"""$margin$namePath($bpmnName) NO-TIMERS<br/>""")
      }
    }

    val endNodes: Seq[EndEvent] = bpmnModel.getModelElementsByType(classOf[EndEvent]).asScala.toSeq
    if (endNodes.nonEmpty) {
      for (endNode <- endNodes) {
        val name = cleanText(endNode.getAttributeValue("name"))
        val bpmnId = endNode.getAttributeValue("id")
        if (responseWriter != null) {
          responseWriter.println(s"""$margin$namePath($bpmnName) END:$name[$bpmnId]<br/>""")
        }
        addEndNode(endNode, bpmnName, namePath, idPath, endNodeBuffer)
      }
    } else {
      if (responseWriter != null) {
        responseWriter.println(s"""$margin$namePath($bpmnName) NO-ENDS<br/>""")
      }
    }

    val startNodes: Seq[StartEvent] = bpmnModel.getModelElementsByType(classOf[StartEvent]).asScala.toSeq
    if (startNodes.nonEmpty) {
      for (startNode <- startNodes) {
        val name = cleanText(startNode.getAttributeValue("name"))
        val bpmnId = startNode.getAttributeValue("id")
        if (responseWriter != null) {
          responseWriter.println(s"""$margin$namePath($bpmnName) START:$name[$bpmnId]<br/>""")
        }
        addStartNode(startNode, bpmnName, namePath, idPath, startNodeBuffer)
      }
    } else {
      if (responseWriter != null) {
        responseWriter.println(s"""$margin$namePath($bpmnName) NO-STARTS<br/>""")
      }
    }

    val milestoneNodes: Seq[IntermediateThrowEvent] = bpmnModel.getModelElementsByType(classOf[IntermediateThrowEvent]).
        asScala.toSeq
    if (milestoneNodes.nonEmpty) {
      for (milestoneNode <- milestoneNodes) {
        val name = cleanText(milestoneNode.getAttributeValue("name"))
        val bpmnId = milestoneNode.getAttributeValue("id")
        if (responseWriter != null) {
          responseWriter.println(s"""$margin$namePath($bpmnName) MILESTONE:$name[$bpmnId]<br/>""")
        }
        addMilestone(milestoneNode, bpmnName, namePath, idPath, milestoneBuffer)
      }
    } else {
      if (responseWriter != null) {
        responseWriter.println(s"""$margin$namePath($bpmnName) NO-MILESTONES<br/>""")
      }
    }

    val variableNodes: Seq[CamundaProperty] = bpmnModel.getModelElementsByType(classOf[CamundaProperty]).asScala.toSeq.
      filter(_.getAttributeValue("name") == "bw-variable")
    if (variableNodes.nonEmpty) {
      for (variableNode <- variableNodes) {
        val name = cleanText(variableNode.getAttributeValue("name"))
        if (responseWriter != null) {
          responseWriter.println(s"""$margin$namePath($bpmnName) VARIABLE:$name[???]<br/>""")
        }
        addVariable(variableNode, bpmnName, idPath, variableBuffer)
      }
    } else {
      if (responseWriter != null) {
        responseWriter.println(s"""$margin$namePath($bpmnName) NO-VARIABLES<br/>""")
      }
    }

    val callActivityNodes: Seq[CallActivity] = bpmnModel.getModelElementsByType(classOf[CallActivity]).asScala.toSeq.
        filter(_.getAttributeValue("calledElement") != "Infra-Activity-Handler")

    if (callActivityNodes.nonEmpty) {
      for (callElementNode <- callActivityNodes) {
        val name = cleanText(callElementNode.getAttributeValue("name"))
        val bpmnId = callElementNode.getAttributeValue("id")
        if (responseWriter != null) {
          responseWriter.println(s"""$margin$namePath($bpmnName) CALL:$name[$bpmnId]<br/>""")
        }
        addCallElement(callElementNode, bpmnName, idPath, callElementBuffer)
      }
    } else {
      if (responseWriter != null) {
        responseWriter.println(s"""$margin$namePath($bpmnName) NO-CALLS<br/>""")
      }
    }

    for (call <- callActivityNodes) {
      val calledBpmnName = call.getCalledElement
      val callerElementName = call.getName
      val callerElementId = call.getId
      val newNamePath = s"$namePath/${cleanText(callerElementName)}"
      val newIdPath = s"$idPath/$callerElementId"
      val isTakt2 = call.getLoopCharacteristics match {
        case _: MultiInstanceLoopCharacteristics => true
        case _ => isTakt
      }
      analyzeBpmn(calledBpmnName, newNamePath, newIdPath, responseWriter, activityBuffer, timerBuffer,
        milestoneBuffer, endNodeBuffer, startNodeBuffer, variableBuffer, callElementBuffer, timeZone, isTakt2, request)
    }
  }

  private def addProcess(user: DynDoc, bpmnName: String, processName: String, phaseOid: ObjectId,
      request: HttpServletRequest): Unit = {
    val thePhase = PhaseApi.phaseById(phaseOid)
    if (!PersonApi.isBuildWhizAdmin(Right(user)))
      throw new IllegalArgumentException("Not permitted")

    val activityBuffer = mutable.Buffer[Document]()
    val timerBuffer: mutable.Buffer[Document] = mutable.Buffer[Document]()
    val milestoneBuffer = mutable.Buffer[Document]()
    val endNodeBuffer = mutable.Buffer[Document]()
    val startNodeBuffer = mutable.Buffer[Document]()
    val variableBuffer = mutable.Buffer[Document]()
    val callElementBuffer = mutable.Buffer[Document]()

    val phaseTimezone = PhaseApi.timeZone(thePhase, Some(request))
    analyzeBpmn(bpmnName, ".", ".", null, activityBuffer, timerBuffer, milestoneBuffer, endNodeBuffer,
      startNodeBuffer, variableBuffer, callElementBuffer, phaseTimezone, isTakt = false, request)

    val newProcess: Document = Map("name" -> processName, "status" -> "defined", "bpmn_name" -> bpmnName,
      "admin_person_id" -> user._id[ObjectId], "process_version" -> -1,
      "timestamps" -> Map("created" -> System.currentTimeMillis), "timers" -> timerBuffer.asJava,
      "variables" -> variableBuffer.asJava,
      "bpmn_timestamps" -> callElementBuffer.asJava, "start" -> "00:00:00", "end" -> "00:00:00",
      "assigned_roles" -> Seq.empty[Document], "milestones" -> milestoneBuffer.asJava,
      "end_nodes" -> endNodeBuffer.asJava, "start_nodes" -> startNodeBuffer.asJava)

    BWMongoDB3.processes.insertOne(newProcess)
    val processOid = newProcess.y._id[ObjectId]
    val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
      Map("$push" -> Map("process_ids" -> processOid)))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    if (activityBuffer.nonEmpty) {
      BWMongoDB3.tasks.insertMany(activityBuffer.asJava)
    }
    val lastActivityOid = activityBuffer.filter(_.y.at_end[Boolean]).
      find(a => a.y.bpmn_name[String] == a.y.bpmn_name_full[String]) match {
      case Some(a) => a.y._id[ObjectId]
      case _ => activityBuffer.find(a => a.y.bpmn_name[String] == a.y.bpmn_name_full[String]) match {
        case Some(a) => a.y._id[ObjectId]
        case _ => activityBuffer.headOption match {
          case Some(a) => a.y._id[ObjectId]
          case _ => throw new IllegalArgumentException("Cant find last activity/task for milestone")
        }
      }
    }
    val activityOids = activityBuffer.map(_.getObjectId("_id")).asJava
    val updateResult2 = BWMongoDB3.processes.updateOne(Map("_id" -> processOid),
      Map("$set" -> Map("activity_ids" -> activityOids, "milestone_activity_id" -> lastActivityOid)))
    if (updateResult2.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val responseWriter = response.getWriter
    try {
      val parameters = getParameterMap(request)
      val bpmnName = parameters("bpmn_name")
      response.setContentType("text/html")
      responseWriter.println("<html><tt><br/>")
      val activityBuffer = mutable.Buffer[Document]()
      val timerBuffer: mutable.Buffer[Document] = mutable.Buffer[Document]()
      val milestoneBuffer = mutable.Buffer[Document]()
      val endNodeBuffer = mutable.Buffer[Document]()
      val startNodeBuffer = mutable.Buffer[Document]()
      val variableBuffer = mutable.Buffer[Document]()
      val callElementBuffer = mutable.Buffer[Document]()

      analyzeBpmn(bpmnName, ".", ".", responseWriter, activityBuffer, timerBuffer,
        milestoneBuffer, endNodeBuffer, startNodeBuffer, variableBuffer, callElementBuffer, "GMT", isTakt = false, request)
      responseWriter.println(s"<b>Total activities: ${activityBuffer.length}</b><br/>")
      for (activity <- activityBuffer) {
        responseWriter.println(s"${activity.toJson}<br/>")
      }
      responseWriter.println(s"<b>Total timers: ${timerBuffer.length}</b><br/>")
      for (timer <- timerBuffer) {
        responseWriter.println(s"${timer.toJson}<br/>")
      }
      responseWriter.println(s"<b>Total milestones: ${milestoneBuffer.length}</b><br/>")
      for (milestone <- milestoneBuffer) {
        responseWriter.println(s"${milestone.toJson}<br/>")
      }
      responseWriter.println(s"<b>Total end-nodes: ${endNodeBuffer.length}</b><br/>")
      for (endNode <- endNodeBuffer) {
        responseWriter.println(s"${endNode.toJson}<br/>")
      }
      responseWriter.println(s"<b>Total start-nodes: ${startNodeBuffer.length}</b><br/>")
      for (startNode <- startNodeBuffer) {
        responseWriter.println(s"${startNode.toJson}<br/>")
      }
      responseWriter.println(s"<b>Total variable-nodes: ${variableBuffer.length}</b><br/>")
      for (variableNode <- variableBuffer) {
        responseWriter.println(s"${variableNode.toJson}<br/>")
      }
      responseWriter.println(s"<b>Total call-element-nodes: ${callElementBuffer.length}</b><br/>")
      for (callElementNode <- callElementBuffer) {
        responseWriter.println(s"${callElementNode.toJson}<br/>")
      }
    } catch {
      case t: Throwable =>
        t.printStackTrace(responseWriter)
    }
    responseWriter.println("</tt></html><br/>")
    responseWriter.flush()
  }

  private def addPhase(user: DynDoc, phaseName: String, parentProjectOid: ObjectId, description: String,
                       phaseManagerOids: Seq[ObjectId]): ObjectId = {

    val userOid = user._id[ObjectId]
    val isProjectAdmin = ProjectApi.isAdmin(userOid, ProjectApi.projectById(parentProjectOid))
    if (!PersonApi.isBuildWhizAdmin(Right(user)) && !isProjectAdmin)
      throw new IllegalArgumentException("Not permitted")

    val badManagerIds = phaseManagerOids.filterNot(PersonApi.exists)
    if (badManagerIds.nonEmpty)
      throw new IllegalArgumentException(s"""Bad project_manager_ids: ${badManagerIds.mkString(", ")}""")

    val managersInRoles = phaseManagerOids.map(oid =>
      new Document("role_name", "Project-Manager").append("person_id", oid)
    ).asJava

    val newPhaseRecord: DynDoc = Map("name" -> phaseName, "process_ids" -> Seq.empty[ObjectId],
      "assigned_roles" -> managersInRoles, "status" -> "defined", "admin_person_id" -> phaseManagerOids.head,
      "timestamps" -> Map("created" -> System.currentTimeMillis), "description" -> description)
    BWMongoDB3.phases.insertOne(newPhaseRecord.asDoc)

    val newPhaseOid = newPhaseRecord._id[ObjectId]
    val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> parentProjectOid),
      Map("$addToSet" -> Map("phase_ids" -> newPhaseOid)))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

    newPhaseOid
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val parameters = getParameterMap(request)
      val parentProjectOid = new ObjectId(parameters("project_id"))
      if (!ProjectApi.exists(parentProjectOid))
        throw new IllegalArgumentException(s"Unknown project-id: '$parentProjectOid'")
      val user: DynDoc = getUser(request)
      val phaseName = parameters("phase_name")
      PhaseApi.validateNewName(phaseName, parentProjectOid)
      val description = parameters.get("description") match {
        case Some(desc) => desc
        case None => s"No description provided for '$phaseName'"
      }
      val phaseManagerOids: Seq[ObjectId] = parameters.get("manager_ids") match {
        case None => Seq(user._id[ObjectId])
        case Some(ids) => ids.split(",").map(_.trim).filter(_.nonEmpty).distinct.map(new ObjectId(_)).toSeq
      }
      val bpmnName = "Phase-" + parameters("bpmn_name")
      val processName = s"$phaseName:$bpmnName"

      BWMongoDB3.withTransaction({
        val phaseOid = addPhase(user, phaseName, parentProjectOid, description, phaseManagerOids)
        addProcess(user, bpmnName, processName, phaseOid, request)
      })
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
    response.getWriter.print(successJson())
    response.setContentType("application/json")
    BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
  }

}