package com.buildwhiz.baf3

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import org.bson.types.ObjectId
import com.buildwhiz.baf2.{PersonApi, ProjectApi}
import com.buildwhiz.slack.SlackApi

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class ProjectInfoSetImage extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      if (!parameters.contains("project_id"))
        throw new IllegalArgumentException("project_id not provided")
      val projectOid = new ObjectId(parameters("project_id"))
      val theProject = ProjectApi.projectById(projectOid)
      val user: DynDoc = getUser(request)
      if (!PersonApi.isBuildWhizAdmin(Right(user)) && !ProjectApi.canManage(user._id[ObjectId], theProject))
        throw new IllegalArgumentException("Not permitted")
      if (request.getParts.size == 1) {
        val part = request.getParts.iterator.next()
        if (part.getSize > 512000)
          throw new IllegalArgumentException("Image must be < 500 Kb")
        val imageFileName = part.getSubmittedFileName
        if (!imageFileName.matches("(?i).+[.](gif|jpeg|jpg|png)"))
          throw new IllegalArgumentException("File type must be gif/jpeg/jpg/png")
        val imageType = imageFileName.split("[.]").last
        val inputStream = part.getInputStream
        val imageBuffer = new Array[Byte](part.getSize.toInt)
        inputStream.read(imageBuffer)
        val updateResult = BWMongoDB3.images.updateOne(Map("project_id" -> projectOid),
          Map("$set" -> Map("project_image" -> imageBuffer, "image_type" -> imageType)))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      } else {
        throw new IllegalArgumentException(f"Has ${request.getParts.size} parts, expected 1")
      }
      val message = s"""Updated image of project '${theProject.name[String]}'"""
      val managers = ProjectApi.managers(Left(projectOid))
      for (manager <- managers) {
        SlackApi.sendNotification(message, Right(manager), Some(projectOid), Some(request))
      }
      response.getWriter.print(successJson())
      response.setContentType("application/json")
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
