package com.buildwhiz.tools

import java.io.{File, FileOutputStream, InputStream}
import javax.servlet.annotation.MultipartConfig
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.HttpUtils
import com.buildwhiz.infra.BWLogger

import scala.collection.JavaConverters._
import scala.sys.process._

@MultipartConfig()
class UploadFile extends HttpServlet with HttpUtils {

  private def copyStream(in: InputStream, out: FileOutputStream, length: Int = 0,
        buffer: Array[Byte] = new Array[Byte](1024)): Int = {
    in.read(buffer) match {
      case 0 | -1 => length
      case 1024 =>
        out.write(buffer)
        copyStream(in, out, length + buffer.length)
      case len =>
        out.write(buffer, 0, len)
        length + len
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val uploadsDirectory = new File("uploads")
      if (parameters.contains("file_location")) {
        val fileLocation = parameters("file_location")
        val locationParts = fileLocation.split("/")
        val theFile = locationParts.foldLeft(uploadsDirectory)((dir, name) => new File(dir, name))
        val fileOutputStream = new FileOutputStream(theFile)
        val parts = request.getParts
        if (parts.isEmpty)
          throw new IllegalArgumentException("No file uploaded")
        val inputStream = request.getParts.asScala.head.getInputStream
        val length = copyStream(inputStream, fileOutputStream)
        fileOutputStream.flush()
        fileOutputStream.close()
        writer.println(s"Received $length bytes for '$fileLocation' from ${request.getRemoteAddr}")
        response.setContentType("text/html")
        BWLogger.log(getClass.getName, "doPost()", s"EXIT-OK Upload-length: $length", request)
      } else {
        val files = uploadsDirectory.listFiles.map(_.getPath)
        val status = s"""cp -fr ${files.mkString(" ")} server/apache-tomcat-8.0.24/webapps/buildwhiz-1.01""".!
        val statusMsg = Seq("OK", "ERROR")(status)
        //writer.println(s"""cp -frl ${files.mkString(" ")} server/apache-tomcat-8.0.24/webapps/buildwhiz-1.01<br/>""")
        writer.println(s"""Update status: $statusMsg, files: ${files.mkString(", ")}""")
        BWLogger.log(getClass.getName, "doPost()", s"""EXIT-OK files: ${files.mkString(", ")}, status: $statusMsg""", request)
      }
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

}