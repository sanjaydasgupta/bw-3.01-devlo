package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class VariableValueSet extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val thePhase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).asScala.head
      val variables: Seq[DynDoc] = thePhase.variables[Many[Document]]
      val label = parameters("label")
      val bpmnName = parameters("bpmn_name")
      val labelsAndBpmnNames: Seq[(String, String)] = variables.map(v => (v.label[String], v.bpmn_name[String]))
      val variableIdx = labelsAndBpmnNames.indexOf((label, bpmnName))
      val stringValue = parameters("value")
      val newValue = variables(variableIdx).`type`[String] match {
        case "B" => stringValue.toBoolean
        case "L" => stringValue.toLong
        case "D" => stringValue.toDouble
      }
      val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
        Map("$set" -> Map(s"variables.$variableIdx.value" -> newValue)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
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
