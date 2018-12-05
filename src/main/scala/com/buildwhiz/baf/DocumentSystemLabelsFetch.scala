package com.buildwhiz.baf

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class DocumentSystemLabelsFetch extends HttpServlet with HttpUtils {

  private val standardLabels = Seq(
    "3D-Perspective",

    "Architecture",

    "Brochure", "Building-Science",

    "Calculations", "Common-Area", "Contract", "Controls", "Cover-Sheet", "Curr-Plan", "Cut-sheets",

    "Detail",

    "EIR",

    "Electrical", "Elevation", "Elevator", "Energy", "Environmental",

    "Fire-Alarm", "Fire-Sprinkler", "Floor-Plan", "Foundation", "Framing",

    "General-Notes", "Geo-Technical",

    "HRE",

    "Interior", "Invoice",

    "Land-Use", "Laser-Scan",

    "Mechanical",

    "Meeting-Notes",

    "Notes",

    "Other",

    "Plan",

    "Plumbing",

    "Pre-App-Meeting",

    "Preservation-Alternatives",

    "Public-Health",

    "Report",

    "Schedules", "Sections", "Single-Line-Diagram", "Site", "Soil",

    "Specifications", "Structure", "Study", "Submittal", "Support-Docs", "Survey",

    "Underground",

    "Wind", "Work-Dwgs"
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
    (standardLabels ++ projectLabels ++ documentLabels).distinct.sorted
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
