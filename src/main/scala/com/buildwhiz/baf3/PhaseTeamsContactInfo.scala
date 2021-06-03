package com.buildwhiz.baf3

import com.buildwhiz.baf2.{OrganizationApi, PersonApi, PhaseApi}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PhaseTeamsContactInfo extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val phaseRecord: DynDoc = PhaseApi.phaseById(phaseOid)
      val user: DynDoc = getPersona(request)
      val teamOids: Seq[ObjectId] = phaseRecord.get[Many[Document]]("team_assignments") match {
        case Some(teamAssignments) => teamAssignments.map(_.team_id[ObjectId])
        case None => Seq.empty[ObjectId]
      }
      val teams = TeamApi.teamsByIds(teamOids)
      val contactInfo: Seq[String] = teams.flatMap(team => {
        if (team.has("organization_id")) {
          val partner = OrganizationApi.organizationById(team.organization_id[ObjectId])
          val partnerName = partner.name[String]
          team.get[Many[Document]]("team_members") match {
            case Some(memberInfos) => memberInfos.map(memberInfo => {
              val roles = memberInfo.roles[Many[String]].mkString(", ")
              val personRecord = PersonApi.personById(memberInfo.person_id[ObjectId])
              val personName = PersonApi.fullName(personRecord)
              val workPhone: String = personRecord.phones[Many[Document]].find(_.`type`[String] == "work") match {
                case Some(eml) => eml.phone[String]
                case None => ""
              }
              val mobilePhone: String = personRecord.phones[Many[Document]].find(_.`type`[String] == "mobile") match {
                case Some(eml) => eml.phone[String]
                case None => ""
              }
              val email: String = personRecord.emails[Many[Document]].find(_.`type`[String] == "work") match {
                case Some(eml) => eml.email[String]
                case None => ""
              }
              val position = personRecord.get[String]("position") match {
                case Some(pos) => pos
                case None => ""
              }
              Seq(team.team_name[String], partnerName, personName, roles, workPhone, mobilePhone, email, position).
                  mkString("[\"", "\", \"", "\"]")
            })
            case None => Seq.empty[String]
          }
        } else {
          Seq.empty[String]
        }
      })
      response.getWriter.print(contactInfo.mkString("[", ",", "]"))
      response.setContentType("application/json")
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}
