package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class TimerDurationSet extends HttpServlet with HttpUtils {

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
      val bpmnName = parameters("bpmn_name")
      val timersInBpmn: Seq[DynDoc] = thePhase.timers[Many[Document]].filter(t => t.bpmn_name[String] == bpmnName)
      val timersWithIndex: Seq[(DynDoc, Int)] = timersInBpmn.zipWithIndex
      val timerIdx: Int = (parameters.get("timer_id"), parameters.get("timer_name")) match {
        case (Some(timerId), _) => timersWithIndex.filter(_._1.bpmn_id[String] == timerId).head._2
        case (_, Some(timerName)) =>
          timersWithIndex.filter(_._1.name[String].replace("\\s+", "") == timerName.replace("\\s+", "")).head._2
        case _ => throw new IllegalArgumentException("Timer id or name not provided")
      }
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
