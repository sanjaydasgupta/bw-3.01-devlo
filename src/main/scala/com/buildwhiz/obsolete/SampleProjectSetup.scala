package com.buildwhiz.obsolete

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

object SampleProjectSetup extends App {
  BWLogger.log(getClass.getName, "main()", "ENTRY")

  //DatabaseInitializePersons.main(Array.empty[String])

  val docOwnersProjectReport: Document = Map("name" -> "Owners-Project-Report", "file_extension" -> ".txt",
      "description" -> "", "content_type" -> "application/octet-stream")
  val docDemolitionPermit: Document = Map("name" -> "Demolition-Permit", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream")
  val docDemolitionComplete: Document = Map("name" -> "Demolition-Complete-Report", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream")
  val docDemolitionManagersReview: Document = Map("name" -> "Demolition-Managers-Review-Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream")
  val docDemolitionCityReview: Document = Map("name" -> "Demolition-Citys-Review-Report", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream")
  val docExcavationStakingComplete: Document = Map("name" -> "Excavation-Staking-Complete-Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream")
  val docExcavationComplete: Document = Map("name" -> "Excavation-Complete-Report", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream")
  val docExcavationCityReview: Document = Map("name" -> "Excavation-Citys-Review-Report", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream")
  val docExcavationRccContractorsReview: Document = Map("name" -> "Excavation-RCC-Contractors-Review-Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream")
  val docBasementConstructionComplete: Document = Map("name" -> "Basement-Construction-Complete-Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream")
  val docBasementConstructionCityReview: Document = Map("name" -> "Basement-Construction-Citys-Review-Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream")
  val docBasementConstructionManagersReview: Document = Map("name" -> "Basement-Construction-Managers-Review-Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream")

  val allDocuments = Seq(docOwnersProjectReport, docDemolitionPermit, docDemolitionComplete, docDemolitionManagersReview,
    docDemolitionCityReview, docExcavationStakingComplete, docExcavationComplete, docExcavationCityReview,
    docExcavationRccContractorsReview, docBasementConstructionComplete, docBasementConstructionCityReview,
    docBasementConstructionManagersReview)
  BWMongoDB3.document_master.drop()
  BWMongoDB3.document_master.insertMany(allDocuments.asJava)

  val orgPrabhas: Document = Map("name" -> "Prabhas Kejriwal Co",
    "timestamps" -> Map("created" -> System.currentTimeMillis))
  val orgDavid: Document = Map("name" -> "David Solnick Architect",
    "timestamps" -> Map("created" -> System.currentTimeMillis))
  val orgDemolitions: Document = Map("name" -> "Demolition Experts, Inc",
    "timestamps" -> Map("created" -> System.currentTimeMillis))
  val orgConcrete: Document = Map("name" -> "Concrete Build, Inc",
    "timestamps" -> Map("created" -> System.currentTimeMillis))
  val orgExcavations: Document = Map("name" -> "Excavations, Inc",
    "timestamps" -> Map("created" -> System.currentTimeMillis))
  val orgSurveys: Document = Map("name" -> "Survey Experts, Inc",
    "timestamps" -> Map("created" -> System.currentTimeMillis))
  val orgBuildwhiz: Document = Map("name" -> "BuildWhiz",
    "timestamps" -> Map("created" -> System.currentTimeMillis))

  val allOrganizations = Seq(orgDavid, orgPrabhas, orgDemolitions,
    orgConcrete, orgExcavations, orgSurveys, orgBuildwhiz)
  BWMongoDB3.organizations.drop()
  BWMongoDB3.organizations.insertMany(allOrganizations.asJava)

  val personCaroline: Document = BWMongoDB3.persons.find(Map("first_name" -> "Caroline", "last_name" -> "Chen")).asScala.head

  val personDavid: Document = BWMongoDB3.persons.find(Map("first_name" -> "David", "last_name" -> "Solnick")).asScala.head

  val personPrabhas: Document = BWMongoDB3.persons.find(Map("first_name" -> "Prabhas", "last_name" -> "Kejriwal")).asScala.head

  val personSanjay: Document = BWMongoDB3.persons.find(Map("first_name" -> "Sanjay", "last_name" -> "Dasgupta")).asScala.head

  val personSurveys: Document = BWMongoDB3.persons.find(Map("first_name" -> "Vergel", "last_name" -> "Galura")).asScala.head

  val personExcavations: Document = BWMongoDB3.persons.find(Map("first_name" -> "Fred", "last_name" -> "Reynolds")).asScala.head

  val personConcrete: Document = BWMongoDB3.persons.find(Map("first_name" -> "Des", "last_name" -> "Nolan")).asScala.head

  val personDemolitions: Document = BWMongoDB3.persons.find(Map("first_name" -> "Scott", "last_name" -> "Rehn")).asScala.head

  BWMongoDB3.organizations.updateOne(orgDemolitions,
    Map("$set" -> Map("principal_person_id" -> personDemolitions.asScala("_id"))))
  BWMongoDB3.organizations.updateOne(orgConcrete,
    Map("$set" -> Map("principal_person_id" -> personConcrete.asScala("_id"))))
  BWMongoDB3.organizations.updateOne(orgExcavations,
    Map("$set" -> Map("principal_person_id" -> personExcavations.asScala("_id"))))
  BWMongoDB3.organizations.updateOne(orgDavid,
    Map("$set" -> Map("principal_person_id" -> personDavid.asScala("_id"))))
  BWMongoDB3.organizations.updateOne(orgPrabhas,
    Map("$set" -> Map("principal_person_id" -> personPrabhas.asScala("_id"))))
  BWMongoDB3.organizations.updateOne(orgBuildwhiz,
    Map("$set" -> Map("principal_person_id" -> personPrabhas.asScala("_id"))))
  BWMongoDB3.organizations.updateOne(orgSurveys,
    Map("$set" -> Map("principal_person_id" -> personSurveys.asScala("_id"))))

  //BWMongoDB3.projects.drop()
  val projectOne: Document = Map("name" -> "Project-One", "admin_person_id" -> personPrabhas.asScala("_id"),
    "status" -> "defined", "phase_ids" -> Seq.empty[ObjectId])
  BWMongoDB3.projects.insertOne(projectOne)

  //BWMongoDB3.phases.drop()
  val phaseConstruction: Document = Map("name" -> "Construction", "bpmn_name" -> "Phase-Construction",
    "admin_person_id" -> personPrabhas.asScala("_id"), "status" -> "defined", "activity_ids" -> Seq.empty[ObjectId],
    "timers" -> Seq.empty[Document], "variables" -> Seq.empty[Document])
  val phaseSimple: Document = Map("name" -> "Simple", "bpmn_name" -> "Phase-Simple",
    "admin_person_id" -> personPrabhas.asScala("_id"), "status" -> "defined", "activity_ids" -> Seq.empty[ObjectId],
    "timers" -> Seq.empty[Document], "variables" -> Seq.empty[Document])
  val phases = Seq(phaseConstruction, phaseSimple)
  BWMongoDB3.phases.insertMany(phases.asJava)
  phases.map(_.getObjectId("_id")).foreach(id => {
    //println(id)
    BWMongoDB3.projects.updateOne(Map("_id" -> projectOne.asScala("_id")),
    Map("$addToSet" -> Map("phase_ids" -> id)))})

  //BWMongoDB3.activities.drop()
  val activityDemolition: Document = Map("name" -> "Demolition", "status" -> "defined",
    "actions" -> Seq(
      Map("name" -> "Demolition-Permit", "type" -> "prerequisite", "assignee_person_id" -> personPrabhas.asScala("_id"),
        "status" -> "defined", "inbox" -> Seq.empty[ObjectId], "outbox" -> Seq(docDemolitionPermit.asScala("_id")),
        "duration" -> "1:12:00"),
      Map("name" -> "Demolition", "type" -> "main", "assignee_person_id" -> personDemolitions.asScala("_id"),
        "status" -> "defined", "inbox" -> Seq.empty[Document], "outbox" -> Seq(docDemolitionComplete.asScala("_id")),
        "duration" -> "1:12:00"),
      Map("name" -> "Managers-Review", "type" -> "review", "assignee_person_id" -> personPrabhas.asScala("_id"),
        "status" -> "defined", "inbox" -> Seq(docDemolitionComplete.asScala("_id")),
        "outbox" -> Seq(docDemolitionManagersReview.asScala("_id")), "duration" -> "1:12:00"),
      Map("name" -> "Citys-Review", "type" -> "review", "assignee_person_id" -> personPrabhas.asScala("_id"),
        "status" -> "defined", "inbox" -> Seq(docDemolitionComplete.asScala("_id")),
        "outbox" -> Seq(docDemolitionCityReview.asScala("_id")), "duration" -> "1:12:00")))
  val activityExcavation: Document = Map("name" -> "Excavation", "status" -> "defined",
    "actions" -> Seq(
      Map("name" -> "Staking", "type" -> "prerequisite", "assignee_person_id" -> personSurveys.asScala("_id"),
        "status" -> "defined", "inbox" -> Seq.empty[Document], "outbox" -> Seq(docExcavationStakingComplete.asScala("_id")),
        "duration" -> "1:12:00"),
      Map("name" -> "Excavation", "type" -> "main", "assignee_person_id" -> personExcavations.asScala("_id"),
        "status" -> "defined", "inbox" -> Seq(docExcavationStakingComplete.asScala("_id")),
        "outbox" -> Seq(docExcavationComplete.asScala("_id")), "duration" -> "1:12:00"),
      Map("name" -> "Citys-Review", "type" -> "review", "assignee_person_id" -> personPrabhas.asScala("_id"),
        "status" -> "defined", "inbox" -> Seq(docExcavationComplete.asScala("_id")),
        "outbox" -> Seq(docExcavationCityReview.asScala("_id")), "duration" -> "1:12:00"),
      Map("name" -> "RCC-Contractors-Review", "type" -> "review", "assignee_person_id" -> personConcrete.asScala("_id"),
        "status" -> "defined", "inbox" -> Seq(docExcavationComplete.asScala("_id")),
        "outbox" -> Seq(docExcavationRccContractorsReview.asScala("_id")), "duration" -> "1:12:00")))
  val activityBasementConstruction: Document = Map("name" -> "BasementConstruction", "status" -> "defined",
    "actions" -> Seq(
      Map("name" -> "Basement-Construction", "type" -> "main", "assignee_person_id" -> personConcrete.asScala("_id"),
        "status" -> "defined", "inbox" -> Seq.empty[Document], "outbox" -> Seq(docBasementConstructionComplete.asScala("_id")),
        "duration" -> "1:12:00"),
      Map("name" -> "Citys-Review", "type" -> "review", "assignee_person_id" -> personPrabhas.asScala("_id"),
        "status" -> "defined", "inbox" -> Seq(docBasementConstructionComplete.asScala("_id")),
        "outbox" -> Seq(docBasementConstructionCityReview.asScala("_id")), "duration" -> "1:12:00"),
      Map("name" -> "Phase-Managers-Review", "type" -> "review", "assignee_person_id" -> personPrabhas.asScala("_id"),
        "status" -> "defined", "inbox" -> Seq(docBasementConstructionComplete.asScala("_id")),
        "outbox" -> Seq(docBasementConstructionManagersReview.asScala("_id")), "duration" -> "1:12:00")))
  val activitySole: Document = Map("name" -> "AnActivity", "status" -> "defined",
    "actions" -> Seq(
      Map("name" -> "Sole-Action", "type" -> "main", "assignee_person_id" -> personPrabhas.asScala("_id"),
        "status" -> "defined", "inbox" -> Seq.empty[Document],
        "outbox" -> Seq(docBasementConstructionComplete.asScala("_id")), "duration" -> "1:12:00")))
  val theActivities = Seq(activityDemolition, activityExcavation, activityBasementConstruction,
    activitySole)
  BWMongoDB3.activities.insertMany(theActivities.asJava)

  val constructionActivities = Seq(activityDemolition, activityExcavation, activityBasementConstruction)
  constructionActivities.foreach(activity => {BWMongoDB3.phases.updateOne(Map("_id" -> phaseConstruction.asScala("_id")),
    Map("$addToSet" -> Map("activity_ids" -> activity.asScala("_id"))))})
  BWMongoDB3.phases.updateOne(Map("_id" -> phaseSimple.asScala("_id")),
    Map("$addToSet" -> Map("activity_ids" -> activitySole.asScala("_id"))))

  val allActions: Seq[DynDoc] = theActivities.flatMap(a => a.asScala("actions").asInstanceOf[DocumentList])
  for (action <- allActions) {
    BWMongoDB3.persons.updateOne(Map("_id" -> action.assignee_person_id),
      Map("$addToSet" -> Map("project_ids" -> projectOne.asScala("_id"))))
  }

  BWLogger.log(getClass.getName, "main()", "EXIT")
}
