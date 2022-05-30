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

  private def validateEndNode(margin: String, bpmnName: String, idPath: String, endNode: DynDoc, process: DynDoc,
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
        if (go) {
          val updateResult = BWMongoDB3.processes.updateOne(
            Map("_id" -> process._id[ObjectId],
              "end_nodes" -> Map($elemMatch -> Map("bpmn_name" -> bpmnName, "bpmn_id" -> bpmnId))),
            Map($set -> Map("end_nodes.$.bpmn_name_full" -> expectedBpmnNameFull)))
          if (updateResult.getModifiedCount == 1) {
            responseWriter.println(s"""$margin<font color="green"><b>UPDATE-OK-EndNode:$name($bpmnId) >> """ +
              s"""$expectedBpmnNameFull</b></font><br/>""")
          } else {
            responseWriter.println(s"""$margin<font color="red"><b>UPDATE-FAIL-EndNode:$name($bpmnId) >> """ +
              s"""$updateResult</b></font><br/>""")
          }
        }
    }
  }

  private def validateStartNode(margin: String, bpmnName: String, idPath: String, startNode: DynDoc, process: DynDoc,
      responseWriter: PrintWriter, go: Boolean): Unit = {
    val name = startNode.name[String]
    val bpmnId = startNode.bpmn_id[String]
    val expectedBpmnNameFull = if (idPath.isEmpty) {
      bpmnName
    } else {
      s"$idPath/$bpmnName"
    }
    startNode.get[String]("bpmn_name_full") match {
      case Some(bpmnNameFull) =>
        if (bpmnNameFull == expectedBpmnNameFull) {
          responseWriter.println(s"""$margin<font color="green">OK-StartNode:$name($bpmnId) >> """ +
            s"""$bpmnNameFull</font><br/>""")
        } else {
          responseWriter.println(s"""$margin<font color="red">Bad-value-StartNode:$name($bpmnId) >> """ +
            s"""$expectedBpmnNameFull!=$bpmnNameFull</font><br/>""".stripMargin)
        }
      case None =>
        responseWriter.println(s"""$margin<font color="red">No-Value-StartNode:$name($bpmnId) >> """ +
          s"""$expectedBpmnNameFull</font><br/>""")
        if (go) {
          val updateResult = BWMongoDB3.processes.updateOne(
            Map("_id" -> process._id[ObjectId],
              "start_nodes" -> Map($elemMatch -> Map("bpmn_name" -> bpmnName, "bpmn_id" -> bpmnId))),
            Map($set -> Map("start_nodes.$.bpmn_name_full" -> expectedBpmnNameFull)))
          if (updateResult.getModifiedCount == 1) {
            responseWriter.println(s"""$margin<font color="green"><b>UPDATE-OK-StartNode:$name($bpmnId) >> """ +
              s"""$expectedBpmnNameFull</b></font><br/>""")
          } else {
            responseWriter.println(s"""$margin<font color="red"><b>UPDATE-FAIL-StartNode:$name($bpmnId) >> """ +
              s"""$updateResult</b></font><br/>""")
          }
        }
    }
  }

  private def validateTimer(margin: String, bpmnName: String, idPath: String, timer: DynDoc, process: DynDoc,
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
        if (go) {
          val updateResult = BWMongoDB3.processes.updateOne(
            Map("_id" -> process._id[ObjectId],
                "timers" -> Map($elemMatch -> Map("bpmn_name" -> bpmnName, "bpmn_id" -> bpmnId))),
            Map($set -> Map("timers.$.bpmn_name_full" -> expectedBpmnNameFull)))
          if (updateResult.getModifiedCount == 1) {
            responseWriter.println(s"""$margin<font color="green"><b>UPDATE-OK-Timer:$name($bpmnId) >> """ +
              s"""$expectedBpmnNameFull</b></font><br/>""")
          } else {
            responseWriter.println(s"""$margin<font color="red"><b>UPDATE-FAIL-Timer:$name($bpmnId) >> """ +
              s"""$updateResult</b></font><br/>""")
          }
        }
    }
  }

  private def validateVariable(margin: String, bpmnName: String, idPath: String, variable: DynDoc, process: DynDoc,
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
        if (go) {
          val updateResult = BWMongoDB3.processes.updateOne(
            Map("_id" -> process._id[ObjectId],
              "variables" -> Map($elemMatch -> Map("bpmn_name" -> bpmnName, "name" -> name))),
            Map($set -> Map("variables.$.bpmn_name_full" -> expectedBpmnNameFull)))
          if (updateResult.getModifiedCount == 1) {
            responseWriter.println(s"""$margin<font color="green"><b>UPDATE-OK-Variable:$name >> """ +
              s"""$expectedBpmnNameFull</b></font><br/>""")
          } else {
            responseWriter.println(s"""$margin<font color="red"><b>UPDATE-FAIL-Variable:$name >> """ +
              s"""$updateResult</b></font><br/>""")
          }
        }
    }
  }

  private def validateCallBlock(margin: String, bpmnName: String, idPath: String, callBlockNode: DynDoc, process: DynDoc,
      responseWriter: PrintWriter, go: Boolean): Unit = {
    val name = callBlockNode.getOrElse[String]("parent_activity_name", "???")
    val bpmnId = callBlockNode.parent_activity_id[String]
    val expectedBpmnNameFull2 = if (idPath.isEmpty) {
      bpmnName
    } else {
      s"$idPath/$bpmnName"
    }
    callBlockNode.get[String]("bpmn_name_full2") match {
      case Some(bpmnNameFull) =>
        if (bpmnNameFull == expectedBpmnNameFull2) {
          responseWriter.println(s"""$margin<font color="green">OK-CallBlock:$name($bpmnId) >> """ +
            s"""$bpmnNameFull</font><br/>""")
        } else {
          responseWriter.println(s"""$margin<font color="red">Bad-value-CallBlock:$name($bpmnId) >> """ +
            s"""$expectedBpmnNameFull2!=$bpmnNameFull</font><br/>""".stripMargin)
        }
      case None =>
        responseWriter.println(s"""$margin<font color="red">No-Value-CallBlock:$name($bpmnId) >> """ +
          s"""$expectedBpmnNameFull2</font><br/>""")
        if (go) {
          val updateResult = BWMongoDB3.processes.updateOne(
            Map("_id" -> process._id[ObjectId],
              "bpmn_timestamps" -> Map($elemMatch -> Map("parent_name" -> bpmnName, "parent_activity_id" -> bpmnId))),
            Map($set -> Map("bpmn_timestamps.$.bpmn_name_full2" -> expectedBpmnNameFull2)))
          if (updateResult.getModifiedCount == 1) {
            responseWriter.println(s"""$margin<font color="green"><b>UPDATE-OK-CallBlock:$name($bpmnId) >> """ +
              s"""$expectedBpmnNameFull2</b></font><br/>""")
          } else {
            responseWriter.println(s"""$margin<font color="red"><b>UPDATE-FAIL-CallBlock:$name($bpmnId) >> """ +
              s"""$updateResult</b></font><br/>""")
          }
        }
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
    val activityOid = activity._id[ObjectId]
    activity.get[String]("bpmn_name_full") match {
      case Some(bpmnNameFull) =>
        if (bpmnNameFull == expectedBpmnNameFull) {
          responseWriter.println(s"""$margin<font color="green">OK-Activity:$name($bpmnId) >> """ +
            s"""$bpmnNameFull ($activityOid)</font><br/>""")
        } else {
          responseWriter.println(s"""$margin<font color="red">Bad-value-Activity:$name($bpmnId) >> """ +
               s"""$expectedBpmnNameFull!=$bpmnNameFull ($activityOid)</font><br/>""".stripMargin)
        }
      case None =>
        responseWriter.println(s"""$margin<font color="red">No-Value-Activity:$name($bpmnId) >> """ +
          s"""$expectedBpmnNameFull ($activityOid)</font><br/>""")
        if (go) {
          val updateResult = BWMongoDB3.tasks.updateOne(Map("_id" -> activityOid),
            Map($set -> Map("bpmn_name_full" -> expectedBpmnNameFull)))
          if (updateResult.getModifiedCount == 1) {
            responseWriter.println(s"""$margin<font color="green"><b>UPDATE-OK-Activity:$name($bpmnId) >> """ +
              s"""$expectedBpmnNameFull ($activityOid)</b></font><br/>""")
          } else {
            responseWriter.println(s"""$margin<font color="red"><b>UPDATE-FAIL-Activity:$name($bpmnId) >> """ +
              s"""$updateResult ($activityOid)</b></font><br/>""")
          }
        }
    }
  }

  private def traverseBpmn(level: Int, bpmnName: String, idPath: String, process: DynDoc,
      activitiesByBpmnNameAndId: Map[(String, String), DynDoc], responseWriter: PrintWriter, go: Boolean): Unit = {
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
            responseWriter.println(s"""$margin<font color="red">MISSING-Activity:$name[$bpmnId]</font><br/>""")
        }
      }
    } else {
      responseWriter.println(s"""$margin<font color="red">NO-Activities in $fullBpmnName</font><br/>""")
    }

    val timerNodes: Seq[Element] = theDom.getElementsByTagName(s"$prefix:intermediateCatchEvent").
      filter(_.getChildNodes.exists(_.getLocalName == "timerEventDefinition")).map(_.asInstanceOf[Element])
    if (timerNodes.nonEmpty) {
      val timers: Seq[DynDoc] = process.timers[Many[Document]]
      val timersByBpmnNameAndId: Map[(String, String), DynDoc] =
        timers.map(timer => ((timer.bpmn_name[String], timer.bpmn_id[String]), timer)).toMap
      for (timerNode <- timerNodes) {
        val name = cleanText(nameAttribute(timerNode))
        val bpmnId = timerNode.getAttributes.getNamedItem("id").getTextContent
        timersByBpmnNameAndId.get((bpmnName, bpmnId)) match {
          case Some(timer: DynDoc) =>
            validateTimer(margin, bpmnName, idPath, timer, process, responseWriter, go)
          case None =>
            responseWriter.println(s"""$margin<font color="red">MISSING-Timer:$name[$bpmnId]</font><br/>""")
        }
      }
    } else {
      responseWriter.println(s"""$margin<font color="brown">NO-Timers in $fullBpmnName</font><br/>""")
    }

    val endNodes: Seq[Element] = theDom.getElementsByTagName(s"$prefix:endEvent").map(_.asInstanceOf[Element])
    if (endNodes.nonEmpty) {
      val procEndNodes: Seq[DynDoc] = process.end_nodes[Many[Document]]
      val endNodesByBpmnNameAndId: Map[(String, String), DynDoc] =
        procEndNodes.map(pEndNode => ((pEndNode.bpmn_name[String], pEndNode.bpmn_id[String]), pEndNode)).toMap
      for (endNode <- endNodes) {
        val name = cleanText(nameAttribute(endNode))
        val bpmnId = endNode.getAttributes.getNamedItem("id").getTextContent
        endNodesByBpmnNameAndId.get((bpmnName, bpmnId)) match {
          case Some(endNode: DynDoc) =>
            validateEndNode(margin, bpmnName, idPath, endNode, process, responseWriter, go)
          case None =>
            responseWriter.println(s"""$margin<font color="red">MISSING-EndNode:$name[$bpmnId]</font><br/>""")
            if (go) {
              val end: Document = Map("bpmn_name_full" -> fullBpmnName, "bpmn_name" -> bpmnName, "name" -> name,
                "bpmn_id" -> bpmnId, "start" -> "00:00:00", "end" -> "00:00:00", "status" -> "defined",
                "full_path_id" -> s"$idPath/$bpmnId")
              val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> process._id[ObjectId]),
                Map($push -> Map("end_nodes" -> end)))
              if (updateResult.getModifiedCount == 1) {
                responseWriter.println(
                  s"""$margin<font color="green"><b>UPDATE-OK-EndNode:$name($bpmnId) >> """ +
                    s"""$fullBpmnName</b></font><br/>""")
              }
            }
        }
      }
    } else {
      responseWriter.println(s"""$margin<font color="brown">NO-EndNodes in $fullBpmnName</font><br/>""")
    }

    val startNodes: Seq[Element] = theDom.getElementsByTagName(s"$prefix:startEvent").map(_.asInstanceOf[Element])
    if (startNodes.nonEmpty) {
      val procStartNodes: Seq[DynDoc] = process.getOrElse[Many[Document]]("start_nodes", Seq.empty)
      val startNodesByBpmnNameAndId: Map[(String, String), DynDoc] =
        procStartNodes.map(pEndNode => ((pEndNode.bpmn_name[String], pEndNode.bpmn_id[String]), pEndNode)).toMap
      for (startNode <- startNodes) {
        val name = cleanText(nameAttribute(startNode))
        val bpmnId = startNode.getAttributes.getNamedItem("id").getTextContent
        startNodesByBpmnNameAndId.get((bpmnName, bpmnId)) match {
          case Some(startNode: DynDoc) =>
            validateStartNode(margin, bpmnName, idPath, startNode, process, responseWriter, go)
          case None =>
            responseWriter.println(s"""$margin<font color="red">MISSING-StartNode:$name[$bpmnId]</font><br/>""")
            if (go) {
              val start: Document = Map("bpmn_name_full" -> fullBpmnName, "bpmn_name" -> bpmnName, "name" -> name,
                "bpmn_id" -> bpmnId, "start" -> "00:00:00", "end" -> "00:00:00", "status" -> "defined",
                "full_path_id" -> s"$idPath/$bpmnId")
              val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> process._id[ObjectId]),
                Map($push -> Map("start_nodes" -> start)))
              if (updateResult.getModifiedCount == 1) {
                responseWriter.println(
                  s"""$margin<font color="green"><b>UPDATE-OK-StartNode:$name($bpmnId) >> """ +
                    s"""$fullBpmnName</b></font><br/>""")
              }
            }
        }
      }
    } else {
      responseWriter.println(s"""$margin<font color="brown">NO-StartNodes in $fullBpmnName</font><br/>""")
    }

    val variableNodes: Seq[Element] = theDom.getElementsByTagName("camunda:property").
        filter(nameAttribute(_) == "bw-variable").map(_.asInstanceOf[Element])
    if (variableNodes.nonEmpty) {
      val procVariableNodes: Seq[DynDoc] = process.variables[Many[Document]]
      val variableNodesByBpmnNameAndName: Map[(String, String), DynDoc] = procVariableNodes.
        map(procVarNode => ((procVarNode.bpmn_name[String], procVarNode.name[String]), procVarNode)).toMap
      for (variableNode <- variableNodes) {
        val name = cleanText(getAttribute(variableNode, "value").split(":").head)
        variableNodesByBpmnNameAndName.get((bpmnName, name)) match {
          case Some(varNode: DynDoc) =>
            validateVariable(margin, bpmnName, idPath, varNode, process, responseWriter, go)
          case None =>
            responseWriter.println(s"""$margin<font color="red">MISSING-Variable:$name</font><br/>""")
        }
      }
    } else {
      responseWriter.println(s"""$margin<font color="brown">NO-Variables in $fullBpmnName</font><br/>""")
    }

    val callActivityNodes: Seq[Element] = theDom.getElementsByTagName(s"$prefix:callActivity").
      filter(_.getAttributes.getNamedItem("calledElement").getTextContent != "Infra-Activity-Handler").
      map(_.asInstanceOf[Element])

    if (callActivityNodes.nonEmpty) {
      val callBlockNodes: Seq[DynDoc] = process.bpmn_timestamps[Many[Document]]
      val callNodesByBpmnNameAndId: Map[(String, String), DynDoc] = callBlockNodes.
        filter(cn => cn.has("parent_name") && cn.has("parent_activity_id")).
        map(callNode => ((callNode.parent_name[String], callNode.parent_activity_id[String]), callNode)).toMap
      if (callBlockNodes.length > callNodesByBpmnNameAndId.size) {
        responseWriter.println(s"""$margin<font color="red">MISSING-CallBlock:Some CallBlocks not processed</font><br/>""")
      }
      for (callElementNode <- callActivityNodes) {
        val name = cleanText(nameAttribute(callElementNode))
        val bpmnId = callElementNode.getAttributes.getNamedItem("id").getTextContent
        callNodesByBpmnNameAndId.get((bpmnName, bpmnId)) match {
          case Some(callBlockNode: DynDoc) =>
            validateCallBlock(margin, bpmnName, idPath, callBlockNode, process, responseWriter, go)
          case None =>
            responseWriter.println(s"""$margin<font color="red">MISSING-CallBlock:$name[$bpmnId]</font><br/>""")
        }
      }
    } else {
      responseWriter.println(s"""$margin<font color="brown">NO-CallBlocks in $fullBpmnName</font><br/>""")
    }

    for (call <- callActivityNodes) {
      val calledBpmnName = call.getAttributes.getNamedItem("calledElement").getTextContent
      val callerElementId = call.getAttributes.getNamedItem("id").getTextContent
      val newIdPath = if (idPath.isEmpty) {
        callerElementId
      } else {
        s"$idPath/$callerElementId"
      }
      traverseBpmn(level + 1, calledBpmnName, newIdPath, process, activitiesByBpmnNameAndId, responseWriter, go)
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
        val activities: Seq[DynDoc] = BWMongoDB3.tasks.find(Map("_id" -> Map($in -> activityOids)))
        val activitiesByBpmnNameAndId: Map[(String, String), DynDoc] =
          activities.map(activity => ((activity.bpmn_name[String], activity.bpmn_id[String]), activity)).toMap
        traverseBpmn(1, bpmnName, "", process, activitiesByBpmnNameAndId, responseWriter, go)
      }
    }
    responseWriter.println(s"EXIT ${getClass.getName}:main()<br/>")
    responseWriter.println("</tt></html><br/>")
    responseWriter.flush()
  }

}
