package com.buildwhiz.infra

import org.bson.Document
import org.bson.types.ObjectId
import BWMongoDB3._

import scala.collection.JavaConverters._
object DocumentCategoryRecordsRedo extends App {

  //"", "", "572456d4d5d8ad25eb8943a7",

  private val data =
    """{"_id": ObjectId("5869d46692982d1c34f12d10"), "category": "ArchiCAD"}
      |{"_id": ObjectId("5869d46692982d1c34f12d11"), "category": "Architecture"}
      |{"_id": ObjectId("5869d46692982d1c34f12d13"), "category": "Building Science"}
      |{"_id": ObjectId("5869d46692982d1c34f12d14"), "category": "Civil"}
      |{"_id": ObjectId("5869d46692982d1c34f12d15"), "category": "Contracts"}
      |{"_id": ObjectId("5869d46692982d1c34f12d16"), "category": "Electrical"}
      |{"_id": ObjectId("5869d46692982d1c34f12d17"), "category": "Elevator"}
      |{"_id": ObjectId("5869d46692982d1c34f12d12"), "category": "Fire Alarm"}
      |{"_id": ObjectId("572456d4d5d8ad25eb8943a5"), "category": "Fire Sprinkler"}
      |{"_id": ObjectId("5869d46692982d1c34f12d18"), "category": "GeoTech Fld Rpts"}
      |{"_id": ObjectId("5869d46692982d1c34f12d19"), "category": "Interior"}
      |{"_id": ObjectId("572456d4d5d8ad25eb8943a6"), "category": "Landscape"}
      |{"_id": ObjectId("5869d46692982d1c34f12d1a"), "category": "Material Specs"}
      |{"_id": ObjectId("5869d46692982d1c34f12d1b"), "category": "Mechanical"}
      |{"_id": ObjectId("5869d46692982d1c34f12d1c"), "category": "Permits"}
      |{"_id": ObjectId("5869d46692982d1c34f12d1d"), "category": "Plumbing"}
      |{"_id": ObjectId("5869d46692982d1c34f12d1e"), "category": "Reports"}
      |{"_id": ObjectId("5869d46692982d1c34f12d1f"), "category": "Revit"}
      |{"_id": ObjectId("5869d46692982d1c34f12d20"), "category": "Special Insp Rpts"}
      |{"_id": ObjectId("5869d46692982d1c34f12d21"), "category": "Structure"}
      |""".stripMargin

  private val documents = data.split("\n").map(line => Document.parse(line))

  BWMongoDB3.document_category_master.insertMany(documents.toSeq.asJava)
}
