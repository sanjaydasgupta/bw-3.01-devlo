package com.buildwhiz.baf3

import com.buildwhiz.utils.{BWLogger, HttpUtils}

import java.io.{File, FileInputStream}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

object MediaServer extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s":ENTRY", request)
    try {
      val t0 = System.currentTimeMillis()
      response.setContentType("image/x-png")
      val fileName = request.getRequestURL.toString.split("/").last
      val mediaDirectory = new File("/home/ubuntu/media")
      if (mediaDirectory.listFiles.exists(_.getName == fileName)) {
        val fis = new FileInputStream(s"/home/ubuntu/media/$fileName")
        val fos = response.getOutputStream
        val buf = new Array[Byte](4096)
        def transfer(): Int = {
          val len = fis.read(buf)
          fos.write(buf, 0, len)
          if (fis.available() > 0) {
            len + transfer()
          } else {
            len
          }
        }
        val totLen = transfer()
        fos.flush()
        val delay = System.currentTimeMillis() - t0
        BWLogger.log(getClass.getName, request.getMethod, s":EXIT-OK (time: $delay ms, length=$totLen)", request)
      } else {
        BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR: '$fileName' not found", request)
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
