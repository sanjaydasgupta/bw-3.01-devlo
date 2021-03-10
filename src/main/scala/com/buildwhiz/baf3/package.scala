package com.buildwhiz

import org.bson.Document
import com.buildwhiz.infra.DynDoc._

import scala.collection.JavaConverters._

package object baf3 {

  val masterData = Map(
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
    "PhaseInfo__task_info__scope" -> Seq("all", "current", "future", "past")
  )

  val menuItemsList: Seq[Document] = Seq(
    Map("access" -> "*I", "navIcon" -> "assets/images/teenyions/outline-white/briefcase-alt.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/briefcase-alt.svg",
      "navLabel" -> "All Projects", "routeUrl" -> "/private/project/dashboard", "toolTipLabel" -> "All Projects"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyions/outline-white/layers.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/layers.svg",
      "navLabel" -> "Phases", "routeUrl" -> "/private/phases/dashboard", "toolTipLabel" -> "All Phases"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyions/outline-white/clipboard-tick.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/clipboard-tick.svg",
      "navLabel" -> "Tasks", "routeUrl" -> "/private/tasks", "toolTipLabel" -> "All Tasks"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyions/outline-white/calendar.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/calendar.svg",
      "navLabel" -> "Calendar", "routeUrl" -> "/private/calendar", "toolTipLabel" -> "Calendar"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyions/outline-white/book.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/book.svg",
      "navLabel" -> "Reports", "routeUrl" -> "/private/reports", "toolTipLabel" -> "All Reports"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyions/outline-white/text-document-alt.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/text-document-alt.svg",
      "navLabel" -> "Docs", "routeUrl" -> "/private/docs", "toolTipLabel" -> "All Docs"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyions/outline-white/chat-typing-alt.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/chat-typing-alt.svg",
      "navLabel" -> "RFI", "routeUrl" -> "/private/rfi", "toolTipLabel" -> "All RFI"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyions/outline-white/bell.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/bell.svg",
      "navLabel" -> "Issues", "routeUrl" -> "/private/issues", "toolTipLabel" -> "All Issues"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyions/outline-white/mood-smile.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/mood-smile.svg",
      "navLabel" -> "Teams", "routeUrl" -> "/private/teams", "toolTipLabel" -> "All Teams"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyions/outline-white/exclamation-circle.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/exclamation-circle.svg",
      "navLabel" -> "Risk", "routeUrl" -> "/private/risk", "toolTipLabel" -> "All Risk"),
    Map("access" -> "AM", "navIcon" -> "assets/images/teenyions/outline-white/area-chart.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/area-chart.svg",
      "navLabel" -> "Finance", "routeUrl" -> "/private/finance", "toolTipLabel" -> "All Finance"),
    Map("access" -> "AM", "navIcon" -> "assets/images/teenyions/outline-white/sign.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/sign.svg",
      "navLabel" -> "Procurement", "routeUrl" -> "/private/procurement", "toolTipLabel" -> "All Procurement"),
    Map("access" -> "*I", "navIcon" -> "assets/images/teenyions/outline-white/contact.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/contact.svg",
      "navLabel" -> "Partners", "routeUrl" -> "/private/partners", "toolTipLabel" -> "All Partners"),
    Map("access" -> "*I", "navIcon" -> "assets/images/teenyions/outline-white/wand.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/wand.svg",
      "navLabel" -> "Applications", "routeUrl" -> "/private/applications", "toolTipLabel" -> "All Applications")
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
