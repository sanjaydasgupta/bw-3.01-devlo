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
    response.getWriter.println(s"${getClass.getName}:main() ENTRY")
    val user: DynDoc = getUser(request)
    if (!PersonApi.isBuildWhizAdmin(Right(user)) || user.first_name[String] != "Sanjay") {
      throw new IllegalArgumentException("Not permitted")
    }
    if (args.length >= 1) {
      val projectOid = new ObjectId(args(0))
      BWMongoDB3.projects.find(Map("_id" -> projectOid)).headOption match {
        case None =>
          response.getWriter.println(s"No such project ID: '${args(0)}'")
        case Some(theProject) =>
          response.getWriter.println(s"Found project: '${theProject.asDoc.toJson}'")
          val go: Boolean = args.length == 2 && args(1) == "GO"
          if (go) {
            response.getWriter.println(s"DELETING project ...")
            ProjectApi.delete(theProject, request)
            response.getWriter.println(s"DELETE project complete")
          }
      }
      response.getWriter.println(s"${getClass.getName}:main() EXIT-OK")
    } else {
      response.getWriter.println(s"${getClass.getName}:main() EXIT-ERROR Usage: ${getClass.getName} phase-id [,GO]")
    }
  }

}
