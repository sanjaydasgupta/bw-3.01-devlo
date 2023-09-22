package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.{PersonApi, ProjectApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.HttpUtils
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object ProjectDelete extends HttpUtils {

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    val writer = response.getWriter
    def output(s: String): Unit = writer.print(s)
    try {
      response.setContentType("text/html")
      output(s"<html><body>")
      output(s"${getClass.getName}:main() ENTRY<br/>")
      val user: DynDoc = getUser(request)
      if (!PersonApi.isBuildWhizAdmin(Right(user)) || user.first_name[String] != "Sanjay") {
        throw new IllegalArgumentException("Not permitted")
      }
      if (args.length >= 1) {
        val projectOid = new ObjectId(args(0))
        BWMongoDB3.projects.find(Map("_id" -> projectOid)).headOption match {
          case None =>
            output(s"No such project ID: '${args(0)}'<br/>")
          case Some(theProject) =>
            output(s"Found project: '${theProject.asDoc.toJson}'<br/>")
            val go: Boolean = args.length == 2 && args(1) == "GO"
            if (go) {
              ProjectApi.delete(theProject, request, output)
            }
        }
        output(s"${getClass.getName}:main() EXIT-OK<br/>")
      } else {
        output(s"${getClass.getName}:main() EXIT-ERROR Usage: ${getClass.getName} project-id [,GO]<br/>")
      }
    } catch {
      case t: Throwable =>
        output(t.getStackTrace.map(_.toString).mkString("<br/>"))
    } finally {
      output("</body></html>")
    }
  }

}
