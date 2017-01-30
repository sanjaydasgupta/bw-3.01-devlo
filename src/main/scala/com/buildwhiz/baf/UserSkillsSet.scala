package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.utils.{CryptoUtils, HttpUtils}
import org.bson.types.ObjectId

class UserSkillsSet extends HttpServlet with HttpUtils with CryptoUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val setAdmin = parameters("BW-Admin").toBoolean match {
        case false => "$pull" -> Map("roles" -> "BW-Admin")
        case true => "$addToSet" -> Map("roles" -> "BW-Admin")
      }
      val setDemo = parameters("BW-Demo").toBoolean match {
        case false => "$pull" -> Map("roles" -> "BW-Demo")
        case true => "$addToSet" -> Map("roles" -> "BW-Demo")
      }
      val updateResult1 = BWMongoDB3.persons.updateOne(Map("_id" -> personOid), Map(setDemo))
      if (updateResult1.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult1")
      val updateResult2 = BWMongoDB3.persons.updateOne(Map("_id" -> personOid), Map(setAdmin))
      if (updateResult2.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult2")
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
    BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
  }
}
