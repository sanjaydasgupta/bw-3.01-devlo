package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines

class VariableValueSet extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val processOid = new ObjectId(parameters("process_id"))
      val (label, bpmnName, value) = (parameters("label"), parameters("bpmn_name"), parameters("value"))
      VariableValueSet.set(request, response, processOid, label, bpmnName, value)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object VariableValueSet extends HttpUtils {

  def set(request: HttpServletRequest, response: HttpServletResponse, processOid: ObjectId, label: String,
          bpmnName: String, value: String): Unit = {

    val theProcess = ProcessApi.processById(processOid)
    val user: DynDoc = getUser(request)
    if (!ProcessApi.canManage(user._id[ObjectId], theProcess))
      throw new IllegalArgumentException("Not permitted")

    val variables: Seq[DynDoc] = theProcess.variables[Many[Document]]
    val labelsAndBpmnNames: Seq[(String, String)] = variables.map(v => (v.label[String], v.bpmn_name[String]))
    val variableIdx = labelsAndBpmnNames.indexOf((label, bpmnName))

    if (variableIdx == -1)
      throw new IllegalArgumentException(s"no such label: '$label'")

    val typedValue = variables(variableIdx).`type`[String] match {
      case "B" => value.toBoolean
      case "L" => value.toLong
      case "D" => value.toDouble
      case "S" => value
    }

    if (theProcess.status[String] != "ended") {
      if (ProcessApi.isActive(theProcess)) {
        val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
        val processInstanceId = theProcess.process_instance_id[String]
        rts.setVariable(processInstanceId, variables(variableIdx).name[String], typedValue)
      }
      val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> processOid),
        Map("$set" -> Map(s"variables.$variableIdx.value" -> typedValue)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
//      response.getWriter.print(value)
//      response.setContentType("text/plain")
      response.setStatus(HttpServletResponse.SC_OK)
      val message = s"Set value of variable '${variables(variableIdx).label[String]}' to '$value'"
      BWLogger.audit(getClass.getName, "doPost", message, request)
    }
  }
}
