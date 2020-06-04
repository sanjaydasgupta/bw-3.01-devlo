package com.buildwhiz.baf2

import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class RoleList extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val omniClass33rows = omniClassTable33.map(pair => s""""${pair._2}"""")
      response.getWriter.println(omniClass33rows.mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", s"EXIT-OK (${omniClass33rows.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  val omniClassTable33 = Seq(
    ("33-11 11 00", "Regional Planning"),
    ("33-11 41 00", "Urban Planning"),
    ("33-11 51 00", "Environmental Planning"),
    ("33-11 61 21", "Historic Building Conservation Planning"),
    ("33-21 11 00", "Architecture"),
    ("33-21 21 00", "Landscape Architecture"),
    ("33-21 23 00", "Interior Design"),
    ("33-21 27 00", "Graphic Design"),
    ("33-21 31 11", "Civil Engineering"),
    ("33-21 31 11 11", "Geotechnical Engineering"),
    ("33-21 31 14", "Structural Engineering"),
    ("33-21 31 17", "Mechanical Engineering"),
    ("33-21 31 17 11", "Plumbing Engineering"),
    ("33-21 31 17 21", "Fire Protection Engineering"),
    ("33-21 31 17 31", "Heating, Ventilation, and Air-Conditioning Engineering"),
    ("33-21 31 17 34", "Energy Monitoring and Controls Engineering"),
    ("33-21 31 21", "Electrical Engineering"),
    ("33-21 31 21 31", "Low Voltage Electrical Engineering"),
    ("33-21 31 24 21", "Wind Engineering"),
    ("33-21 31 31", "Environmental Engineering"),
    ("33-21 31 99 11", "Acoustical/Emanations Shielding Engineering"),
    ("33-21 51 11", "Drafting"),
    ("33-21 51 19 11", "Photography"),
    ("33-21 51 19 13", "Commercial Photography"),
    ("33-21 99 31 13", "Solar Design"),
    ("33-21 99 46", "Vertical Conveyance Design"),
    ("33-23 11 00", "Surveying"),
    ("33-23 41 00", "Geotechnical Investigation"),
    ("33-25 11 00", "Cost Estimation"),
    ("33-25 15 00", "Architectural and Engineering Management"),
    ("33-25 16 00", "Construction Management"),
    ("33-25 16 11", "General Contracting"),
    ("33-25 16 13", "Subcontracting"),
    ("33-25 31 00", "Contract Administration"),
    ("33-25 41 00", "Procurement Administration"),
    ("33-25 51 00", "Quality Assurance"),
    ("33-25 51 11", "Construction Inspection"),
    ("33-25 51 13", "Building Inspection"),
    ("33-41 03 11 11", "Hazardous Material Abatement Services"),
    ("33-41 03 21", "Demolition Services"),
    ("33-41 03 31", "Fence Erection Services")
  )
}
