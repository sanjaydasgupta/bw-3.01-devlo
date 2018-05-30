package com.buildwhiz.tools

import java.io._

import javax.servlet.annotation.MultipartConfig
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}

import scala.collection.JavaConverters._
import scala.sys.process._

@MultipartConfig()
class UploadFile extends HttpServlet with HttpUtils with DateTimeUtils {

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

  private def purgeOldFiles(directory: File, fileName: String): Unit = {
    val versionPattern = "[0-9a-z]{20}"
    val fileNamePattern = s"(.+)($versionPattern)(.+)".r
    val fileNameRegex = fileName match {
      case fileNamePattern(prefix, _, suffix) => s"$prefix$versionPattern$suffix"
      case _ => fileName
    }
    val files = directory.listFiles().filter(_.getName.matches(fileNameRegex))
    if (files.length > 1) {
      val latest = files.last.getCanonicalPath
      val previous = files.init.last.getCanonicalPath
      val diffMsg = s"""diff $latest $previous""".!!
      val pw = new FileWriter("diff.txt", true)
      val time = dateTimeString(System.currentTimeMillis)
      pw.write(s"$time - $fileName\n")
      pw.write(s"$diffMsg\n")
      pw.flush()
      pw.close()
      files.init.foreach(_.delete())
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val user: DynDoc = getUser(request)
      val rawRoles: Seq[String] = user.roles[Many[String]]
      if (!rawRoles.contains("BW-File-Uploads")) {
        throw new IllegalArgumentException("Not permitted")
      }
      val uploadsDirectory = new File("uploads")
        if (!uploadsDirectory.exists)
          throw new IllegalArgumentException("No 'uploads' directory")
      if (parameters.contains("file_location")) {
        val fileLocation = parameters("file_location")
        //val locationParts = fileLocation.split("/")
        //val theFile = locationParts.foldLeft(uploadsDirectory)((dir, name) => new File(dir, name))
        //val fileOutputStream = new FileOutputStream(theFile)
        val directory = new File(uploadsDirectory, fileLocation)
        if (!directory.exists()) {
          val cwd = uploadsDirectory.getCanonicalPath
          throw new IllegalArgumentException(s"No directory '$fileLocation' in '$cwd'")
        }
        val parts = request.getParts.asScala
        if (parts.isEmpty)
          throw new IllegalArgumentException("No file uploaded")
        val fileName = parts.head.getSubmittedFileName
        val inputStream = parts.head.getInputStream
        val relativeFileName = fileLocation + "/" + fileName
        val fileOutputStream = new FileOutputStream(new File(uploadsDirectory, relativeFileName))
        val length = copyStream(inputStream, fileOutputStream)
        fileOutputStream.flush()
        fileOutputStream.close()
        purgeOldFiles(directory, fileName)
        writer.println(s"Received $length bytes for '$relativeFileName' from ${request.getRemoteAddr}")
        response.setContentType("text/html")
        BWLogger.audit(getClass.getName, "doPost()", s"File-Loaded '$fileLocation/$fileName' ($length)", request)
      } else {
        val files = uploadsDirectory.listFiles.map(_.getPath)
        val status = s"""cp -fr ${files.mkString(" ")} server/apache-tomcat-8.0.47/webapps/bw-dot-1.01""".!
        val statusMsg = if (status == 0) {
          BWLogger.audit(getClass.getName, "doPost()", s"""File-Committed: ${files.mkString(", ")}""", request)
          "OK"
        } else {
          BWLogger.log(getClass.getName, "doPost()", s"""ERROR: Upload failure status: $status""", request)
          s"ERROR [$status]"
        }
        writer.println(s"""Update status: $statusMsg, files: ${files.mkString(", ")}""")
      }
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}