package com.buildwhiz
import com.buildwhiz.baf2.{PhaseApi, ProjectApi}
import com.buildwhiz.baf3.LibraryContentsUtility
import com.buildwhiz.infra.{BWMongoDB3, DynDoc, GoogleDriveRepository}
import com.buildwhiz.infra.BWMongoDB3._
import org.bson.Document
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.tools.scripts.GlobalConfigSetup.{getConfigs, projectConfigs}
import org.bson.types.ObjectId

import scala.jdk.CollectionConverters._

object ScratchPad extends App {

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
    output("""<table border="1" width="100%">""")
    output("<tr><td>project</td><td>phase</td><td>site</td><td>control</td><td>enables</td><td>options</td></tr>")
    val configs = getConfigs
    configs.foreach(config => {
      val proj_ids = config.project_id[ObjectId]
      val ph_ids = config.phase_id[ObjectId]
      val site = config.site_name[String]
      val controls = config.control_names[Many[String]]
      val enables = config.enables[Many[Document]]
      val options = config.options[Many[_]]
      val optSeq: Seq[String] = options.map {
        case doc: Document => doc.toJson()
        case manyDocs: Many[_] => manyDocs.asInstanceOf[Many[Document]].map(_.asDoc.toJson).mkString("[", ", ", "]")
        // case manyManyDocs: Many[Many[Document]] => manyManyDocs.map(_.map(_.asDoc.toJson).mkString("[", ", ", "]")).mkString(", ")
      }
      val optStr = optSeq.mkString(", ")
      output(s"<tr><td>$proj_ids</td><td>$ph_ids</td><td>$site</td><td>${controls.mkString(", ")}</td><td>${enables.map(_.asDoc.toJson).mkString(", ")}</td><td>$optStr</td></tr>")
    })
    output("</table>")
    val allControlNames: Set[String] = projectConfigs.map(_._1).toSet
    val allProjects = ProjectApi.listProjects()
    val projectMissingControls: Seq[(ObjectId, Seq[String])] = allProjects.map(project => {
      val projectOid = project._id[ObjectId]
      configs.find(conf => conf.project_id[ObjectId] == projectOid && conf.phase_id[ObjectId] == null) match {
        case Some(c) =>
          val existingControlNames: Set[String] = c.control_names[Many[String]].toSet
          (projectOid, (allControlNames -- existingControlNames).toSeq)
        case None => (projectOid, allControlNames.toSeq)
      }
    })
    output("""<table border="1" width="100%">""")
    output("<tr><td>project</td><td>missing controls</td></tr>")
    projectMissingControls.foreach(c => {
      output(s"""<tr><td>${c._1}</td><td>${c._2.mkString(", ")}</td></tr>""")
    })
    output("</table>")
    if (go) {
      // setupProjects(go, output)
      // setupPhases(go, output)
    }
  }

  setup(false, println(_))
}