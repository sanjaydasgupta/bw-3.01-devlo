package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.{PersonApi, ProjectApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.HttpUtils
import com.mongodb.client.model.UpdateOneModel
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import scala.annotation.unused

@unused
object GlobalConfigSetup extends HttpUtils {

  private val phaseConfigs: Seq[(String, String)] = Seq(
    ("issues-issues-ACTIVITY-COMPLETE-BUTTON", "{'roles': ['Responsible-Member', 'Support-Member']}"),
    ("issues-issues-ACTIVITY-ROUTE-BUTTON", "{'roles': ['Responsible-Member', 'Support-Member']}"),
    ("issues-issues-CAN-ADD-ACTIVITY-CHECKLIST-BUTTON", "{'roles': ['Phase-Manager', 'Responsible-Member', 'Support-Member']}"),
    ("issues-issues-CAN-BYPASS-ACTIVITY-BUTTON", "{'roles': ['Phase-Manager']}"),
    ("issues-issues-CAN-DELETE-ACTIVITY-CHECKLIST-BUTTON", "{'roles': ['Phase-Manager', 'Responsible-Member', 'Support-Member']}"),
    ("issues-issues-CLONE-PROCESS-BUTTON", "{'roles': ['Phase-Teams-Members']}"),
    ("issues-issues-EDIT-ISSUE-BUTTON", "{'roles': ['Phase-Manager', 'Responsible-Member', 'Support-Member']}"),
    ("issues-issues-EDIT-RESPONSIBLE-MEMBER-BUTTON", "{'roles': ['Phase-Manager', 'Responsible-Member', 'Support-Member']}"),
    ("issues-issues-PROCESS-DELETE-BUTTON", "{'roles': ['BW-Admin', 'Phase-Manager', 'Creator']}"),
    ("issues-periodic-issue-ADD-BUTTON", "{'roles': ['Phase-Manager']}"),
    ("issues-periodic-issue-DELETE-BUTTON", "{'roles': ['Phase-Manager']}"),
    ("issues-periodic-issue-EDIT-BUTTON", "{'roles': ['Phase-Manager']}"),
    ("issues-workflow-ACTIVITY-ADD-BUTTON", "{'roles': ['Phase-Manager']}"),
    ("issues-workflow-ACTIVITY-DELETE-BUTTON", "{'roles': ['Phase-Manager']}"),
    ("issues-workflow-ACTIVITY-EDIT-BUTTON", "{'roles': ['Phase-Manager']}"),
    ("issues-workflow-ACTIVITY-PREDECESSOR-ADD-EDIT-DELETE", "{'roles': ['Phase-Manager']}"),
    ("issues-workflow-ADD-WORKFLOW-BUTTON", "{'roles': ['Phase-Manager']}"),
    ("issues-workflow-DELETE-WORKFLOW-BUTTON", "{'roles': ['Phase-Manager']}"),
    ("issues-workflow-EDIT-WORKFLOW-INFO-PARAMETERS", "{'roles': ['Phase-Manager']}"),
    ("issues-workflow-EXPORT-DATA-BUTTON", "{'roles': ['BW-Admin']}"),
    ("issues-workflow-MANAGE-VISIBLE-FLAG", "{'roles': ['BW-Admin']}"),
    ("overall-activities-CAN-ASSIGN-TEAM-BUTTON", "{'roles': ['Phase-Manager']}"),
    ("phaseplan-processdia-OVERALL-EDITABILITY", "{'roles': ['Phase-Manager']}"),
    ("teams-teams-CAN-ADD-PHASE-TEAM-MEMBER-BUTTON", "{'roles': ['Project-Manager', 'Phase-Manager']}"),
    ("teams-teams-CAN-DELETE-PHASE-TEAM-MEMBER-BUTTON", "{'roles': ['Project-Manager', 'Phase-Manager']}"),
    ("teams-teams-CAN-EDIT-PHASE-TEAM-MEMBER-BUTTON", "{'roles': ['Project-Manager', 'Phase-Manager']}")
  )

  private def setupPhases(go: Boolean, output: String => Unit): Unit = {
    output(s"${getClass.getName}:setupPhases() ENTRY<br/>")
    val allProjects = ProjectApi.listProjects()
    val allPhaseOids: Seq[(ObjectId, ObjectId)] = allProjects.flatMap(project => {
      val projectOid = project._id[ObjectId]
      val phaseOids = project.phase_ids[Many[ObjectId]]
      phaseOids.map(phaseOid => (projectOid, phaseOid))
    })
    if (go) {
      val bulkWritesBuffer: Many[UpdateOneModel[Document]] = allPhaseOids.flatMap(oids => {
        phaseConfigs.map(conf => {
          new UpdateOneModel[Document](Map("project_id" -> oids._1, "phase_id" -> oids._2, "control_name" -> conf._1),
            Map($set -> Map("enable" -> conf._2)))
        })
      })
    } else {
      val anyControlRegex = phaseConfigs.map(_._1).mkString("|")
      val countsByPhase = allPhaseOids.map(_._2).map(phaseOid => {
        val count = BWMongoDB3.global_configs.countDocuments(Map("phase_id" -> phaseOid,
            "control_name" -> Map($regex -> anyControlRegex)))
        (phaseOid, count)
      })
    }
    output(s"${getClass.getName}:setupPhases() EXIT<br/>")
  }

  private val projectConfigs: Seq[(String, String)] = Seq(
    ("project-CREATE-PHASE-BUTTON", "{'roles': ['BW-Admin']}"),
    ("project-IMAGE-UPLOAD-BUTTON", "{'roles': ['Project-Manager']}"),
    ("project-OVERALL-EDITABILITY", "{'roles': ['Project-Manager']}"),
    ("project-list-Management", "{'roles': ['Project-Manager']}"),
    ("project-tags-CREATE-BUTTON", "{'roles': ['Project-Manager']}"),
    ("project-tags-DELETE-BUTTON", "{'roles': ['Project-Manager']}"),
    ("project-tags-EDIT-BUTTON", "{'roles': ['Project-Manager']}"),
    ("project-tags-EXCEL-UPLOAD-BUTTON", "{'roles': ['Project-Manager']}"),
    ("project-units-EXCEL-UPLOAD-BUTTON", "{'roles': ['Project-Manager']}"),
    ("teams-teams-CAN-ADD-BUTTON", "{'roles': ['BW-Admin', 'Project-Manager']}"),
    ("teams-teams-CAN-ADD-PROJECT-TEAM-MEMBER-BUTTON", "{'roles': ['BW-Admin', 'Project-Manager']}"),
    ("teams-teams-CAN-DELETE-BUTTON", "{'roles': ['Project-Manager', 'Phase-Manager']}"),
    ("teams-teams-CAN-DELETE-PROJECT-TEAM-MEMBER-BUTTON", "{'roles': ['BW-Admin', 'Project-Manager']}"),
    ("teams-teams-CAN-EDIT-BUTTON", "{'roles': ['BW-Admin', 'Project-Manager']}"),
    ("teams-teams-CAN-EDIT-PROJECT-TEAM-MEMBER-BUTTON", "{'roles': ['BW-Admin', 'Project-Manager']}"),
    ("zones-CAN-ADD-ZONE-BUTTON", "{'roles': ['BW-Admin', 'Project-Manager']}"),
    ("zones-CAN-DELETE-ZONE-BUTTON", "{'roles': ['BW-Admin', 'Project-Manager']}"),
    ("zones-CAN-EDIT-ZONE-BUTTON", "{'roles': ['BW-Admin', 'Project-Manager']}"),
    ("zones-CAN-UPLOADE-EXCEL-ZONE-BUTTON", "{'roles': ['BW-Admin', 'Project-Manager']}")
  )

  private def setupProjects(go: Boolean, output: String => Unit): Unit = {
    output(s"${getClass.getName}:setupProjects() ENTRY<br/>")
    val allProjects = ProjectApi.listProjects()
    if (go) {
      val bulkWritesBuffer: Many[UpdateOneModel[Document]] = allProjects.flatMap(project => {
        val projectOid = project._id[ObjectId]
        projectConfigs.map(conf => {
          new UpdateOneModel[Document](Map("project_id" -> projectOid, "control_name" -> conf._1),
            Map($set -> Map("enable" -> conf._2)))
        })
      })
    } else {
      val anyControlRegex = projectConfigs.map(_._1).mkString("|")
      val allProjectOids = allProjects.map(_._id[ObjectId])
      val countsByPhase = allProjectOids.map(projectOid => {
        val count = BWMongoDB3.global_configs.countDocuments(Map("project_id" -> projectOid,
          "control_name" -> Map($regex -> anyControlRegex)))
        (projectOid, count)
      })
    }
    output(s"${getClass.getName}:setupProjects() EXIT<br/>")
  }

  private def getConfigs: Seq[DynDoc] = {
    val aggregationPipeline: Many[Document] = Seq(
      Map("$addFields" -> Map("key" -> Seq("$project_id", "$phase_id", "$site_name"))),
      Map("$group" -> Map("_id" -> "$key", "control_names" -> Map($push -> "$control_name"),
        "enables" -> Map($push -> "$enable"), "options" -> Map($push -> "$options"))),
      Map("$project" -> Map("project_id" -> Map("$arrayElemAt" -> Seq("$_id", 0)),
        "phase_id" -> Map("$arrayElemAt" -> Seq("$_id", 1)), "site_name" -> Map("$arrayElemAt" -> Seq("$_id", 2)),
        "control_names" -> true, "enables" -> true, "options" -> true, "_id" -> false))
    ).map(new Document(_))
    val configs: Seq[DynDoc] = BWMongoDB3.global_configs.aggregate(aggregationPipeline)
    configs
  }

  private def setup(go: Boolean, output: String => Unit): Unit = {
    val configs = getConfigs
//    output("""<table border="1" width="100%">""")
//    output("<tr><td>project</td><td>phase</td><td>site</td><td>control</td><td>enables</td><td>options</td></tr>")
//    configs.foreach(config => {
//      val proj_ids = config.project_id[ObjectId]
//      val ph_ids = config.phase_id[ObjectId]
//      val site = config.site_name[String]
//      val controls = config.control_names[Many[String]]
//      val enables = config.enables[Many[Document]]
//      val options = config.options[Many[_]]
//      val optSeq: Seq[String] = options.map {
//        case doc: Document => doc.toJson()
//        case manyDocs: Many[Document] => manyDocs.map(_.asDoc.toJson).mkString("[", ", ", "]")
//        case manyManyDocs: Many[Many[Document]] => manyManyDocs.map(_.map(_.asDoc.toJson).mkString("[", ", ", "]")).mkString(", ")
//      }
//      val optStr = optSeq.mkString(", ")
//      output(s"<tr><td>$proj_ids</td><td>$ph_ids</td><td>$site</td><td>${controls.mkString(", ")}</td><td>${enables.map(_.asDoc.toJson).mkString(", ")}</td><td>$optStr</td></tr>")
//    })
//    output("</table>")
    // Projects ...
    val allProjects = ProjectApi.listProjects()
    val allProjectControlNames: Set[String] = projectConfigs.map(_._1).toSet
    val projectMissingControls: Seq[(ObjectId, Seq[String])] = allProjects.map(project => {
      val projectOid = project._id[ObjectId]
      configs.find(conf => conf.project_id[ObjectId] == projectOid && conf.phase_id[ObjectId] == null) match {
        case Some(c) =>
          val existingControlNames: Set[String] = c.control_names[Many[String]].toSet
          (projectOid, (allProjectControlNames -- existingControlNames).toSeq)
        case None => (projectOid, allProjectControlNames.toSeq)
      }
    })
    output("""<table border="1" width="100%">""")
    output("<tr><td>project</td><td>missing controls</td></tr>")
    projectMissingControls.foreach(c => {
      output(s"""<tr><td>${c._1}</td><td>${c._2.mkString(", ")}</td></tr>""")
    })
    output("</table>")
    // Phases ...
    val allPhaseControlNames: Set[String] = phaseConfigs.map(_._1).toSet
    val allPhaseOids: Seq[ObjectId] = allProjects.flatMap(_.phase_ids[Many[ObjectId]])
    val phaseMissingControls: Seq[(ObjectId, Seq[String])] = allPhaseOids.map(phaseOid => {
      configs.find(conf => conf.phase_id[ObjectId] == phaseOid) match {
        case Some(c) =>
          val existingControlNames: Set[String] = c.control_names[Many[String]].toSet
          (phaseOid, (allPhaseControlNames -- existingControlNames).toSeq)
        case None => (phaseOid, allPhaseControlNames.toSeq)
      }
    })
    output("""<table border="1" width="100%">""")
    output("<tr><td>phase</td><td>missing controls</td></tr>")
    phaseMissingControls.foreach(c => {
      output(s"""<tr><td>${c._1}</td><td>${c._2.mkString(", ")}</td></tr>""")
    })
    output("</table>")
    if (go) {
      // setupProjects(go, output)
      // setupPhases(go, output)
    }
  }

  @unused
  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    val go = args.length >= 1 && args(0) == "GO"
    val writer = response.getWriter
    def output(s: String): Unit = writer.print(s)
    response.setContentType("text/html")
    output(s"<html><body>")

    output(s"${getClass.getName}:main() ENTRY<br/>")
    val user: DynDoc = getUser(request)
    if (!PersonApi.isBuildWhizAdmin(Right(user)) || user.first_name[String] != "Sanjay") {
      throw new IllegalArgumentException("Not permitted")
    }
    setup(go, output)
    output(s"${getClass.getName}:main() EXIT-OK<br/>")
    output("</body></html>")
  }

}
