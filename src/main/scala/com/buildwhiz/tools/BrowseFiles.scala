package com.buildwhiz.tools

import java.io.{File, FileInputStream}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.Utils

class BrowseFiles extends HttpServlet with Utils {

  private def browseDirectory(directory: File, request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val writer = response.getWriter
    writer.println("<html><head><title>Browse Directory</title></head><body>")
    writer.println(s"""<h1 align="center">${directory.getCanonicalPath}</h1>""")
    writer.println("<table align=\"center\" border=\"1\">")
    writer.println(
      s"""<tr><td align="center">Name</td><td align="center">Directory</td>
         |<td align="center">Size</td></tr>""".stripMargin)
    val canonDir = directory.getCanonicalFile
    val allFiles = (new File(canonDir, "..") +: canonDir.listFiles).
      sortWith((a, b) => a.getName.toLowerCase < b.getName.toLowerCase)
    val url = request.getRequestURL.toString
    for (file <- allFiles) {
      val linkedName = file.getCanonicalPath
      val hyperlink = if (file.isDirectory)
        s"""<a href="$url?location=$linkedName">${file.getName}</a>"""
      else
        s"""<a href="$url/${file.getName}?location=$linkedName">${file.getName}</a>"""
      val dirColor = if (file.isDirectory) "bgcolor=\"yellow\"" else ""
      val dirFlag = if (file.isDirectory) "Y" else ""
      val length = if (file.isDirectory) "-" else file.length.toString
      writer.println(
        s"""<tr $dirColor><td align="center">$hyperlink</td><td align="center">$dirFlag</td>
           |<td align="center">$length</td></tr>""".stripMargin)
    }
    writer.println(s"</table>")
    val zipName = canonDir.getName + ".zip"
    val zipLink = s"""<a href="$url/$zipName?location=${canonDir.getCanonicalPath}/$zipName">Download ZIP</a>"""
    writer.println(s"""<h3 align="center">$zipLink</h3>""")
    writer.println(s"</body></html>")
    response.setContentType("text/html")
  }

  private def downloadFile(file: File, response: HttpServletResponse): Unit = {
    val fis = new FileInputStream(file)
    val buffer = new Array[Byte](4096)
    def copy(): Unit = {
      val length = fis.read(buffer)
      if (length == buffer.length) {
        response.getOutputStream.write(buffer)
        copy()
      } else if (length > 0) {
        response.getOutputStream.write(buffer, 0, length)
      }
    }
    copy()
    response.setContentType("application/octet-stream")
  }

  private def downloadZIP(file: File, response: HttpServletResponse): Unit = {
    if (file.getName.endsWith(".zip")) {
      val directory = file.getParentFile
      if (directory.exists && directory.getName + ".zip" == file.getName) {
        import scala.sys.process.Process
        val cmd = Seq("zip", "-r", file.getName, ".")
        val status = Process(cmd, file.getParentFile).!
        if (status == 0) {
          downloadFile(file, response)
          file.delete()
        } else {
          val writer = response.getWriter
          writer.println("<html><head><title>Browse ERROR</title></head><body>")
          writer.println(s"""<h3 align="center">ZIP failed, status = $status, cmd = '${cmd.mkString(" ")}'</h3>""")
          writer.println(s"</body></html>")
        }
      } else {
        val writer = response.getWriter
        writer.println("<html><head><title>Browse ERROR</title></head><body>")
        writer.println(s"""<h3 align="center">Can't find '${file.getAbsolutePath}'</h3>""")
        writer.println(s"</body></html>")
      }
    } else {
      val writer = response.getWriter
      writer.println("<html><head><title>Browse ERROR</title></head><body>")
      writer.println(s"""<h3 align="center">Can't find '${file.getAbsolutePath}'</h3>""")
      writer.println(s"</body></html>")
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    //BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    try {
      val location = parameters.get("location")
      location match {
        case None => browseDirectory(new File("."), request, response)
        case Some(fn) => val file = new File(fn).getCanonicalFile
          if (file.isDirectory) {
            browseDirectory(file, request, response)
          } else if (file.exists()) {
            downloadFile(file, response)
          } else {
            downloadZIP(file, response)
          }
      }
      response.setStatus(HttpServletResponse.SC_OK)
      //BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        //BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

}