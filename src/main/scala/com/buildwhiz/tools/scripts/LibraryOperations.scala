package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.baf3.{LibraryOperations => LibOps}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.utils.HttpUtils
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import scala.annotation.unused

object LibraryOperations extends HttpUtils {

  @unused
  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    response.setContentType("text/html")
    val writer = response.getWriter
    def output(s: String): Unit = writer.print(s)
    response.setContentType("text/html")
    output(s"<html><body>")
    output(s"<br/>${getClass.getName}:main() ENTRY<br/>")
    try {
      val user: DynDoc = getUser(request)
      if (!PersonApi.isBuildWhizAdmin(Right(user)) || !user.first_name[String].matches("Prabhas|Sanjay")) {
        throw new IllegalArgumentException("Not permitted")
      }
      LibOps.registerUser(user)
      if (args.length >= 2) {
        if (args(0).matches("(?i)EXPORT")) {
          val phaseSourceOid = new ObjectId(args(1))
          LibOps.exportPhase(phaseSourceOid, output, request)
        } else if (args.length == 3 && args(0).matches("(?i)IMPORT")) {
          val phaseSourceOid = new ObjectId(args(1))
          LibOps.importPhase(phaseSourceOid, new ObjectId(args(2)), output, request)
        } else if (args(0).matches("(?i)CLEAN")) {
          LibOps.cleanLibrary(args(1), output)
        } else {
          LibOps.listLibrary(output)
        }
      } else {
        LibOps.listLibrary(output)
        output(s"""<font color="blue">${getClass.getName}:main() Usage: ${getClass.getName} op-name src-phase-id [dest-proj-id]</font><br/>""")
      }
      output(s"<br/>${getClass.getName}:main() EXIT-OK<br/>")
    } catch {
      case t: Throwable =>
        output("%s(%s)<br/>".format(t.getClass.getName, t.getMessage))
        output(t.getStackTrace.map(_.toString).mkString("<br/>"))
    } finally {
      output("</body></html>")
    }
  }

}
