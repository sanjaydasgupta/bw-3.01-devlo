package com.buildwhiz

import org.bson.Document
import com.buildwhiz.infra.DynDoc._

import scala.collection.JavaConverters._

package object baf3 {

  val masterData = Map(
    "DeliverableInfoSet__deliverable_type" -> Seq("Data", "Document", "Equipment", "Material", "Work"),
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
    "PartnerList__serving_area" -> Seq("USA", "Europe", "India", "California:USA")
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
      "navLabel" -> "Phases", "routeUrl" -> "/private/phases", "toolTipLabel" -> "All Phases"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/clipboard-tick.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/clipboard-tick.svg",
      "navLabel" -> "Tasks", "routeUrl" -> "/private/tasks", "toolTipLabel" -> "All Tasks"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/bag.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/bag.svg",
      "navLabel" -> "Deliverables", "routeUrl" -> "/private/deliverables", "toolTipLabel" -> "All Deliverables"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/database.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/database.svg",
      "navLabel" -> "Key Data", "routeUrl" -> "/private/key-data", "toolTipLabel" -> "Key Data"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/calendar.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/calendar.svg",
      "navLabel" -> "Calendar", "routeUrl" -> "/private/calendar", "toolTipLabel" -> "Calendar"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/book.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/book.svg",
      "navLabel" -> "Reports", "routeUrl" -> "/private/reports", "toolTipLabel" -> "All Reports"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/text-document-alt.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/text-document-alt.svg",
      "navLabel" -> "Docs", "routeUrl" -> "/private/docs", "toolTipLabel" -> "All Docs"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/chat-typing-alt.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/chat-typing-alt.svg",
      "navLabel" -> "RFI", "routeUrl" -> "/private/rfi", "toolTipLabel" -> "All RFI"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/bell.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/bell.svg",
      "navLabel" -> "Issues", "routeUrl" -> "/private/issues", "toolTipLabel" -> "All Issues"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/mood-smile.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/mood-smile.svg",
      "navLabel" -> "Teams", "routeUrl" -> "/private/teams", "toolTipLabel" -> "All Teams"),
    Map("access" -> "*", "navIcon" -> "assets/images/teenyicons/outline-white/exclamation-circle.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/exclamation-circle.svg",
      "navLabel" -> "Risk", "routeUrl" -> "/private/risk", "toolTipLabel" -> "All Risk"),
    Map("access" -> "AM", "navIcon" -> "assets/images/teenyicons/outline-white/area-chart.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/area-chart.svg",
      "navLabel" -> "Finance", "routeUrl" -> "/private/finance", "toolTipLabel" -> "All Finance"),
    Map("access" -> "AM", "navIcon" -> "assets/images/teenyicons/outline-white/sign.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/sign.svg",
      "navLabel" -> "Procurement", "routeUrl" -> "/private/procurement", "toolTipLabel" -> "All Procurement"),
    Map("access" -> "*I", "navIcon" -> "assets/images/teenyicons/outline-white/contact.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/contact.svg",
      "navLabel" -> "Partners", "routeUrl" -> "/private/partners", "toolTipLabel" -> "All Partners"),
    Map("access" -> "*I", "navIcon" -> "assets/images/teenyicons/outline-white/wand.svg",
      "navIconActive" -> "assets/images/teenyicons/solid-white/wand.svg",
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
