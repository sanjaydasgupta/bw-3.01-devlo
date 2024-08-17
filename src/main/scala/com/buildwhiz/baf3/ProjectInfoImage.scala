package com.buildwhiz.baf3

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils, ImageUtils}
import org.bson.types.{Binary, ObjectId}

import java.io.ByteArrayInputStream
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class ProjectInfoImage extends HttpServlet with HttpUtils with ImageUtils {

  private val contentTypes = Map(
    "gif" -> "image/gif", "jpg" -> "image/jpeg", "jpeg" -> "image/jpeg", "png" -> "image/png"
  )

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val (imageType, imageLength) = BWMongoDB3.images.find(Map("project_id" -> projectOid)).headOption match {
        case Some(imageRecord) =>
          val projectImage = imageRecord.project_image[Binary].getData
          val inputStream = new ByteArrayInputStream(projectImage)
          val outputStream = response.getOutputStream
          val buffer = new Array[Byte](4096)
          var len = inputStream.read(buffer)
          while (len > 0) {
            outputStream.write(buffer, 0, len)
            len = inputStream.read(buffer)
          }
          (imageRecord.image_type[String], projectImage.length)
        case None =>
          val imageFormat = "png"
          val imageArray = blankImage(100, 150, imageFormat)
          val outputStream = response.getOutputStream
          outputStream.write(imageArray)
          (imageFormat, imageArray.length)
      }
      response.setContentType(contentTypes(imageType))
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (length: $imageLength)", request)
    } catch {
      case t: Throwable =>
        reportFatalException(t, getClass.getName, request, response)
    }
  }
}
