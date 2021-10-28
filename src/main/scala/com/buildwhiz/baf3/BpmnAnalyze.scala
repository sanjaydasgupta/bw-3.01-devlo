package com.buildwhiz.baf3

import com.buildwhiz.utils.{BpmnUtils, HttpUtils}
import com.buildwhiz.infra.DynDoc._
import com.sun.org.apache.xerces.internal.parsers.DOMParser
import org.w3c.dom
import org.w3c.dom.{Element, NamedNodeMap, Node, NodeList}
import org.xml.sax.InputSource

import java.io.PrintWriter
import java.util.TimeZone
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.language.implicitConversions
import scala.collection.mutable
import org.bson.Document

class BpmnAnalyze extends HttpServlet with HttpUtils with BpmnUtils {
  private implicit def nodeList2nodeSeq(nl: NodeList): Seq[Node] = (0 until nl.getLength).map(nl.item)

  private def extensionProperties(e: Element, name: String): Seq[Node] = e.getElementsByTagName("camunda:property").
      filter(_.getAttributes.getNamedItem("name").getTextContent == name)

  private def cleanText(txt: String): String = txt.replaceAll("\\s+", " ").replaceAll("&#10;", " ")

  private def getAttribute(node: Node, attributeName: String): String = {
    node.getAttributes match {
      case null => ""
      case attributes: NamedNodeMap => attributes.getNamedItem(attributeName) match {
        case null => ""
        case valueItem: Node => valueItem.getTextContent match {
          case null => ""
          case textContent: String => textContent
        }
      }
    }
  }

  private def valueAttribute(node: Node): String = {
    getAttribute(node, "value")
  }

  case class CallerBpmnDom(callerName: String, bpmnName: String, theDom: dom.Document)

  def bpmnDom(bpmnName: String): dom.Document = {
    val modelInputStream = getProcessModel(bpmnName)
    val domParser = new DOMParser()
    domParser.parse(new InputSource(modelInputStream))
    domParser.getDocument
  }

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

  private def addTimer(timerNode: Element, bpmnName: String, namePath: String, idPath: String, timeZone: String,
      prefix: String, timerBuffer: mutable.Buffer[Document]): Unit = {
    val name = timerNode.getAttributes.getNamedItem("name").getTextContent
    val bpmnId = timerNode.getAttributes.getNamedItem("id").getTextContent
    val timerVariableName = timerNode.getElementsByTagName(s"$prefix:timeDuration").
      find(n => n.getAttributes.getNamedItem("xsi:type").getTextContent == s"$prefix:tFormalExpression" &&
        n.getTextContent.matches("\\$\\{.+\\}")).map(_.getTextContent.replaceAll("[${}]", "")).head
    val duration = extensionProperties(timerNode, "bw-duration") match {
      case dur +: _ => valueAttribute(dur)
      case Nil => "00:00:00"
    }
    val timer: Document = Map("bpmn_name" -> bpmnName, "name" -> name, "variable" -> timerVariableName,
        "bpmn_id" -> bpmnId, "duration" -> duration, "start" -> "00:00:00", "end" -> "00:00:00", "status" -> "defined",
        "full_path_name" -> s"$namePath/${cleanText(name)}", "full_path_id" -> s"$idPath/$bpmnId")
    timerBuffer.append(timer)
  }

  private def addMilestone(milestoneNode: Element, bpmnName: String, namePath: String, idPath: String,
      timeZone: String, milestoneBuffer: mutable.Buffer[Document]): Unit = {
    val name = milestoneNode.getAttributes.getNamedItem("name").getTextContent
    val bpmnId = milestoneNode.getAttributes.getNamedItem("id").getTextContent
    val milestone: Document = Map("bpmn_name" -> bpmnName, "name" -> name, "bpmn_id" -> bpmnId,
      "start" -> "00:00:00", "end" -> "00:00:00", "status" -> "defined",
      "full_path_name" -> s"$namePath/${cleanText(name)}", "full_path_id" -> s"$idPath/$bpmnId")
    milestoneBuffer.append(milestone)
  }

  private def addEndNode(endNode: Element, bpmnName: String, namePath: String, idPath: String,
      timeZone: String, endNodeBuffer: mutable.Buffer[Document]): Unit = {
    val name = endNode.getAttributes.getNamedItem("name").getTextContent
    val bpmnId = endNode.getAttributes.getNamedItem("id").getTextContent
    val end: Document = Map("bpmn_name" -> bpmnName, "name" -> name, "bpmn_id" -> bpmnId,
      "start" -> "00:00:00", "end" -> "00:00:00", "status" -> "defined",
      "full_path_name" -> s"$namePath/${cleanText(name)}", "full_path_id" -> s"$idPath/$bpmnId")
    endNodeBuffer.append(end)
  }

  private def addActivity(activityNode: Element, bpmnName: String, namePath: String, idPath: String,
       timeZone: String, activityBuffer: mutable.Buffer[Document]): Unit = {
    val name = activityNode.getAttributes.getNamedItem("name").getTextContent
    val bpmnId = activityNode.getAttributes.getNamedItem("id").getTextContent
    val role = extensionProperties(activityNode, "bw-role") match {
      case r +: _ => valueAttribute(r)
      //case Nil | null => "phase-manager"
      case Nil | null => "none"
    }
    val description = extensionProperties(activityNode, "bw-description") match {
      case d +: _ => valueAttribute(d).replaceAll("\"", "\'")
      case Nil | null => s"$name (no description provided)"
    }
    val bpmnDuration = extensionProperties(activityNode, "bw-duration") match {
      case dur +: _ => valueAttribute(dur)
      case Nil | null => "00:00:00"
    }
    val bpmnActualStart = extensionProperties(activityNode, "bw-actual-start") match {
      case Nil | null => ""
      case start +: _ => valueAttribute(start)
    }
    val bpmnActualEnd = extensionProperties(activityNode, "bw-actual-end") match {
      case Nil | null => ""
      case end +: _ => valueAttribute(end)
    }
    val bpmnScheduledStart = extensionProperties(activityNode, "bw-scheduled-start") match {
      case Nil | null => ""
      case start +: _ => valueAttribute(start)
    }
    val bpmnScheduledEnd = extensionProperties(activityNode, "bw-scheduled-end") match {
      case Nil | null => ""
      case end +: _ => valueAttribute(end)
    }
    val durations: Document = Map("optimistic" -> -1, "pessimistic" -> -1, "likely" -> -1, "actual" -> -1)
    val activity: Document = Map("status" -> "defined", "name" -> name, "bpmn_id" -> bpmnId,
        "full_path_name" -> s"$namePath/${cleanText(name)}", "full_path_id" -> s"$idPath/$bpmnId",
        "bpmn_name" -> bpmnName, "role" -> role, "description" -> description,
        "start" -> "00:00:00", "end" -> "00:00:00", "duration" -> bpmnDuration, "durations" -> durations,
        "bpmn_scheduled_start_date" -> date2long(bpmnScheduledStart, timeZone),
        "bpmn_scheduled_end_date" -> date2long(bpmnScheduledEnd, timeZone),
        "bpmn_actual_start_date" -> date2long(bpmnActualStart, timeZone),
        "bpmn_actual_end_date" -> date2long(bpmnActualEnd, timeZone), "on_critical_path" -> false)
    activityBuffer.append(activity)
  }

  private def analyzeBpmn(bpmnName: String, namePath: String, idPath: String, responseWriter: PrintWriter,
      activityBuffer: mutable.Buffer[Document], timerBuffer: mutable.Buffer[Document],
      milestoneBuffer: mutable.Buffer[Document], endNodeBuffer: mutable.Buffer[Document]): Unit = {
    val level = idPath.split("/").length
    val margin = "&nbsp;&nbsp;&nbsp;|" * level
    val theDom = bpmnDom(bpmnName)
    val prefix = theDom.getDocumentElement.getTagName.split(":")(0)

    val activityNodes: Seq[Element] = (theDom.getElementsByTagName(s"$prefix:userTask") ++
        theDom.getElementsByTagName(s"$prefix:task")).map(_.asInstanceOf[Element])
    if (activityNodes.nonEmpty) {
      for (activityNode <- activityNodes) {
        val name = activityNode.getAttributes.getNamedItem("name").getTextContent
        val bpmnId = activityNode.getAttributes.getNamedItem("id").getTextContent
        responseWriter.println(s"""$margin$namePath($bpmnName) ACTIVITY:$name[$bpmnId]<br/>""")
        addActivity(activityNode, bpmnName, namePath, idPath, "GMT", activityBuffer)
      }
    } else {
      responseWriter.println(s"""$margin$namePath($bpmnName) NO-ACTIVITIES<br/>""")
    }

    val timerNodes: Seq[Element] = theDom.getElementsByTagName(s"$prefix:intermediateCatchEvent").
      filter(_.getChildNodes.exists(_.getLocalName == "timerEventDefinition")).map(_.asInstanceOf[Element])
    if (timerNodes.nonEmpty) {
      for (timerNode <- timerNodes) {
        val name = timerNode.getAttributes.getNamedItem("name").getTextContent
        val bpmnId = timerNode.getAttributes.getNamedItem("id").getTextContent
        responseWriter.println(s"""$margin$namePath($bpmnName) TIMER:$name[$bpmnId]<br/>""")
        addTimer(timerNode, bpmnName, namePath, idPath, "GMT", prefix, timerBuffer)
      }
    } else {
      responseWriter.println(s"""$margin$namePath($bpmnName) NO-TIMERS<br/>""")
    }

    val endNodes: Seq[Element] = theDom.getElementsByTagName(s"$prefix:endEvent").map(_.asInstanceOf[Element])
    if (endNodes.nonEmpty) {
      for (endNode <- endNodes) {
        val name = endNode.getAttributes.getNamedItem("name").getTextContent
        val bpmnId = endNode.getAttributes.getNamedItem("id").getTextContent
        responseWriter.println(s"""$margin$namePath($bpmnName) END:$name[$bpmnId]<br/>""")
        addEndNode(endNode, bpmnName, namePath, idPath, "GMT", endNodeBuffer)
      }
    } else {
      responseWriter.println(s"""$margin$namePath($bpmnName) NO-ENDS<br/>""")
    }

    val milestoneNodes: Seq[Element] = theDom.getElementsByTagName(s"$prefix:intermediateThrowEvent").
        map(_.asInstanceOf[Element])
    if (milestoneNodes.nonEmpty) {
      for (milestoneNode <- milestoneNodes) {
        val name = milestoneNode.getAttributes.getNamedItem("name").getTextContent
        val bpmnId = milestoneNode.getAttributes.getNamedItem("id").getTextContent
        responseWriter.println(s"""$margin$namePath($bpmnName) MILESTONE:$name[$bpmnId]<br/>""")
        addMilestone(milestoneNode, bpmnName, namePath, idPath, "GMT", milestoneBuffer)
      }
    } else {
      responseWriter.println(s"""$margin$namePath($bpmnName) NO-MILESTONES<br/>""")
    }

    val callActivities: Seq[Node] = theDom.getElementsByTagName(s"$prefix:callActivity")
    for (call <- callActivities) {
      val calledBpmnName = call.getAttributes.getNamedItem("calledElement").getTextContent
      if (calledBpmnName != "Infra-Activity-Handler") {
        val callerElementName = call.getAttributes.getNamedItem("name").getTextContent
        val callerElementId = call.getAttributes.getNamedItem("id").getTextContent
        val newNamePath = s"$namePath/${cleanText(callerElementName)}"
        val newIdPath = s"$idPath/$callerElementId"
        analyzeBpmn(calledBpmnName, newNamePath, newIdPath, responseWriter, activityBuffer, timerBuffer,
          milestoneBuffer, endNodeBuffer)
      }
    }
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
      analyzeBpmn(bpmnName, ".", ".", responseWriter, activityBuffer, timerBuffer,
        milestoneBuffer, endNodeBuffer)
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
    } catch {
      case t: Throwable =>
        t.printStackTrace(responseWriter)
    }
    responseWriter.println("</tt></html><br/>")
    responseWriter.flush()
  }
}