package com.buildwhiz

import org.bson.Document
import com.buildwhiz.infra.DynDoc._

import scala.collection.JavaConverters._

package object baf3 {

  val masterData = Map(
    "DeliverableInfoSet__deliverable_type" -> Seq("Data", "Document", "Work"),
    "Docs__category" -> Seq("Floor-Plans", "3D-Models", "Elevations", "Perspectives"),
    "Docs__file_format" -> Seq("BMP", "DOC", "DOCX", "GIF", "JPEG", "JPG", "PDF", "PPT", "PPTX", "PNG", "SVG",
        "TIF", "TIFF", "TXT", "XLS", "XLSX", "XML", "ZIP"),
    "Docs__tags" -> Seq("Architecture", "Contract", "Current-Plan", "EIR", "Geotech", "HRE", "Invoice", "Land-Use",
      "Meeting-Notes", "Other", "Pre-App-Meeting", "Preservation-Alternatives", "Public-Health", "Report",
      "Soils-Report", "Survey", "Traffic-Study", "Wind-Study"),
    "Partners__skills" -> Seq("architect", "electrical-engineer", "landscape-architect", "mechanical-engineer",
        "plumbing-engineer", "project-manager"),
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
    "Team__role" -> Seq("Contributor", "Finance-Contact", "Principal", "Team-Admin", "Team-Lead"),
    "Team__omniclass33" -> Seq("Planning (33-11 00 00)", "Design (33-21 00 00)", "Investigation (33-23 00 00)",
      "Project-Management (33-25 00 00)", "Construction (33-41 00 00)", "Facility-Use (33-55 00 00)",
      "Support (33-81 00 00)")
  )

  val menuItemsList: Seq[Document] = Seq(
    Map("access" -> "*I", "navIcon" -> "assets/images/teenyicons/outline-white/home.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/home.svg",
      "navLabel" -> "Home", "routeUrl" -> "/private/project", "toolTipLabel" -> "Home"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/briefcase-alt.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/briefcase-alt.svg",
      "navLabel" -> "Project", "routeUrl" -> "/private/project/project-details", "toolTipLabel" -> "Project"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/layers.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/layers.svg",
      "navLabel" -> "Phase", "routeUrl" -> "/private/phases", "toolTipLabel" -> "Phase"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/clipboard-tick.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/clipboard-tick.svg",
      "navLabel" -> "Tasks", "routeUrl" -> "/private/tasks", "toolTipLabel" -> "Tasks"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/bag.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/bag.svg",
      "navLabel" -> "Deliverables", "routeUrl" -> "/private/deliverables", "toolTipLabel" -> "Deliverables"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/database.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/database.svg",
      "navLabel" -> "Key Data", "routeUrl" -> "/private/key-data", "toolTipLabel" -> "Key Data"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/text-document-alt.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/text-document-alt.svg",
      "navLabel" -> "Docs", "routeUrl" -> "/private/docs", "toolTipLabel" -> "Docs"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/chat-typing-alt.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/chat-typing-alt.svg",
      "navLabel" -> "RFI", "routeUrl" -> "/private/rfi", "toolTipLabel" -> "RFI"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/bell.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/bell.svg",
      "navLabel" -> "Issues", "routeUrl" -> "/private/issues", "toolTipLabel" -> "Issues"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/mood-smile.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/mood-smile.svg",
      "navLabel" -> "Teams", "routeUrl" -> "/private/teams", "toolTipLabel" -> "Teams"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/exclamation-circle.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/exclamation-circle.svg",
      "navLabel" -> "Risk", "routeUrl" -> "/private/risk", "toolTipLabel" -> "Risk"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/pin.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/pin.svg",
      "navLabel" -> "Zones", "routeUrl" -> "/private/zones", "toolTipLabel" -> "Zones"),
    Map("access" -> "AM", "navIcon" -> "assets/images/teenyicons/outline-white/area-chart.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/area-chart.svg",
      "navLabel" -> "Finance", "routeUrl" -> "/private/finance", "toolTipLabel" -> "Finance"),
    Map("access" -> "AM", "navIcon" -> "assets/images/teenyicons/outline-white/sign.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/sign.svg",
      "navLabel" -> "Procurement", "routeUrl" -> "/private/procurement", "toolTipLabel" -> "Procurement"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/calendar.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/calendar.svg",
      "navLabel" -> "Calendar", "routeUrl" -> "/private/calendar", "toolTipLabel" -> "Calendar"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/book.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/book.svg",
      "navLabel" -> "Reports", "routeUrl" -> "/private/reports", "toolTipLabel" -> "Reports"),
    Map("access" -> "*I", "navIcon" -> "assets/images/teenyicons/outline-white/contact.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/contact.svg",
      "navLabel" -> "Partners", "routeUrl" -> "/private/partners", "toolTipLabel" -> "Partners"),
    Map("access" -> "*I", "navIcon" -> "assets/images/teenyicons/outline-white/wand.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/wand.svg",
      "navLabel" -> "Applications", "routeUrl" -> "/private/applications", "toolTipLabel" -> "Applications")
  )

  def displayedMenuItems(userIsAdmin: Boolean, userIsManager: Boolean = false, starting: Boolean = false):
      Many[Document] = {
    val filteredMenuItems = if (starting) {
      menuItemsList.filter(_.getString("access").contains("I"))
    } else if (userIsAdmin) {
      menuItemsList
    } else if (userIsManager) {
      menuItemsList.filter(_.getString("access").contains("M"))
    } else {
      menuItemsList.filter(_.getString("access").contains("*"))
    }
    filteredMenuItems.map(doc => {
      val withoutAccess = doc.entrySet().asScala.map(es => (es.getKey, es.getValue)).filterNot(_._1 == "access").toMap
      new Document(withoutAccess)
    }).asJava
  }

}
