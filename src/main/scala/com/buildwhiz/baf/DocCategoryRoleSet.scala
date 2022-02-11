package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

class DocCategoryRoleSet extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val categoryOid = new ObjectId(parameters("category_id"))
      val roleOid = new ObjectId(parameters("role_id"))
      val permitted = parameters("permitted").toBoolean
      val query = Map("category_id" -> categoryOid, "role_id" -> roleOid)
      val updateResult = BWMongoDB3.role_category_mapping.updateOne(query, Map("$set" -> Map("permitted" -> permitted)))
      val matchCount = updateResult.getMatchedCount
      if (matchCount == 0) {
        BWMongoDB3.role_category_mapping.insertOne(query ++ Map("permitted" -> permitted))
      }
      val status = if (matchCount == 0) "inserted" else "updated"
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (record $status)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
