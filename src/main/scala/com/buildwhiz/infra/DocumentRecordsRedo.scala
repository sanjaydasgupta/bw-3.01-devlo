package com.buildwhiz.infra

import org.bson.Document
import org.bson.types.ObjectId
import BWMongoDB3._
import scala.collection.JavaConversions._

object DocumentRecordsRedo extends App {

  val docRfiRequest: Document = Map("name" -> "RFI-Request", "file_extension" -> ".txt", "description" -> "",
    "content_type" -> "application/octet-stream",
    "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d38"))
  val docRfiResponse: Document = Map("name" -> "RFI-Response", "file_extension" -> ".txt", "description" -> "",
    "content_type" -> "application/octet-stream",
    "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d39"))

  val docCoverSheet: Document = Map("name" -> "Cover Sheet", "file_extension" -> ".txt", "description" -> "",
    "content_type" -> "application/octet-stream", "_id" -> new ObjectId("56f124dfd5d8ad25b1325b42"))
  val docSitePlan: Document = Map("name" -> "Site Plan", "file_extension" -> ".txt", "description" -> "",
    "content_type" -> "application/octet-stream", "_id" -> new ObjectId("56f124dfd5d8ad25b1325b3a"))
  val docBasementFloorPlan: Document = Map("name" -> "Basement Floor Plan", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream", "_id" -> new ObjectId("56f124dfd5d8ad25b1325b3b"))


  val docOwnersProjectReport: Document = Map("name" -> "Owners Project Report", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream", "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d2c"))
  val docDemolitionPermit: Document = Map("name" -> "Demolition Permit", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream", "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d2d"))
  val docDemolitionComplete: Document = Map("name" -> "Demolition Complete Report", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream", "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d2e"))
  val docDemolitionManagersReview: Document = Map("name" -> "Demolition Managers Review Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream",
    "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d2f"))
  val docDemolitionCityReview: Document = Map("name" -> "Demolition Citys Review Report", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream", "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d30"))
  val docExcavationStakingComplete: Document = Map("name" -> "Excavation-Staking-Complete-Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream",
    "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d31"))
  val docExcavationComplete: Document = Map("name" -> "Excavation-Complete-Report", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream", "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d32"))
  val docExcavationCityReview: Document = Map("name" -> "Excavation-Citys-Review-Report", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream", "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d33"))
  val docExcavationRccContractorsReview: Document = Map("name" -> "Excavation-RCC-Contractors-Review-Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream",
    "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d34"))
  val docBasementConstructionComplete: Document = Map("name" -> "Basement-Construction-Complete-Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream",
    "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d35"))
  val docBasementConstructionCityReview: Document = Map("name" -> "Basement-Construction-Citys-Review-Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream",
    "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d36"))
  val docBasementConstructionManagersReview: Document = Map("name" -> "Basement-Construction-Managers-Review-Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream",
    "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d37"))

  val allDocuments = Seq(docOwnersProjectReport, docDemolitionPermit, docDemolitionComplete, docDemolitionManagersReview,
    docDemolitionCityReview, docExcavationStakingComplete, docExcavationComplete, docExcavationCityReview,
    docExcavationRccContractorsReview, docBasementConstructionComplete, docBasementConstructionCityReview,
    docBasementConstructionManagersReview, docRfiRequest, docRfiResponse, docSitePlan, docCoverSheet, docBasementFloorPlan)
  BWMongoDB3.document_master.drop()
  BWMongoDB3.document_master.insertMany(allDocuments)
}
