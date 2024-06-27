package com.buildwhiz.web

import com.buildwhiz.infra.BWMongoDBLib
import com.buildwhiz.infra.BWMongoDBLib._
import com.buildwhiz.utils.BWLogger
import com.buildwhiz.infra.DynDoc
//import com.buildwhiz.infra.DynDoc._

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class LibDump extends HttpServlet {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val t0 = System.currentTimeMillis()
    val libPersons: Seq[DynDoc] = BWMongoDBLib.people.find()
    response.setContentType("text/plain")
    for (p <- libPersons) {
      response.getWriter.println(p.asDoc.toJson)
    }
    val delay = System.currentTimeMillis() - t0
    val message = s"EXIT-OK (time: $delay ms)"
    BWLogger.log(getClass.getName, request.getMethod, message, request)
  }

}
