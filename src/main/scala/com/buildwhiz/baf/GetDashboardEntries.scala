package com.buildwhiz.baf

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.utils.BWLogger
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import org.bson.Document

class GetDashboardEntries extends HttpServlet {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doGet", s"ENTRY", request)

    //val user: DynDoc = getUser(request)

    val entries: Seq[Document] = Seq(
      Map("status" -> "normal", "due_date" -> "None", "status_date" -> "2018-07-31", "url" -> "",
        "description" -> "Project 'Multistory at 3300 Pickwick' awaiting completion"),
      Map("status" -> "urgent", "due_date" -> "2018-09-15", "status_date" -> "2018-08-01", "url" -> "",
        "description" -> "Task 'Floor slab casting' awaiting completion")
    ).map(m => DynDoc.mapToDocument(m))

    response.getWriter.println(entries.map(_.toJson).mkString("[", ", ", "]"))
    response.setContentType("application/json")
    response.setStatus(HttpServletResponse.SC_OK)

    BWLogger.log(getClass.getName, "doGet", s"EXIT-OK", request)
  }

}