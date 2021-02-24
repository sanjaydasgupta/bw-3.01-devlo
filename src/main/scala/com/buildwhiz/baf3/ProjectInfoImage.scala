package com.buildwhiz.baf3

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.{Binary, ObjectId}

import java.io.ByteArrayInputStream
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class ProjectInfoImage extends HttpServlet with HttpUtils {

  private val contentTypes = Map(
    "gif" -> "image/gif", "jpg" -> "image/jpeg", "jpeg" -> "image/jpeg", "png" -> "image/png"
  )

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val imageRecord: DynDoc = BWMongoDB3.images.find(Map("project_id" -> projectOid)).head
      val projectImage = imageRecord.project_image[Binary].getData
      val imageType = imageRecord.image_type[String]
      val inputStream = new ByteArrayInputStream(projectImage)
      val outputStream = response.getOutputStream
      val buffer = new Array[Byte](4096)
      var len = inputStream.read(buffer)
      while (len > 0) {
        outputStream.write(buffer, 0, len)
        len = inputStream.read(buffer)
      }
      response.setStatus(HttpServletResponse.SC_OK)
      response.setContentType(contentTypes(imageType))
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (length: ${projectImage.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
