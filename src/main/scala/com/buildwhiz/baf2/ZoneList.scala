package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ZoneList extends HttpServlet with HttpUtils with DateTimeUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val optProjectOid = parameters.get("project_id").map(new ObjectId(_))
      val optPhaseOid = parameters.get("phase_id").map(new ObjectId(_))
      val optActivityOid = parameters.get("activity_id").map(new ObjectId(_))

      val allZones = ZoneApi.list(optProjectOid, optPhaseOid, optActivityOid)

      val zoneDocuments = allZones.map(zone => {
        new Document("_id", zone._id[ObjectId].toString).append("name", zone.name[String]).
          append("location", zone.location[String]).append("area", zone.area[String]).
          append("description", zone.description[String])
      }).asJava


      //val organizations: Seq[DynDoc] = Seq.empty[DynDoc]
      val user: DynDoc = getUser(request)
//      val detail = parameters.get("detail") match {
//        case None => false
//        case Some(dv) => dv.toBoolean
//      }
      //val parentPhase: DynDoc = ???
      val canManage = true //PhaseApi.canManage(user._id[ObjectId], parentPhase)
//      if (detail) {
//        val parentProject: DynDoc = ???
//        val organizationDetails: java.util.List[Document] = organizations.
//            map(process => ProcessApi.processProcess(process, parentProject, user._id[ObjectId]).asDoc).asJava
//        val result = new Document("organization_list", organizationDetails).append("can_add_organization", canManage)
//        response.getWriter.print(result.toJson)
//      } else {
        val result = new Document("zone_list", zoneDocuments).append("can_add_zone", canManage)
        response.getWriter.print(result.toJson)
//      }
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${zoneDocuments.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}