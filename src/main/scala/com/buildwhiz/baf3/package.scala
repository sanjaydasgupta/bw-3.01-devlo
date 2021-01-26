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
    Map("access" -> "*", "navIcon" -> "assets/images/teenyions/outline-white/briefcase-alt.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/briefcase-alt.svg",
      "navLabel" -> "Project", "routeUrl" -> "/private/project/dashboard", "toolTipLabel" -> "All Projects"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyions/outline-white/layers.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/layers.svg",
      "navLabel" -> "Phases", "routeUrl" -> "/private/phases/dashboard", "toolTipLabel" -> "All Phases"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyions/outline-white/clipboard-tick.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/clipboard-tick.svg",
      "navLabel" -> "Tasks", "routeUrl" -> "/private/tasks", "toolTipLabel" -> "All Tasks"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyions/outline-white/calendar.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/calendar.svg",
      "navLabel" -> "Calendar", "routeUrl" -> "/private/phases/calendar", "toolTipLabel" -> "Calendar"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyions/outline-white/text-document-alt.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/text-document-alt.svg",
      "navLabel" -> "Docs", "routeUrl" -> "/private/phases/docs", "toolTipLabel" -> "All Docs"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyions/outline-white/chat-typing-alt.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/chat-typing-alt.svg",
      "navLabel" -> "RFI", "routeUrl" -> "/private/phases/rfi", "toolTipLabel" -> "All RFI"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyions/outline-white/bell.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/bell.svg",
      "navLabel" -> "Issues", "routeUrl" -> "/private/phases/issues", "toolTipLabel" -> "All Issues"),
    Map("access" -> "M", "navIcon" -> "assets/images/teenyions/outline-white/mood-smile.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/mood-smile.svg",
      "navLabel" -> "Teams", "routeUrl" -> "/private/phases/teams", "toolTipLabel" -> "All Teams"),
    Map("access" -> "M", "navIcon" -> "assets/images/teenyions/outline-white/exclamation-circle.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/exclamation-circle.svg",
      "navLabel" -> "Risk", "routeUrl" -> "/private/phases/risk", "toolTipLabel" -> "All Risk"),
    Map("access" -> "A", "navIcon" -> "assets/images/teenyions/outline-white/area-chart.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/area-chart.svg",
      "navLabel" -> "Finance", "routeUrl" -> "/private/phases/finance", "toolTipLabel" -> "All Finance"),
    Map("access" -> "A", "navIcon" -> "assets/images/teenyions/outline-white/sign.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/sign.svg",
      "navLabel" -> "Procurement", "routeUrl" -> "/private/phases/procurement", "toolTipLabel" -> "All Procurement"),
    Map("access" -> "A", "navIcon" -> "assets/images/teenyions/outline-white/contact.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/contact.svg",
      "navLabel" -> "Partners", "routeUrl" -> "/private/phases/partners", "toolTipLabel" -> "All Partners"),
    Map("access" -> "A", "navIcon" -> "assets/images/teenyions/outline-white/wand.svg",
      "navIconActive" -> "assets/images/teenyions/solid-white/wand.svg",
      "navLabel" -> "Applications", "routeUrl" -> "/private/phases/applications", "toolTipLabel" -> "All Applications")
  )

  def displayedMenuItems(userIsAdmin: Boolean, userIsManager: Boolean = false): Many[Document] = {
    (if (userIsAdmin) {
      menuItemsList
    } else if (userIsManager) {
      menuItemsList.filter(_.getString("access").matches("[*M]"))
    } else {
      menuItemsList.filter(_.getString("access") == "*")
    }).map(doc => {doc.remove("access"); doc}).asJava
  }

}
