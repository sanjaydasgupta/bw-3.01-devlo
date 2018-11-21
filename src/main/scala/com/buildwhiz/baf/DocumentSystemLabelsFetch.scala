package com.buildwhiz.baf

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class DocumentSystemLabelsFetch extends HttpServlet with HttpUtils {

  val labels = Seq(
    "Architecture", "Architecture.Cover-Sheet", "Architecture.Site-Plan", "Architecture.Floor-Plans",
    "Architecture.Schedules", "Architecture.Electrical-Plan", "Architecture.Sections", "Architecture.Elevations",
    "Architecture.3D-Perspectives", "Architecture.Details",

    "Building-Science", "Building-Science.FLOOR-PLAN", "Building-Science.Other", "Building-Science.Details",
    "Building-Science.support-docs",

    "Electrical", "Electrical.Notes", "Electrical.Single-Line-Dia.", "Electrical.Energy-Calcs",
    "Electrical.Common-Area-Elec-Plans",

    "Elevator", "Elevator.Submittals", "Elevator.Brochure",

    "Fire-Alarm", "Fire-Alarm.Plans", "Fire-Alarm.Other",

    "Fire-Sprinkler", "Fire-Sprinkler.Underground", "Fire-Sprinkler.Other",

    "Interior", "Interior.Details",

    "Mechanical", "Mechanical.Notes", "Mechanical.Schedules", "Mechanical.Specifications",
    "Mechanical.Energy-Calcs", "Mechanical.Mech-Plans", "Mechanical.Details", "Mechanical.Controls",
    "Mechanical.Work-dwgs", "Mechanical.Cut-sheets",

    "Plumbing", "Plumbing.Notes", "Plumbing.Calculations", "Plumbing.Specifications", "Plumbing.Plumbing-Plans",
    "Plumbing.Details",

    "Structure", "Structure.General-Notes", "Structure.Details",
    "Structure.Foundation-Plans", "Structure.Framing-Plans", "Structure.Elevations", "Structure.3D-Perspectives",

    "Studies", "Studies.Geo-Technical",
  )

  private def getProjectLabels(projectOid: ObjectId): Seq[String] = {
    val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
    val projectLabels: Seq[String] = if (project.has("labels"))
      project.labels[Many[String]]
    else
      Seq.empty[String]
    val documents: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("project_id" -> projectOid))
    val documentLabels: Seq[String] = documents.flatMap(doc => {
      if (doc.has("labels")) {
        doc.labels[Many[String]]
      } else {
        Seq.empty[String]
      }
    })
    (labels ++ projectLabels ++ documentLabels).distinct
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val parameters = getParameterMap(request)
      val projectOid = new ObjectId(parameters("project_id"))
      val projectLabels = getProjectLabels(projectOid)
      val boxedLabels = projectLabels.map(label => s"""{"name":"$label"}""")
      response.getWriter.println(boxedLabels.mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${projectLabels.length} labels)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}
