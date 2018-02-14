package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines

class VariableValueSet extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val (label, bpmnName, value) = (parameters("label"), parameters("bpmn_name"), parameters("value"))
      VariableValueSet.set(request, response, phaseOid, label, bpmnName, value)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object VariableValueSet {

  def set(request: HttpServletRequest, response: HttpServletResponse, phaseOid: ObjectId, label: String,
        bpmnName: String, value: String): Unit = {

    val thePhase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
    val variables: Seq[DynDoc] = thePhase.variables[Many[Document]]
    val labelsAndBpmnNames: Seq[(String, String)] = variables.map(v => (v.label[String], v.bpmn_name[String]))
    val variableIdx = labelsAndBpmnNames.indexOf((label, bpmnName))

    val typedValue = variables(variableIdx).`type`[String] match {
      case "B" => value.toBoolean
      case "L" => value.toLong
      case "D" => value.toDouble
      case "S" => value
    }

    if (thePhase.status[String] != "ended") {
      if (thePhase.has("process_instance_id")) {
        val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
        val processInstanceId = thePhase.process_instance_id[String]
        rts.setVariable(processInstanceId, variables(variableIdx).name[String], typedValue)
      }
      val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
        Map("$set" -> Map(s"variables.$variableIdx.value" -> typedValue)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.setStatus(HttpServletResponse.SC_OK)
      val variableLog = s"'${variables(variableIdx).label[String]}'"
      BWLogger.audit(getClass.getName, "doPost", s"""Set value of variable '$variableLog' to '$value'""", request)
    }
  }
}
