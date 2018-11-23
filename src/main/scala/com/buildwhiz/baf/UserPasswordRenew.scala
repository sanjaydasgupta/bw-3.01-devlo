package com.buildwhiz.baf

import com.buildwhiz.infra.DynDoc.Many
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._

import scala.math.random
import com.buildwhiz.utils.{BWLogger, CryptoUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class UserPasswordRenew extends HttpServlet with HttpUtils with CryptoUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val isAdmin = freshUserRecord.roles[Many[String]].contains("BW-Admin")
      if (!isAdmin)
        throw new IllegalArgumentException("not permitted")
      val targetUserOid = new ObjectId(parameters("user_id"))
      val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
      val password = (0 to 10).map(_ => {
        val randomNumber = (random() * characters.length).toInt
        characters(randomNumber)
      }).mkString
      val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> targetUserOid),
          Map("$set" -> Map("password" -> md5(password))))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.getWriter.println(s"""{"password": "$password"}""")
      response.setContentType("application/json")
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
