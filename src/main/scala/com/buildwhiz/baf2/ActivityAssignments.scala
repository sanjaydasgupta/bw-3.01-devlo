package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ActivityAssignments extends HttpServlet with HttpUtils {

  private def userCanManage(user: DynDoc, project: DynDoc, phase: DynDoc, process: DynDoc, activity: DynDoc): Boolean = {
    val userOid = user._id[ObjectId]
    PersonApi.isBuildWhizAdmin(Right(user)) || Seq(process, phase, project).exists(p => {
      (p.has("admin_person_id") && p.admin_person_id[ObjectId] == userOid) ||
      p.assigned_roles[Many[Document]].exists(_.role_name[String].matches("(Project|Phase)-Manager"))
    })
  }

  private def personOid2NameMap(assignments: Seq[DynDoc]): Map[ObjectId, String] = {
    val personOids: Seq[ObjectId] = assignments.filter(_.has("person_id")).map(_.person_id[ObjectId]).distinct
    val persons: Seq[DynDoc] = PersonApi.personsByIds(personOids)
    persons.map(p => (p._id[ObjectId], s"${p.first_name[String]} ${p.last_name[String]}")).toMap
  }

  private def organizationOid2NameMap(assignments: Seq[DynDoc]): Map[ObjectId, String] = {
    val organizationOids: Seq[ObjectId] = assignments.filter(_.has("organization_id")).
        map(_.organization_id[ObjectId]).distinct
    val organizations: Seq[DynDoc] = OrganizationApi.organizationsByIds(organizationOids)
    organizations.map(org => (org._id[ObjectId], org.name[String])).toMap
  }

  private def augmentAssignments(assignmentDetails: Seq[(DynDoc, DynDoc, DynDoc, DynDoc, DynDoc)], user: DynDoc):
      Seq[Document] = {
    val justAssignments = assignmentDetails.map(_._1)
    val personNameCache = personOid2NameMap(justAssignments)
    val organizationNameCache = organizationOid2NameMap(justAssignments)
    assignmentDetails.map(detail => {
      val (assignment, project, phase, process, activity) = detail
      val (processName, processBpmnName, activityBpmnName) =
        (process.name[String], process.bpmn_name[String], activity.bpmn_name[String])
      val fullProcessName = if (processBpmnName == activityBpmnName)
        processName
      else
        s"$processName ($activityBpmnName)"
      val canManage = userCanManage(user, project, phase, process, activity) && activity.status[String] != "ended"
      val assignmentDoc = new Document("_id", assignment._id[ObjectId]).append("role", assignment.role[String]).
        append("activity_name", activity.name[String]).append("activity_id", activity._id[ObjectId].toString).
        append("process_name", processName).append("can_delete", true).append("can_manage", canManage).
        append("bpmn_name", activityBpmnName).append("process_id", process._id[ObjectId].toString).
        append("is_main_role", activity.role[String] == assignment.role[String]).
        append("full_process_name", fullProcessName).append("phase_id", phase._id[ObjectId].toString)
      if (assignment.has("organization_id")) {
        val orgOid = assignment.organization_id[ObjectId]
        assignmentDoc.append("organization_id", orgOid)
        assignmentDoc.append("organization_name", organizationNameCache(orgOid))
      } else {
        assignmentDoc.append("organization_id", "")
        assignmentDoc.append("organization_name", "")
      }
      if (assignment.has("person_id")) {
        val personOid = assignment.person_id[ObjectId]
        assignmentDoc.append("person_id", personOid)
        assignmentDoc.append("person_name", personNameCache(personOid))
      } else {
        assignmentDoc.append("person_id", "")
        assignmentDoc.append("person_name", "")
      }
      if (assignment.has("individual_role")) {
        val indRole = assignment.individual_role[Many[String]]
        assignmentDoc.append("individual_role", indRole)
      } else {
        assignmentDoc.append("individual_role", Seq.empty[String].asJava)
      }
      if (assignment.has("document_access")) {
        val docAccess = assignment.document_access[Many[String]]
        assignmentDoc.append("document_access", docAccess)
      } else {
        assignmentDoc.append("document_access", Seq.empty[String].asJava)
      }
      assignmentDoc
    })
  }

  private def sorter(in: Seq[Document]): Seq[Document] = {
    in.sortBy(d => {
      val dd: DynDoc = d
      val role = dd.role[String]
      val role2 = if (RoleListSecondary.secondaryRoles.contains(role)) s"999$role" else s"000$role"
      (dd.process_name[String], dd.activity_name[String], role2)
    })
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val optProjectOid = parameters.get("project_id").map(new ObjectId(_))
      val optPhaseOid = parameters.get("phase_id").map(new ObjectId(_))
      val optActivityOid = parameters.get("activity_id").map(new ObjectId(_))

      val assignmentDetails: Seq[(DynDoc, DynDoc, DynDoc, DynDoc, DynDoc)] =
          (optActivityOid, optPhaseOid, optProjectOid) match {
        case (Some(activityOid), _, _) =>
          val parentProcess = ActivityApi.parentProcess(activityOid)
          val parentPhase = ProcessApi.parentPhase(parentProcess._id[ObjectId])
          val parentProject = PhaseApi.parentProject(parentPhase._id[ObjectId])
          val theActivity = ActivityApi.activityById(activityOid)
          val activityAssignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.find(Map("activity_id" -> activityOid))
          activityAssignments.map(aa => (aa, parentProject, parentPhase, parentProcess, theActivity))
        case (None, Some(phaseOid), _) =>
          val parentProject = PhaseApi.parentProject(phaseOid)
          val thePhase = PhaseApi.phaseById(phaseOid)
          val processes = PhaseApi.allProcesses(phaseOid)
          val activityOid2process: Map[ObjectId, DynDoc] =
            processes.flatMap(p => ProcessApi.allActivities(p).map(a => (a._id[ObjectId], p))).toMap
          val activities = PhaseApi.allActivities(phaseOid)
          val activityOid2Activity: Map[ObjectId, DynDoc] = activities.map(a => (a._id[ObjectId], a)).toMap
          val activityAssignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.find(Map("phase_id" -> phaseOid))
          activityAssignments.map(aa => (aa, parentProject, thePhase, activityOid2process(aa.activity_id[ObjectId]),
            activityOid2Activity(aa.activity_id[ObjectId])))
        case (None, None, Some(projectOid)) =>
          val theProject = ProjectApi.projectById(projectOid)
          val processes = ProjectApi.allProcesses(projectOid)
          val activityOid2process: Map[ObjectId, DynDoc] =
            processes.flatMap(p => ProcessApi.allActivities(p).map(a => (a._id[ObjectId], p))).toMap
          val activities = ProjectApi.allActivities(projectOid)
          val activityOid2Activity: Map[ObjectId, DynDoc] = activities.map(a => (a._id[ObjectId], a)).toMap
          val activityAssignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.find(Map("project_id" -> projectOid))
          activityAssignments.map(aa => (aa, theProject, PhaseApi.phaseById(aa.phase_id[ObjectId]),
              activityOid2process(aa.activity_id[ObjectId]), activityOid2Activity(aa.activity_id[ObjectId])))
        case _ => throw new IllegalArgumentException("Required parameters not provided")
      }

      val assignments = augmentAssignments(assignmentDetails, user)

      val assignmentList = sorter(assignments).map(bson2json).mkString("[", ", ", "]")
      response.getWriter.print(assignmentList)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}