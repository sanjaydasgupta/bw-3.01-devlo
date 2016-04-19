package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.Utils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.types.ObjectId

import scala.collection.JavaConversions._

class TimerDurationSet extends HttpServlet with Utils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    def formatDuration(d: String): String = {
      val parts = d.split(":").map(_.toInt) match {
        case arr3 @ Array(a, b, c) => arr3
        case arr2 @ Array(b, c) => 0 +: arr2
        case arr1 @ Array(c) => 0 +: 0 +: arr1
      }
      parts.map(p => f"$p%02d").mkString(":")
    }
    try {
      val duration = parameters("duration")
      if (!duration.matches("(?:(?:\\d{1,2}\\:)?\\d{1,2}\\:)?\\d{1,2}"))
        throw new IllegalArgumentException("Bad duration format")
      val phaseOid = new ObjectId(parameters("phase_id"))
      val thePhase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val timerNamesAndBpmnNames: Seq[(String, String)] = thePhase.timers[DocumentList].
        map(t => (t.name[String], t.bpmn_name[String]))
      val timerName = parameters("timer_name")
      val bpmnName = parameters("bpmn_name")
      val timerIdx = timerNamesAndBpmnNames.indexOf((timerName, bpmnName))
      val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
        Map("$set" -> Map(s"timers.$timerIdx.duration" -> formatDuration(duration))))
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
