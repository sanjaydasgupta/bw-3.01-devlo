package com.buildwhiz.infra

import org.bson.Document
import org.bson.types.ObjectId
import BWMongoDB3._
import scala.collection.JavaConversions._

object DocumentRecordsRedo extends App {

  val drawings: Seq[String] = Seq(
    "57207549d5d8ad331d2ea699#1A#COVER SHEET#Project parameters, vicinity, sheet index, design team, general information#Architecture#C",
    "57207549d5d8ad331d2ea69a#1B#SITE PLAN##Architecture#C",
    "57207549d5d8ad331d2ea69b#2A#BASEMENT FLOOR PLAN##Architecture#C",
    "57207549d5d8ad331d2ea69c#2B#LIGHTING PLAN/ BASEMENT##Architecture#C",
    "57207549d5d8ad331d2ea69d#2C#PLUMBING PLAN/ BASEMENT##Architecture#P",
    "57207549d5d8ad331d2ea69e#3A#GROUND FLOOR PLAN/ FRONT BUILDING##Architecture#C",
    "57207549d5d8ad331d2ea69f#3B#2ND FLOOR PLAN/ FRONT BUILDING##Architecture#C",
    "57207549d5d8ad331d2ea6a0#3C#3RD FLOOR PLAN/ FRONT BUILDING##Architecture#C",
    "57207549d5d8ad331d2ea6a1#3D#CLERESTORY & ROOF PLAN/ FRONT BUILDING##Architecture#C",
    "57207549d5d8ad331d2ea6a2#3E#WINDOW & DOOR SCHEDULE/ FRONT BUILDING##Architecture#",
    "57207549d5d8ad331d2ea6a3#3F#ELECTRICAL PLAN- GROUND FLOOR/ FRONT BUILDING##Architecture#",
    "57207549d5d8ad331d2ea6a4#3G#ELECTRICAL PLAN- 2ND FLOOR/ FRONT BUILDING##Architecture#",
    "57207549d5d8ad331d2ea6a5#3H#ELECTRICAL PLAN- 3RD FLOOR/ FRONT BUILDING##Architecture#",
    "57207549d5d8ad331d2ea6a6#3I#SECTIONS/ FRONT BUILDING##Architecture#C",
    "57207549d5d8ad331d2ea6a7#3J#SECTIONS/ FRONT BUILDING##Architecture#",
    "57207549d5d8ad331d2ea6a8#3K#ELEVATIONS/ FRONT BUILDING##Architecture#C",
    "57207549d5d8ad331d2ea6a9#3L#ELEVATIONS/ FRONT BUILDING##Architecture#C",
    "57207549d5d8ad331d2ea6aa#4A#FLOOR PLANS/ REAR BUILDING##Architecture#C",
    "57207549d5d8ad331d2ea6ab#4B#ROOF PLAN/ REAR BUILDING##Architecture#C",
    "57207549d5d8ad331d2ea6ac#4C#WINDOW & DOOR SCHEDULE/ REAR BUILDING##Architecture#",
    "57209fa5d5d8ad4034c11b94#4D#ELECTRICAL PLAN/ REAR BUILDING##Architecture#",
    "57209fa5d5d8ad4034c11b95#4E#SECTIONS/ REAR BUILDING##Architecture#C",
    "57209fa5d5d8ad4034c11b96#4F#SECTIONS/ REAR BUILDING##Architecture#",
    "57209fa5d5d8ad4034c11b97#4G#ELEVATIONS/ REAR BUILDING##Architecture#C",
    "57209fa5d5d8ad4034c11b98#5A#3D PERSPECTIVES##Architecture#C",
    "57209fa5d5d8ad4034c11b99#5B#3D PERSPECTIVES##Architecture#C",
    "57209fa5d5d8ad4034c11b9a#6A#DETAILS##Architecture#",
    "57209fa5d5d8ad4034c11b9c#6B#DETAILS##Architecture#",
    "57209fa5d5d8ad4034c11b9d#C1#BOUNDARY AND TOPOGRAPHIC SURVEY##Civil#V",
    "57209fa5d5d8ad4034c11b9e#C2#GRADING & DRAINAGE PLAN WITH STORMWATER TREATMENT##Civil#V",
    "57209fa5d5d8ad4034c11b9f#C3#ROUGH GRADING PLAN WITH EROSION & SEDIMENTATION CONTROL MEASURES AND TREE PROTECTION##Civil#V",
    "57209fa5d5d8ad4034c11ba0#C4#CIVIL DETAILS AND NOTES##Civil#V",
    "57209fa5d5d8ad4034c11ba1#C5#STORMWATER TREATMENT PLAN##Civil#V",
    "57209fa5d5d8ad4034c11ba2#C6#CONSTRUCTION BEST MANAGEMENT PRACTICES PLAN##Civil#V",
    "57209fa5d5d8ad4034c11ba3#S0.1#GENERAL STRUCTURAL NOTES##Structural#B",
    "57209fa5d5d8ad4034c11ba4#S0.2#GENERAL STRUCTURAL NOTES##Structural#B",
    "57209fa5d5d8ad4034c11ba5#S0.3#STANDARD DETAILS##Structural#B",
    "57209fa5d5d8ad4034c11ba6#S0.4#STANDARD DETAILS##Structural#B",
    "57209fa5d5d8ad4034c11ba7#S0.5#STANDARD DETAILS##Structural#B",
    "5720a083d5d8ad407e2af0e7#S1.0#GARAGE FOUNDATION PLAN##Structural#B",
    "5720a083d5d8ad407e2af0e8#S1.1#DECK MILD REINFORCEMENT##Structural#",
    "5720a083d5d8ad407e2af0e9#S1.2#DECK MILD REINFORCEMENT##Structural#B",
    "5720a083d5d8ad407e2af0ea#S1.3#DECK PRESTRESS REINFORCEMENT##Structural#B",
    "5720a083d5d8ad407e2af0eb#S1.4#DECK PRESTRESS REINFORCEMENT##Structural#B",
    "5720a083d5d8ad407e2af0ec#S1.5#FOUNDATION AT GRADE##Structural#B",
    "5720a083d5d8ad407e2af0ed#S2.1#FRONT BUILDING SHEAR WALLS LEVEL 1##Structural#B",
    "5720a083d5d8ad407e2af0ee#S2.2#FRONT BUILDING SECOND FLOOR FRAMING##Structural#",
    "5720a083d5d8ad407e2af0ef#S2.3#FRONT BUILDING SHEAR WALLS LEVEL 2##Structural#",
    "5720a083d5d8ad407e2af0f0#S2.4#FRONT BUILDING THIRD FLOOR FRAMING##Structural#",
    "5720a083d5d8ad407e2af0f1#S2.5#FRONT BUILDING SHEAR WALLS LEVEL 3##Structural#",
    "5720a083d5d8ad407e2af0f2#S2.6#FRONT BUILDING LOW ROOF FRAMING PLAN##Structural#",
    "5720a083d5d8ad407e2af0f3#S2.7#FRONT BUILDING UPPER ROOF FRAMING##Structural#",
    "5720a083d5d8ad407e2af0f4#S3.1#REAR BUILDING SHEAR WALLS##Structural#",
    "5720a083d5d8ad407e2af0f5#S3.2#REAR BUILDING SECOND FLOOR FRAMING##Structural#",
    "5720a083d5d8ad407e2af0f6#S3.3#REAR BUILDING ROOF FRAMING##Structural#",
    "5720a083d5d8ad407e2af0f7#S4.1#FOUNDATION DETAILS##Structural#",
    "5720a083d5d8ad407e2af0f8#S5.1#FLOOR AND LOW ROOF FRAMING DETAILS##Structural#",
    "5720a083d5d8ad407e2af0f9#S5.2#FLOOR AND LOW ROOF FRAMING DETAILS##Structural#",
    "5720a083d5d8ad407e2af0fa#S5.3#FLOOR AND LOW ROOF FRAMING DETAILS##Structural#",
    "5720a147d5d8ad40bdcd7a81#S6.1#ROOF FRAMING DETAILS##Structural#",
    "5720a147d5d8ad40bdcd7a82#M0.1#MECHANICAL SCHEDULES##Mechanical#M",
    "5720a147d5d8ad40bdcd7a83#M0.2#MECHANICAL SPECIFICATIONS##Mechanical#M",
    "5720a147d5d8ad40bdcd7a84#M1.0#MECHANICAL PARKING GARAGE PLAN##Mechanical#M",
    "5720a147d5d8ad40bdcd7a85#M6.0#MECHANICAL DETAILS##Mechanical#M",
    "5720a147d5d8ad40bdcd7a86#M6.1#MECHANICAL DETAILS##Mechanical#M",
    "5720a147d5d8ad40bdcd7a87#L1.1#PLANTING PLAN##Landscaping#",
    "5720a147d5d8ad40bdcd7a88#L2.1#IRRIGATION PLAN##Landscaping#",
    "5720a147d5d8ad40bdcd7a89#L3.1#LANDSCAPE DETAILS##Landscaping#",
    "5720a147d5d8ad40bdcd7a8a#L4.1#LANDSCAPE SPECIFICATIONS##Landscaping#",
    "5720a147d5d8ad40bdcd7a8b#L-TR#OFF-SITE TREE REPLACEMENT PLAN##Landscaping#",
    "5720a147d5d8ad40bdcd7a8c#T24.1#ENERGY COMPLIANCE/ BASEMENT##Energy#",
    "5720a147d5d8ad40bdcd7a8d#T24.2#ENERGY COMPLIANCE##Energy#",
    "5720a147d5d8ad40bdcd7a8e#T24.3#ENERGY COMPLIANCE##Energy#",
    "5720a147d5d8ad40bdcd7a8f#T24.4#ENERGY COMPLIANCE##Energy#",
    "5720a147d5d8ad40bdcd7a90#P#PHOTOMETRICS##Energy#",
    "5720a147d5d8ad40bdcd7a91#GB-1#GREEN BUILDING##Green Building#P",
    "5720a147d5d8ad40bdcd7a92#T1#TREE PROTECTION##Tree, Arborist#C",
    "5720a147d5d8ad40bdcd7a93#T2#TREE PROTECTION/ ARBORIST’S REPORT##Tree, Arborist#C",
    "5720a147d5d8ad40bdcd7a94#T&U#TREE DISPOSITION PLAN & SITE UTILITIES##Tree, Arborist#C",
    "5720a239d5d8ad41061c3a90#Z1#CONDITIONS OF APPROVAL###C",
    "5720a239d5d8ad41061c3a91#Z2#CONDITIONS OF APPROVAL###C",
    "5720a239d5d8ad41061c3a92#Z3#LOGISTIC PLAN###P"
  )

  def processDrawings(): Seq[Document] = {
    def drawing2document(drawing: String): Document = {
      val fields = Seq("_id", "sheet", "name", "description", "keywords", "author").
        zip(drawing.replaceAll("\\s", " ").split("#")).toMap
      val drawingDoc: Document = Seq("_id" -> new ObjectId(fields("_id")),
        "name" -> s"${fields("name")} (${fields("sheet")})", "description" -> fields("description"),
        "keywords" -> fields("keywords"), "author" -> fields.getOrElse("author", null),
        "document_type" -> "drawing", "file_extension" -> ".pdf").
        filterNot(p => p._1 == "author" && p._2 == null).toMap
      drawingDoc
    }
    drawings.map(drawing2document)
  }

/*
"", "", "", "5720a239d5d8ad41061c3a93", "5720a239d5d8ad41061c3a94", "5720a239d5d8ad41061c3a95",
"5720a239d5d8ad41061c3a96", "5720a239d5d8ad41061c3a97", "5720a239d5d8ad41061c3a98", "5720a239d5d8ad41061c3a99",
"5720a239d5d8ad41061c3a9a", "5720a239d5d8ad41061c3a9b", "5720a239d5d8ad41061c3a9c", "5720a239d5d8ad41061c3a9d",
"5720a239d5d8ad41061c3a9e", "5720a239d5d8ad41061c3a9f", "5720a239d5d8ad41061c3aa0", "5720a239d5d8ad41061c3aa1",
"5720a239d5d8ad41061c3aa2", "5720a239d5d8ad41061c3aa3"
*/

  val docRfiRequest: Document = Map("name" -> "RFI-Request", "file_extension" -> ".txt", "description" -> "",
    "content_type" -> "application/octet-stream", "document_type" -> "text",
    "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d38"))
  val docRfiResponse: Document = Map("name" -> "RFI-Response", "file_extension" -> ".txt", "description" -> "",
    "content_type" -> "application/octet-stream", "document_type" -> "text",
    "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d39"))

//  val docCoverSheet: Document = Map("name" -> "Cover Sheet", "file_extension" -> ".txt", "description" -> "",
//    "content_type" -> "application/octet-stream", "_id" -> new ObjectId("56f124dfd5d8ad25b1325b42"))
//  val docSitePlan: Document = Map("name" -> "Site Plan", "file_extension" -> ".txt", "description" -> "",
//    "content_type" -> "application/octet-stream", "_id" -> new ObjectId("56f124dfd5d8ad25b1325b3a"))
//  val docBasementFloorPlan: Document = Map("name" -> "Basement Floor Plan", "file_extension" -> ".txt",
//    "description" -> "", "content_type" -> "application/octet-stream", "_id" -> new ObjectId("56f124dfd5d8ad25b1325b3b"))
//
//
  val docOwnersProjectReport: Document = Map("name" -> "Owners Project Report", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream", "document_type" -> "text",
    "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d2c"))
  val docDemolitionPermit: Document = Map("name" -> "Demolition Permit", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream", "document_type" -> "text",
    "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d2d"))
  val docDemolitionComplete: Document = Map("name" -> "Demolition Complete Report", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream", "document_type" -> "text",
    "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d2e"))
  val docDemolitionManagersReview: Document = Map("name" -> "Demolition Managers Review Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream",
    "document_type" -> "text", "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d2f"))
  val docDemolitionCityReview: Document = Map("name" -> "Demolition Citys Review Report", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream", "document_type" -> "text",
    "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d30"))
  val docExcavationStakingComplete: Document = Map("name" -> "Excavation-Staking-Complete-Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream",
    "document_type" -> "text", "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d31"))
  val docExcavationComplete: Document = Map("name" -> "Excavation-Complete-Report", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream", "document_type" -> "text",
    "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d32"))
  val docExcavationCityReview: Document = Map("name" -> "Excavation-Citys-Review-Report", "file_extension" -> ".txt",
    "description" -> "", "content_type" -> "application/octet-stream", "document_type" -> "text",
    "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d33"))
  val docExcavationRccContractorsReview: Document = Map("name" -> "Excavation-RCC-Contractors-Review-Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream",
    "document_type" -> "text", "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d34"))
  val docBasementConstructionComplete: Document = Map("name" -> "Basement-Construction-Complete-Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream",
    "document_type" -> "text", "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d35"))
  val docBasementConstructionCityReview: Document = Map("name" -> "Basement-Construction-Citys-Review-Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream",
    "document_type" -> "text", "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d36"))
  val docBasementConstructionManagersReview: Document = Map("name" -> "Basement-Construction-Managers-Review-Report",
    "file_extension" -> ".txt", "description" -> "", "content_type" -> "application/octet-stream",
    "document_type" -> "text", "_id" -> new ObjectId("56fe4e6bd5d8ad3da60d5d37"))

  val allDocuments = Seq(docOwnersProjectReport, docDemolitionPermit, docDemolitionComplete, docDemolitionManagersReview,
    docDemolitionCityReview, docExcavationStakingComplete, docExcavationComplete, docExcavationCityReview,
    docExcavationRccContractorsReview, docBasementConstructionComplete, docBasementConstructionCityReview,
    docBasementConstructionManagersReview, docRfiRequest, docRfiResponse)  ++ processDrawings()
  println(s"Original count: ${BWMongoDB3.document_master.count()}")
  BWMongoDB3.document_master.drop()
  println(s"After Drop count: ${BWMongoDB3.document_master.count()}")
  BWMongoDB3.document_master.insertMany(allDocuments)
  println(s"Final count: ${BWMongoDB3.document_master.count()}")
}
