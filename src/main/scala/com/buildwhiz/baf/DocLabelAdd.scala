package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

class DocLabelAdd extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val newLabelName: String = parameters("label_name")
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val labels: Seq[String] = if (freshUserRecord.has("labels"))
        freshUserRecord.labels[Many[String]] else Seq.empty[String]
      if (labels.contains(newLabelName))
        throw new IllegalArgumentException(s"label '$newLabelName' already exists")
      val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> user._id[ObjectId]),
        Map("$addToSet" -> Map("labels" -> newLabelName)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.audit(getClass.getName, "doPost", s"Added document-label $newLabelName", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}
