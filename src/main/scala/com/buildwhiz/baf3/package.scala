package com.buildwhiz

import com.buildwhiz.baf2.{PhaseApi, ProjectApi}
import com.buildwhiz.infra.DynDoc
import org.bson.Document
import org.bson.types.ObjectId
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.BWLogger

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

package object baf3 {

  private object OmniClass33 {
    private val omniClass33text =
      """33-11 00 00,Planning Disciplines
        |33-11 21 00,Development Planning
        |33-11 41 00,Urban Planning
        |33-11 51 00,Environmental Planning
        |33-21 00 00,Design Disciplines
        |33-21 11 00,Architecture
        |33-21 21 00,Landscape Architecture
        |33-21 23 00,Interior Design
        |33-21 31 11,Civil Engineering
        |33-21 31 11 11,Geotechnical Engineering
        |33-21 31 14,Structural Engineering
        |33-21 31 17,Mechanical Engineering
        |33-21 31 17 11,Plumbing Engineering
        |33-21 31 17 21,Fire Protection Engineering
        |33-21 31 17 31,"Heating, Ventilation, and Air-Conditioning Engineering"
        |33-21 31 17 34,Energy Monitoring and Controls Engineering
        |33-21 31 21,Electrical Engineering
        |33-21 31 21 31 ,Low Voltage Electrical Engineering
        |33-21 31 24 21,Wind Engineering
        |33-21 31 31,Environmental Engineering
        |33-21 31 99 11,Acoustical/Emanations Shielding Engineering
        |33-21 31 99 21 11,Computer Network Engineering
        |33-21 31 99 21 31,Audiovisual Engineering
        |33-21 51 11,Drafting
        |33-21 51 19 11,Photographic Services
        |33-21 99 10,Building Envelope Design
        |33-21 99 28,Lighting Design
        |33-21 99 31 13,Solar Design
        |33-21 BW 11,Waterproofing
        |33-21 BW 12,Accessibility
        |33-23 00 00,Investigation Disciplines
        |33-23 11 00,Surveying
        |33-23 21 00,Environmental Investigation
        |33-23 21 11,Environmental Impact Investigation
        |33-23 21 21,Air Quality Evaluation Investigation
        |33-23 21 31,Hazardous Materials Investigation
        |33-23 31 00,Hydrological Investigation
        |33-23 41 00,Geotechnical Investigation
        |33-23 51 00,Risk Assessment
        |33-25 00 00,Project Management Disciplines
        |33-25 BW 11,Project-Manager
        |33-25 11 00,Cost Estimation
        |33-25 16 00,Construction Management
        |33-25 16 11,General Contracting
        |33-25 21 00,Scheduling
        |33-25 31 00,Contract Administration
        |33-25 41 00,Procurement Administration
        |33-25 51 11,Construction Inspection
        |33-25 51 13,Building Inspection
        |33-81 00 00,Support Disciplines
        |33-81 11 00,Legal Services
        |33-81 11 17,Permitting
        |33-81 21 11,Public Relations
        |33-81 21 21 13,Computer and Information Systems Management
        |33-81 31 00,Finance
        |33-81 31 11,Banking
        |33-81 31 14,Accounting
        |33-81 31 17,Insurance""".stripMargin

    private case class OC33(code: String, name: String) {
      def isGroup: Boolean = code.matches(".+00 00")
      private def shortName: String = name.split("\\s+").init.mkString(" ")
      override def toString: String = "%s (%s)".format(if (isGroup) shortName else name, code)
      def toStringWithoutCode: String = if (isGroup) shortName else name
    }

    private val oc33entries: Seq[OC33] = omniClass33text.split("\n").map(_.split(",").map(_.trim.replace("\"", ""))).
        map(array => OC33(array(0), array(1))).toSeq

    def omniClass33groups(): Seq[String] = oc33entries.filter(_.isGroup).map(_.toStringWithoutCode)
    def omniClass33skills(): Seq[String] = oc33entries.filterNot(_.isGroup).map(_.toString)

    def omniClass33skillsByGroup(): Map[String, Seq[String]] = {
      @tailrec
      def makeGroupSkillPairs(group: OC33, entries: Seq[OC33], acc: Seq[(OC33, OC33)] = Nil): Seq[(OC33, OC33)] = {
        entries match {
          case sequence if sequence.isEmpty => acc
          case sequence =>
            val (head, tail) = (sequence.head, sequence.tail)
            if (head.isGroup)
              makeGroupSkillPairs(head, tail, acc)
            else
              makeGroupSkillPairs(group, tail, (group, head) +: acc)
        }
      }
      val pairs: Seq[(OC33, OC33)] = makeGroupSkillPairs(oc33entries.head, oc33entries.tail)
      pairs.groupBy(_._1).map(ks => (ks._1.toStringWithoutCode, ks._2.map(_._2.toString).reverse))
    }

  }

  val masterData: Map[String, _] = Map(
    "DeliverableInfoSet__deliverable_type" -> Seq("Data", "Document", "Work"),
    "Docs__category" -> Seq("Budget", "City-Applications", "City-Approvals", "Contracts", "Deliverables",
        "Del-Specs", "Financial-Applications", "Invoices", "Meeting-Notes", "Progress-Reports", "Specification"
      , "Submittals", "Task-Specs", "Work-Scope"),
    "Docs__file_format" -> Seq("PDF", "Folder", "DOC", "DOCX", "XLS", "XLSX", "GIF", "JPEG", "JPG", "PPT", "PPTX",
        "BLD", "RVT", "RFA", "RTE", "RFT", "SVG", "DGN", "DWF", "DWG", "DXF", "IFC", "SAT", "SKP", "HTML", "gbXML",
        "PNG", "TIF", "TIFF", "TXT", "XML", "ZIP", "JSON", "BMP", "TXT"),
    "Docs__tags" -> Seq("Architecture", "Contract", "Current-Plan", "EIR", "Geotech", "HRE", "Invoice", "Land-Use",
      "Meeting-Notes", "Other", "Pre-App-Meeting", "Preservation-Alternatives", "Public-Health", "Report",
      "Soils-Report", "Survey", "Traffic-Study", "Wind-Study"),
    "Issue__category" -> Seq("Design", "Material", "Procurement", "Safety", "Security"),
    "Partners__skills" -> OmniClass33.omniClass33skills(),
    "ProjectInfoSet__building_use" -> Seq(
      "Assembly Facility", "Education Facility", "Public Service Facility", "Cultural Facility",
      "Recreation Facility", "Housing Facility", "Retail Facility", "Health Care Facility",
      "Hospitality Facility", "Lodging Facility", "Office Facility", "Research Facility",
      "Production Facility", "Storage Facility", "Water Infrastructure Facility",
      "Energy Infrastructure Facility", "Waste Infrastructure Facility",
      "Information Infrastructure Facility", "Transportation Facility", "Mixed-Use Facility", "Land",
    ),
    "ProjectInfoSet__construction_type" -> Seq("Steel Frame", "Wood Frame", "Concrete"),
    "ProjectInfoSet__project_type" -> Seq("Building", "Structure", "Mobile Structure",
      "Linear Form", "Construction Entity Grouping"),
    "ProjectList__scope" -> Seq("all", "current", "future", "past"),
    "PhaseList__scope" -> Seq("all", "current", "future", "past"),
    "PhaseInfo__task_info__scope" -> Seq("all", "current", "future", "past"),
    "PartnerList__serving_area" -> Seq("USA", "Europe", "India", "California:USA"),
    "RFI__category" -> Seq("Code Compliance", "Design Coordination", "Documentation Incomplete", "Constructability",
        "Documentation Conflict", "Field Condition"),
    "Team__group" -> OmniClass33.omniClass33groups(),
    "Team__group_skill_mapping" -> OmniClass33.omniClass33skillsByGroup(),
    "Team__role" -> Seq("Contributor", "Finance-Contact", "Principal", "Team-Admin", "Team-Lead"),
    "Team__individual_role" -> Seq("Contributor", "Finance-Contact", "Manager", "Principal", "Team-Admin", "Team-Lead"),
    "Team__team_role" -> Seq("Observer", "Responsible", "Reviewer", "Support"),
    "Team__omniclass33" -> OmniClass33.omniClass33skills()
  )

  private val menuItemsList: Seq[Document] = Seq(
    Map("access" -> "*I", "navIcon" -> "assets/images/teenyicons/outline-white/home.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/home.svg",
      "navLabel" -> "Home", "routeUrl" -> "/private/project", "toolTipLabel" -> "Home"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/briefcase-alt.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/briefcase-alt.svg",
      "navLabel" -> "Project", "routeUrl" -> "/private/project/project-details", "toolTipLabel" -> "Project"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/layers.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/layers.svg",
      "navLabel" -> "Phase Plan", "routeUrl" -> "/private/phases/phase-details", "toolTipLabel" -> "Phase Plan"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/bell.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/bell.svg",
      "navLabel" -> "Issues", "routeUrl" -> "/private/issues", "toolTipLabel" -> "Issues"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/bag.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/bag.svg",
      "navLabel" -> "Detailed Plan", "routeUrl" -> "/private/activities", "toolTipLabel" -> "Detailed Plan"),
    /*Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/database.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/database.svg",
      "navLabel" -> "Key Data", "routeUrl" -> "/private/key-data", "toolTipLabel" -> "Key Data"),*/
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/text-document-alt.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/text-document-alt.svg",
      "navLabel" -> "Docs", "routeUrl" -> "/private/docs", "toolTipLabel" -> "Docs"),
    /*Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/chat-typing-alt.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/chat-typing-alt.svg",
      "navLabel" -> "RFI", "routeUrl" -> "/private/rfi", "toolTipLabel" -> "RFI"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/bell.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/bell.svg",
      "navLabel" -> "Issues", "routeUrl" -> "/private/issues", "toolTipLabel" -> "Issues"),*/
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/mood-smile.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/mood-smile.svg",
      "navLabel" -> "Teams", "routeUrl" -> "/private/teams", "toolTipLabel" -> "Teams"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/exclamation-circle.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/exclamation-circle.svg",
      "navLabel" -> "Risk", "routeUrl" -> "/private/risk", "toolTipLabel" -> "Risk"),
    /*Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/pin.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/pin.svg",
      "navLabel" -> "Zones", "routeUrl" -> "/private/zones", "toolTipLabel" -> "Zones"),*/
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/area-chart.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/area-chart.svg",
      "navLabel" -> "Finance", "routeUrl" -> "/private/finance", "toolTipLabel" -> "Finance"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/sign.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/sign.svg",
      "navLabel" -> "Procurement", "routeUrl" -> "/private/procurement", "toolTipLabel" -> "Procurement"),
    /*Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/calendar.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/calendar.svg",
      "navLabel" -> "Calendar", "routeUrl" -> "/private/calendar", "toolTipLabel" -> "Calendar"),*/
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/book.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/book.svg",
      "navLabel" -> "Reports", "routeUrl" -> "/private/reports", "toolTipLabel" -> "Reports"),
    Map("access" -> "*I", "navIcon" -> "assets/images/teenyicons/outline-white/contact.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/contact.svg",
      "navLabel" -> "Partners", "routeUrl" -> "/private/partners", "toolTipLabel" -> "Partners"),
    /*Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/contact.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/contact.svg",
      "navLabel" -> "Tenants", "routeUrl" -> "/private/tenants", "toolTipLabel" -> "Tenants"),*/
    Map("access" -> "A", "navIcon" -> "assets/images/teenyicons/outline-white/list-layout.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/list-layout.svg",
      "navLabel" -> "Lists", "routeUrl" -> "/private/lists", "toolTipLabel" -> "Lists")
  )

  def reportFatalException(t: Throwable, className: String, request: HttpServletRequest,
      response: HttpServletResponse): Unit = {
    try {
      val stackTrace = t.getStackTrace.filter(_ != null).map(_.toString).filter(_.contains("com.buildwhiz."))
      val stackMessages = t.getMessage match {
        case null => stackTrace
        case msg => msg +: stackTrace
      }
      val (entryMessages, appMessages) = stackMessages.partition(_.contains("com.buildwhiz.Entry"))
      val errorMessage = if (appMessages.isEmpty) {
        entryMessages.mkString("<br/>\n")
      } else {
        appMessages.mkString("<br/>\n")
      }
      // val errorMessage = stackMessages.mkString("<br/>\n")
      BWLogger.log(className, request.getMethod, s"EXIT-ERROR: $errorMessage", request)
    } catch {
      case t: Throwable =>
        val errorMessage = (t.getMessage +: t.getStackTrace.toSeq.map(_.toString)).mkString("<br/>\n")
        BWLogger.log(className, request.getMethod, s"EXIT-ERROR: $errorMessage", request)
    }
    response.setContentType("application/json")
    val returnJson = new Document("ok", 0).append("message", "See details in log").toJson
    response.getWriter.print(returnJson)
  }

  def landingPageInfo(user: DynDoc, request: HttpServletRequest): Document = {
    try {
      val userProjects = ProjectApi.projectsByUser30(user._id[ObjectId])
      val siteConfigInfos = request.getSession.getAttribute("siteConfigInfos").asInstanceOf[Seq[DynDoc]]
      BWLogger.log(getClass.getName, request.getMethod,
        s"landingPageInfo() found ${siteConfigInfos.length} global_configs records", request)
      siteConfigInfos.headOption match {
        case None =>
          userProjects match {
            case Seq(project) =>
              Map("name" -> "Project", "params" -> Map("project_name" -> project.name[String],
                "project_id" -> project._id[ObjectId]))
            case _ =>
              Map("name" -> "Home", "params" -> Map())
          }
        case Some(siteConfig) =>
          val options: DynDoc = siteConfig.options[Document]
          val landingPage: DynDoc = options.landing_page[Document]
          val params: DynDoc = landingPage.params[Document]
          val projectOid = params.project_id[ObjectId]
          val phaseOid = params.phase_id[ObjectId]
          userProjects.find(_._id[ObjectId] == projectOid) match {
            case Some(project) =>
              if (project.phase_ids[Many[ObjectId]].contains(phaseOid) && PhaseApi.exists(phaseOid)) {
                val phaseName = PhaseApi.phaseById(phaseOid).name[String]
                Map("name" -> landingPage.name[String], "params" -> Map("project_name" -> project.name[String],
                  "project_id" -> project._id[ObjectId], "phase_id" -> phaseOid, "phase_name" -> phaseName))
              } else {
                BWLogger.log(getClass.getName, request.getMethod,
                  s"ERROR Bad phase_id in global_configs record ${siteConfig._id[ObjectId]}", request)
                Map("name" -> "Project", "params" -> Map("project_name" -> project.name[String],
                  "project_id" -> project._id[ObjectId]))
              }
            case _ =>
              if (ProjectApi.exists(projectOid)) {
                val project = ProjectApi.projectById(projectOid)
                if (PhaseApi.exists(phaseOid)) {
                  val phase = PhaseApi.phaseById(phaseOid)
                  BWLogger.log(getClass.getName, request.getMethod,
                    s"WARN User has no role in project_id in global_configs record ${siteConfig._id[ObjectId]}", request)
                  Map("name" -> landingPage.name[String], "params" -> Map("project_name" -> project.name[String],
                    "project_id" -> project._id[ObjectId], "phase_id" -> phaseOid, "phase_name" -> phase.name[String]))
                } else {
                  BWLogger.log(getClass.getName, request.getMethod,
                    s"ERROR Bad phase_id in global_configs record ${siteConfig._id[ObjectId]}", request)
                  Map("name" -> "Project", "params" -> Map("project_name" -> project.name[String],
                    "project_id" -> project._id[ObjectId]))
                }
              } else {
                BWLogger.log(getClass.getName, request.getMethod,
                  s"ERROR Bad project_id in global_configs record ${siteConfig._id[ObjectId]}", request)
                Map("name" -> "Home", "params" -> Map())
              }
          }
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod,
          s"""ERROR ${t.getStackTrace.map(_.toString).mkString("\n")}""", request)
        Map("name" -> "Home", "params" -> Map())
    }
  }

  def displayedMenuItems(userIsAdmin: Boolean, request: HttpServletRequest, userIsManager: Boolean = false,
      starting: Boolean = false, includeHome: Boolean = true): Many[Document] = {
    val siteConfigInfos = request.getSession.getAttribute("siteConfigInfos").asInstanceOf[Seq[DynDoc]] match {
      case null =>
        BWLogger.log(getClass.getName, request.getMethod,
          s"WARN displayedMenuItems(): FAILED to find global_configs information", request)
        Nil
      case scInfo =>
        BWLogger.log(getClass.getName, request.getMethod,
          s"displayedMenuItems(): found ${scInfo.length} global_configs records", request)
        scInfo
    }
    val menuItemsList0 = siteConfigInfos.headOption match {
      case Some(siteConfig) =>
        val options: DynDoc = siteConfig.options[Document]
        val siteMenuList: Seq[String] = options.main_menu_items[Many[String]]
        menuItemsList.filter(m => siteMenuList.contains(m.getString("navLabel")))
      case None =>
        menuItemsList
    }
    val menuItemsList1 = if (includeHome) {
      menuItemsList0
    } else {
      menuItemsList0.filterNot(_.getString("navLabel") == "Home")
    }
    val menuItemsList2 = if (starting && siteConfigInfos.isEmpty) {
      menuItemsList1.filter(_.getString("access").contains("I"))
    } else {
      menuItemsList1
    }
    val filteredMenuItems = if (userIsAdmin) {
      menuItemsList2
    } else if (userIsManager) {
      menuItemsList2.filter(items => items.getString("access").contains("*") || items.getString("access").contains("M"))
    } else {
      menuItemsList2.filter(_.getString("access").contains("*"))
    }
    filteredMenuItems.map(doc => {
      val withoutAccess = doc.entrySet().asScala.map(es => (es.getKey, es.getValue)).filterNot(_._1 == "access").toMap
      new Document(withoutAccess)
    }).asJava
  }

}
