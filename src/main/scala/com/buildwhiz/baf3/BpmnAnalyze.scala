package com.buildwhiz.baf3

import com.buildwhiz.utils.{BpmnUtils, HttpUtils}
import com.sun.org.apache.xerces.internal.parsers.DOMParser
import org.w3c.dom
import org.w3c.dom.{Element, NamedNodeMap, Node, NodeList}
import org.xml.sax.InputSource

import java.io.PrintWriter
import java.util.TimeZone
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.language.implicitConversions

class BpmnAnalyze extends HttpServlet with HttpUtils with BpmnUtils {
  private implicit def nodeList2nodeSeq(nl: NodeList): Seq[Node] = (0 until nl.getLength).map(nl.item)

  private def extensionProperties(e: Element, name: String): Seq[Node] = e.getElementsByTagName("camunda:property").
      filter(_.getAttributes.getNamedItem("name").getTextContent == name)

  private def cleanText(txt: String): String = txt.replaceAll("\\s+", " ").replaceAll("&#10;", " ")

  private def validateProcess(namesAndDoms: Seq[CallerBpmnDom]): Seq[String] = {

    def validateBpmn(bpmnName: String, processDom: dom.Document): Seq[String] = {
      val prefix = processDom.getDocumentElement.getTagName.split(":")(0)

      def executionListeners(e: Element): Seq[Element] = e.getElementsByTagName(s"$prefix:extensionElements").
        flatMap(_.asInstanceOf[Element].getElementsByTagName("camunda:executionListener")).
        map(_.asInstanceOf[Element])

      val startEvents: Seq[Element] = processDom.getElementsByTagName(s"$prefix:startEvent").map(_.asInstanceOf[Element])
      val startExecutionListeners = startEvents.flatMap(executionListeners)
      val startOk = startExecutionListeners.exists(listener => (listener.hasAttribute("class") &&
        listener.getAttribute("class") == "com.buildwhiz.jelly.BpmnStart") && (listener.hasAttribute("event") &&
        listener.getAttribute("event") == "end") && startEvents.length == 1 &&
        startExecutionListeners.length == 1)

      val endEvents: Seq[Element] = processDom.getElementsByTagName(s"$prefix:endEvent").map(_.asInstanceOf[Element])
      val endExecutionListeners = endEvents.flatMap(executionListeners)
      val endOk = endExecutionListeners.exists(listener => (listener.hasAttribute("class") &&
        listener.getAttribute("class") == "com.buildwhiz.jelly.BpmnEnd") && (listener.hasAttribute("event") &&
        listener.getAttribute("event") == "start") && endEvents.length == 1 &&
        endExecutionListeners.length == 1)

      val userTasks: Seq[Element] =
          processDom.getElementsByTagName(s"$prefix:userTask").map(_.asInstanceOf[Element]) ++
          processDom.getElementsByTagName(s"$prefix:task").map(_.asInstanceOf[Element])
      val userTaskStartListeners = userTasks.flatMap(executionListeners)
      val userTaskOk = userTaskStartListeners.forall(listener => (listener.hasAttribute("class") &&
        listener.getAttribute("class") == "com.buildwhiz.jelly.ActivityHandlerStart") &&
        (listener.hasAttribute("event") && listener.getAttribute("event") == "start"))

      val timerNodes: Seq[Element] = processDom.getElementsByTagName(s"$prefix:intermediateCatchEvent").
        filter(_.getChildNodes.exists(_.getLocalName == "timerEventDefinition")).map(_.asInstanceOf[Element])
      val timerOk = timerNodes.isEmpty || timerNodes.forall(timerNode => {
        val extensionElements: Seq[Element] = timerNode.getElementsByTagName(s"$prefix:extensionElements").
          map(_.asInstanceOf[Element])
        val executionListeners = extensionElements.flatMap(_.getElementsByTagName("camunda:executionListener")).
          map(_.asInstanceOf[Element])
        executionListeners.exists(listener => {
          (listener.hasAttribute("class") && listener.getAttribute("class") == "com.buildwhiz.jelly.TimerTransitions") &&
            (listener.hasAttribute("event") && listener.getAttribute("event") == "start")
        }) && executionListeners.exists(listener => {
          (listener.hasAttribute("class") && listener.getAttribute("class") == "com.buildwhiz.jelly.TimerTransitions") &&
            (listener.hasAttribute("event") && listener.getAttribute("event") == "end")
        }) && extensionElements.length == 1 && executionListeners.length == 2
      })
      Seq(startOk, endOk, timerOk, userTaskOk).zip(Seq("start", "end", "timer", "userTask")).
        filter(!_._1).map(pair => s"$bpmnName: ${pair._2}")
    }
    namesAndDoms.flatMap(nd => validateBpmn(nd.bpmnName, nd.theDom))
  }

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

  private def analyzeProcess(bpmnName: String, path: String, responseWriter: PrintWriter): Unit = {
    val level = path.split("/").length
    val margin = "&nbsp;&nbsp;&nbsp;|" * level
    val theDom = bpmnDom(bpmnName)
    val prefix = theDom.getDocumentElement.getTagName.split(":")(0)
    val activities: Seq[Node] = theDom.getElementsByTagName(s"$prefix:userTask") ++
        theDom.getElementsByTagName(s"$prefix:task")
    for (activity <- activities) {
      val name = activity.getAttributes.getNamedItem("name").getTextContent
      val bpmnId = activity.getAttributes.getNamedItem("id").getTextContent
      responseWriter.println(s"""$margin$path($bpmnName):$name[$bpmnId]<br/>""")
    }
    val callActivities: Seq[Node] = theDom.getElementsByTagName(s"$prefix:callActivity")
    for (call <- callActivities) {
      val calledBpmnName = call.getAttributes.getNamedItem("calledElement").getTextContent
      if (calledBpmnName != "Infra-Activity-Handler") {
        val callerElementName = call.getAttributes.getNamedItem("name").getTextContent
        val callerElementId = call.getAttributes.getNamedItem("id").getTextContent
        val newPath = s"$path/${cleanText(callerElementName)}[$callerElementId]"
        analyzeProcess(calledBpmnName, newPath, responseWriter)
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
      analyzeProcess(bpmnName, ".", responseWriter)
    } catch {
      case t: Throwable =>
        t.printStackTrace(responseWriter)
    }
    responseWriter.println("</tt></html><br/>")
    responseWriter.flush()
  }
}