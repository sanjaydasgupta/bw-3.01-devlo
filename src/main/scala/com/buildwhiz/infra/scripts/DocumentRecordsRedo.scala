package com.buildwhiz.infra.scripts

import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

object DocumentRecordsRedo extends App {

  private val drawings: Seq[String] = Seq(
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

  private def processDrawings(): Seq[Document] = {
    def drawing2bsonDocument(drawing: String): Document = {
      val fields = Seq("_id", "sheet", "name", "description", "keywords", "author").
        zip(drawing.replaceAll("\\s", " ").split("#")).toMap
      val drawingDoc: Document = Seq("_id" -> new ObjectId(fields("_id")),
        "name" -> s"${fields("name")} (${fields("sheet")})", "description" -> fields("description"),
        "keywords" -> fields("keywords"), "author" -> fields.getOrElse("author", null),
        "document_type" -> "drawing", "file_extension" -> ".pdf", "preload" -> true).
        filterNot(p => p._1 == "author" && p._2 == null).toMap
      drawingDoc
    }
    drawings.map(drawing2bsonDocument)
  }

  //"", "", "572456d4d5d8ad25eb8943a3", "572456d4d5d8ad25eb8943a4",
  // "572456d4d5d8ad25eb8943a8",
  // "572456d4d5d8ad25eb8943a9", "572456d4d5d8ad25eb8943aa", "572456d4d5d8ad25eb8943ab", "572456d4d5d8ad25eb8943ac",
  // "572456d4d5d8ad25eb8943ad", "572456d4d5d8ad25eb8943ae", "572456d4d5d8ad25eb8943af", "572456d4d5d8ad25eb8943b0",
  // "572456d4d5d8ad25eb8943b1", "572456d4d5d8ad25eb8943b2", "572456d4d5d8ad25eb8943b3", "572456d4d5d8ad25eb8943b4"

  private val documents = Seq(
    "572456d4d5d8ad25eb8943a1#Geotechnical Report#Geotechnical Report#pdf",
    "5720a239d5d8ad41061c3a93#Energy Model - Front Building (PDF)#Energy Model (PDF)#pdf",
    "5720a239d5d8ad41061c3aa2#Energy Model - Front Building (BLD)#Energy Model (BLD)#bld",
    "5720a239d5d8ad41061c3a94#Energy Model - Back Building (PDF)#Energy Model (PDF)#pdf",
    "5720a239d5d8ad41061c3aa3#Energy Model - Back Building (BLD)#Energy Model (BLD)#bld",
    "5720a239d5d8ad41061c3a95#Structural Calculations - basement#Structural Calculations#pdf",
    "5720a239d5d8ad41061c3a96#Structural Calculations - buildings#Structural Calculations#pdf",
    "5720a239d5d8ad41061c3a97#Historcal Study#Historcal Study#pdf",
    "5720a239d5d8ad41061c3a98#Traffic Study#Traffic Study#pdf",
    "5720a239d5d8ad41061c3a99#Appraisal#Appraisal#pdf",
    "5720a239d5d8ad41061c3a9a#Photometrics - exterior#Photometrics Report#vsl",
    "5720a239d5d8ad41061c3a9b#Photometrics - Garage#Photometrics Report#vsl",
    "5720a239d5d8ad41061c3a9c#Environmenral Study - Phase 1#Environmenral Study#pdf",
    "5720a239d5d8ad41061c3a9d#Owner Project Requirements - OPR#Owner Project Requirements#pdf",
    "5720a239d5d8ad41061c3a9e#Permits#Permits#pdf",
    "5720a239d5d8ad41061c3a9f#Invoices#Invoices#pdf",
    "5720a239d5d8ad41061c3aa0#Receipts#Receipts#pdf",
    "5720a239d5d8ad41061c3aa1#Financial Reports#Financial Reports#pdf"
  )

  private def processDocuments(): Seq[Document] = {
    def document2bsonDocument(document: String): Document = {
      val fields = Seq("_id", "name", "document_type", "file_extension").
        zip(document.replaceAll("\\s", " ").split("#")).toMap
      val bsonDocument: Document = Seq("_id" -> new ObjectId(fields("_id")),
        "name" -> fields("name"), "description" -> fields("name"), "document_type" -> fields("document_type"),
        "file_extension" -> s".${fields("file_extension")}", "preload" -> true).toMap
      bsonDocument
    }
    documents.map(document2bsonDocument)
  }

  private val docRfiRequest: Document = Map("_id" -> rfiRequestOid, "name" -> "RFI-Request", "file_extension" -> ".txt", "description" -> "",
    "content_type" -> "application/octet-stream", "document_type" -> "text")
  private val docRfiResponse: Document = Map("_id" -> rfiResponseOid, "name" -> "RFI-Response", "file_extension" -> ".txt", "description" -> "",
    "content_type" -> "application/octet-stream", "document_type" -> "text")
  private val docSubmittal: Document = Map("_id" -> submittalOid, "name" -> "Submittal", "file_extension" -> ".txt", "description" -> "",
    "content_type" -> "application/octet-stream", "document_type" -> "text")

  val allDocuments = Seq(docRfiRequest, docRfiResponse, docSubmittal)  ++ processDrawings() ++ processDocuments()
  println(s"Original count: ${BWMongoDB3.document_master.count()}")
  BWMongoDB3.document_master.drop()
  println(s"After Drop count: ${BWMongoDB3.document_master.count()}")
  BWMongoDB3.document_master.insertMany(allDocuments.asJava)
  println(s"Final count: ${BWMongoDB3.document_master.count()}")
}
