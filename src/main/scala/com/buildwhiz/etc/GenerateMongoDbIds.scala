package com.buildwhiz.etc

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import org.bson.types.ObjectId
import org.bson.Document
import scala.collection.JavaConverters._

object GenerateMongoDbIds extends App {
  println(s"Count=${BWMongoDB3.id_generation.count()}")
  for (i <- 1 to 20) {
    val tempObject: Document = Map("value" -> (900 + i))
    BWMongoDB3.id_generation.insertOne(tempObject)
    val tempDynObj: DynDoc = tempObject
  }
  val ids: Seq[DynDoc] = BWMongoDB3.id_generation.find().asScala.toList
  println(ids.map(obj => s""""${obj._id[ObjectId]}"""").mkString(", "))
  println(s"Count=${BWMongoDB3.id_generation.count()}")
  BWMongoDB3.id_generation.deleteMany(new Document)
  println(s"Count=${BWMongoDB3.id_generation.count()}")
}
