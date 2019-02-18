package com.buildwhiz.baf2

import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document

class TaskStatusInfo extends HttpServlet with HttpUtils with DateTimeUtils {

  private def changeLogItems(request: HttpServletRequest): Seq[Document] = {
    (1 to 10).map(n => {
      val dateTime = s"2019-01-2$n 00:00"
      val updatedBy = "FirstName LastName"
      val (percentComplete, description) = if (n % 3 == 0)
        ("-", "one two three four five six seven eight nine ten")
      else
        (s"${(n - 1) * 10}", s"The update for $n on the date of ????")
      new Document("date_time", dateTime).append("updated_by", updatedBy).append("pct_complete", percentComplete).
        append("description", description)
    })
  }

  private def taskStatusRecord(request: HttpServletRequest): String = {
    val changeLog = changeLogItems(request: HttpServletRequest).map(bson2json).mkString("[", ", ", "]")
    val record = new Document("status", "running").append("on_critical_path", true).
        append("estimated_duration", 33).append("actual_duration", 35).
        append("estimated_start_date", "2018-MM-DD").append("actual_start_date", "2018-MM-DD").
        append("estimated_end_date", "2019-MM-DD").append("actual_end_date", "2019-MM-DD").
        append("reporting_interval", "weekly").
        append("change_log", changeLog)
    bson2json(record)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      //val activityOid = new ObjectId(parameters("activity_id"))
      //val user: DynDoc = getUser(request)
      //val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      response.getWriter.print(taskStatusRecord(request))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}