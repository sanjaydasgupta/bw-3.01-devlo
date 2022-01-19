package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BpmnUtils, HttpUtils}
import com.sun.org.apache.xerces.internal.parsers.DOMParser
import org.bson.Document
import org.bson.types.ObjectId
import org.w3c.dom
import org.w3c.dom.{Element, NamedNodeMap, Node, NodeList}
import org.xml.sax.InputSource

import java.io.PrintWriter
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import scala.language.implicitConversions

object BpmnNameFullValidate extends HttpUtils with BpmnUtils {

  private implicit def nodeList2nodeSeq(nl: NodeList): Seq[Node] = (0 until nl.getLength).map(nl.item)

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

  private def nameAttribute(node: Node): String = {
    getAttribute(node, "name")
  }

  private def bpmnDom(bpmnName: String): dom.Document = {
    val modelInputStream = getProcessModel(bpmnName)
    val domParser = new DOMParser()
    domParser.parse(new InputSource(modelInputStream))
    domParser.getDocument
  }

  private def validateEndNode(margin: String, bpmnName: String, idPath: String, endNode: DynDoc,
      responseWriter: PrintWriter, go: Boolean): Unit = {
    val name = endNode.name[String]
    val bpmnId = endNode.bpmn_id[String]
    val expectedBpmnNameFull = if (idPath.isEmpty) {
      bpmnName
    } else {
      s"$idPath/$bpmnName"
    }
    endNode.get[String]("bpmn_name_full") match {
      case Some(bpmnNameFull) =>
        if (bpmnNameFull == expectedBpmnNameFull) {
          responseWriter.println(s"""$margin<font color="green">OK-EndNode:$name($bpmnId) >> """ +
            s"""$bpmnNameFull</font><br/>""")
        } else {
          responseWriter.println(s"""$margin<font color="red">Bad-value-EndNode:$name($bpmnId) >> """ +
            s"""$expectedBpmnNameFull!=$bpmnNameFull</font><br/>""".stripMargin)
        }
      case None =>
        responseWriter.println(s"""$margin<font color="red">No-Value-EndNode:$name($bpmnId) >> """ +
          s"""$expectedBpmnNameFull</font><br/>""")
    }
  }

  private def validateTimer(margin: String, bpmnName: String, idPath: String, timer: DynDoc,
      responseWriter: PrintWriter, go: Boolean): Unit = {
    val name = timer.name[String]
    val bpmnId = timer.bpmn_id[String]
    val expectedBpmnNameFull = if (idPath.isEmpty) {
      bpmnName
    } else {
      s"$idPath/$bpmnName"
    }
    timer.get[String]("bpmn_name_full") match {
      case Some(bpmnNameFull) =>
        if (bpmnNameFull == expectedBpmnNameFull) {
          responseWriter.println(s"""$margin<font color="green">OK-Timer:$name($bpmnId) >> """ +
            s"""$bpmnNameFull</font><br/>""")
        } else {
          responseWriter.println(s"""$margin<font color="red">Bad-value-Timer:$name($bpmnId) >> """ +
            s"""$expectedBpmnNameFull!=$bpmnNameFull</font><br/>""".stripMargin)
        }
      case None =>
        responseWriter.println(s"""$margin<font color="red">No-Value-Timer:$name($bpmnId) >> """ +
          s"""$expectedBpmnNameFull</font><br/>""")
    }
  }

  private def validateVariable(margin: String, bpmnName: String, idPath: String, variable: DynDoc,
      responseWriter: PrintWriter, go: Boolean): Unit = {
    val name = variable.name[String]
    val expectedBpmnNameFull = if (idPath.isEmpty) {
      bpmnName
    } else {
      s"$idPath/$bpmnName"
    }
    variable.get[String]("bpmn_name_full") match {
      case Some(bpmnNameFull) =>
        if (bpmnNameFull == expectedBpmnNameFull) {
          responseWriter.println(s"""$margin<font color="green">OK-Variable:$name >> """ +
            s"""$bpmnNameFull</font><br/>""")
        } else {
          responseWriter.println(s"""$margin<font color="red">Bad-value-Variable:$name >> """ +
            s"""$expectedBpmnNameFull!=$bpmnNameFull</font><br/>""".stripMargin)
        }
      case None =>
        responseWriter.println(s"""$margin<font color="red">No-Value-Variable:$name >> """ +
          s"""$expectedBpmnNameFull</font><br/>""")
    }
  }

  private def validateActivity(margin: String, bpmnName: String, idPath: String, activity: DynDoc,
      responseWriter: PrintWriter, go: Boolean): Unit = {
    val name = activity.name[String]
    val bpmnId = activity.bpmn_id[String]
    val expectedBpmnNameFull = if (idPath.isEmpty) {
      bpmnName
    } else {
      s"$idPath/$bpmnName"
    }
    activity.get[String]("bpmn_name_full") match {
      case Some(bpmnNameFull) =>
        if (bpmnNameFull == expectedBpmnNameFull) {
          responseWriter.println(s"""$margin<font color="green">OK-Activity:$name($bpmnId) >> """ +
            s"""$bpmnNameFull (${activity._id[ObjectId]})</font><br/>""")
        } else {
          responseWriter.println(s"""$margin<font color="red">Bad-value-Activity:$name($bpmnId) >> """ +
               s"""$expectedBpmnNameFull!=$bpmnNameFull (${activity._id[ObjectId]})</font><br/>""".stripMargin)
        }
      case None =>
        responseWriter.println(s"""$margin<font color="red">No-Value-Activity:$name($bpmnId) >> """ +
          s"""$expectedBpmnNameFull (${activity._id[ObjectId]})</font><br/>""")
    }
  }

  private def traverseBpmn(level: Int, bpmnName: String, idPath: String,
      activitiesByBpmnNameAndId: Map[(String, String), DynDoc], timersByBpmnNameAndId: Map[(String, String), DynDoc],
      endNodesByBpmnNameAndId: Map[(String, String), DynDoc], variableNodesByBpmnNameAndName: Map[(String, String), DynDoc],
      responseWriter: PrintWriter, go: Boolean): Unit = {
    val margin = "&nbsp;&nbsp;&nbsp;|" * level
    val fullBpmnName = if (idPath.isEmpty) {
      bpmnName
    } else {
      s"$idPath/$bpmnName"
    }
    responseWriter.println(s"""${margin}ENTRY traverseBpmn($fullBpmnName)<br/>""")
    val theDom = bpmnDom(bpmnName)
    val prefix = theDom.getDocumentElement.getTagName.split(":")(0)

    val activityNodes: Seq[Element] = (theDom.getElementsByTagName(s"$prefix:userTask") ++
      theDom.getElementsByTagName(s"$prefix:task")).map(_.asInstanceOf[Element])
    if (activityNodes.nonEmpty) {
      for (activityNode <- activityNodes) {
        val name = cleanText(nameAttribute(activityNode))
        val bpmnId = activityNode.getAttributes.getNamedItem("id").getTextContent
        activitiesByBpmnNameAndId.get((bpmnName, bpmnId)) match {
          case Some(activity: DynDoc) =>
            validateActivity(margin, bpmnName, idPath, activity, responseWriter, go)
          case None =>
            responseWriter.println(s"""$margin<font color="red">MISSING activity:$name[$bpmnId]</font><br/>""")
        }
      }
    } else {
      responseWriter.println(s"""$margin<font color="red">NO-ACTIVITIES in $fullBpmnName</font><br/>""")
    }

    val timerNodes: Seq[Element] = theDom.getElementsByTagName(s"$prefix:intermediateCatchEvent").
      filter(_.getChildNodes.exists(_.getLocalName == "timerEventDefinition")).map(_.asInstanceOf[Element])
    if (timerNodes.nonEmpty) {
      for (timerNode <- timerNodes) {
        val name = cleanText(nameAttribute(timerNode))
        val bpmnId = timerNode.getAttributes.getNamedItem("id").getTextContent
        timersByBpmnNameAndId.get((bpmnName, bpmnId)) match {
          case Some(timer: DynDoc) =>
            validateTimer(margin, bpmnName, idPath, timer, responseWriter, go)
          case None =>
            responseWriter.println(s"""$margin<font color="red">MISSING timer:$name[$bpmnId]</font><br/>""")
        }
      }
    } else {
      responseWriter.println(s"""$margin<font color="brown">NO-Timers in $fullBpmnName</font><br/>""")
    }

    val endNodes: Seq[Element] = theDom.getElementsByTagName(s"$prefix:endEvent").map(_.asInstanceOf[Element])
    if (endNodes.nonEmpty) {
      for (endNode <- endNodes) {
        val name = cleanText(nameAttribute(endNode))
        val bpmnId = endNode.getAttributes.getNamedItem("id").getTextContent
        endNodesByBpmnNameAndId.get((bpmnName, bpmnId)) match {
          case Some(endNode: DynDoc) =>
            validateEndNode(margin, bpmnName, idPath, endNode, responseWriter, go)
          case None =>
            responseWriter.println(s"""$margin<font color="red">MISSING EndNode:$name[$bpmnId]</font><br/>""")
        }
      }
    } else {
      responseWriter.println(s"""$margin<font color="brown">NO-EndNodes in $fullBpmnName</font><br/>""")
    }

    val variableNodes: Seq[Element] = theDom.getElementsByTagName("camunda:property").
        filter(nameAttribute(_) == "bw-variable").map(_.asInstanceOf[Element])
    if (variableNodes.nonEmpty) {
      for (variableNode <- variableNodes) {
        val name = cleanText(getAttribute(variableNode, "value").split(":").head)
        variableNodesByBpmnNameAndName.get((bpmnName, name)) match {
          case Some(varNode: DynDoc) =>
            validateVariable(margin, bpmnName, idPath, varNode, responseWriter, go)
          case None =>
            responseWriter.println(s"""$margin<font color="red">MISSING variable:$name</font><br/>""")
        }
      }
    } else {
      responseWriter.println(s"""$margin<font color="brown">NO-Variables in $fullBpmnName</font><br/>""")
    }

    val callActivityNodes: Seq[Element] = theDom.getElementsByTagName(s"$prefix:callActivity").
      filter(_.getAttributes.getNamedItem("calledElement").getTextContent != "Infra-Activity-Handler").
      map(_.asInstanceOf[Element])
    for (call <- callActivityNodes) {
      val calledBpmnName = call.getAttributes.getNamedItem("calledElement").getTextContent
      val callerElementId = call.getAttributes.getNamedItem("id").getTextContent
      val newIdPath = if (idPath.isEmpty) {
        callerElementId
      } else {
        s"$idPath/$callerElementId"
      }
      traverseBpmn(level + 1, calledBpmnName, newIdPath, activitiesByBpmnNameAndId, timersByBpmnNameAndId,
        endNodesByBpmnNameAndId, variableNodesByBpmnNameAndName, responseWriter, go)
    }

    val breaks = if (level == 1) "<br/><br/>" else "<br/>"
    responseWriter.println(s"""${margin}EXIT traverseBpmn($fullBpmnName)$breaks""")
  }

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    response.setContentType("text/html")
    val responseWriter = response.getWriter
    responseWriter.println("<html><tt><br/>")
    responseWriter.println(s"ENTRY ${getClass.getName}:main()<br/><br/>")
    val user: DynDoc = getUser(request)
    if (!PersonApi.isBuildWhizAdmin(Right(user)) || user.first_name[String] != "Sanjay") {
      throw new IllegalArgumentException("Not permitted")
    }
    val go: Boolean = args.length == 1 && args(0) == "GO"
    val processes: Seq[DynDoc] = BWMongoDB3.processes.find()
    for (process <- processes) {
      val bpmnName = process.bpmn_name[String]
      if (bpmnName != "****") {
        val activityOids = process.activity_ids[Many[ObjectId]]
        val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map($in -> activityOids)))
        val activitiesByBpmnNameAndId: Map[(String, String), DynDoc] =
          activities.map(activity => ((activity.bpmn_name[String], activity.bpmn_id[String]), activity)).toMap
        val timers: Seq[DynDoc] = process.timers[Many[Document]]
        val timersByBpmnNameAndId: Map[(String, String), DynDoc] =
          timers.map(timer => ((timer.bpmn_name[String], timer.bpmn_id[String]), timer)).toMap
        val endNodes: Seq[DynDoc] = process.end_nodes[Many[Document]]
        val endNodesByBpmnNameAndId: Map[(String, String), DynDoc] =
          endNodes.map(endNode => ((endNode.bpmn_name[String], endNode.bpmn_id[String]), endNode)).toMap
        val variableNodes: Seq[DynDoc] = process.variables[Many[Document]]
        val variableNodesByBpmnNameAndName: Map[(String, String), DynDoc] = variableNodes.
          map(variableNode => ((variableNode.bpmn_name[String], variableNode.name[String]), variableNode)).toMap
        traverseBpmn(1, bpmnName, "", activitiesByBpmnNameAndId, timersByBpmnNameAndId, endNodesByBpmnNameAndId,
          variableNodesByBpmnNameAndName, responseWriter, go)
      }
    }
    responseWriter.println(s"EXIT ${getClass.getName}:main()<br/>")
    responseWriter.println("</tt></html><br/>")
    responseWriter.flush()
  }

}
