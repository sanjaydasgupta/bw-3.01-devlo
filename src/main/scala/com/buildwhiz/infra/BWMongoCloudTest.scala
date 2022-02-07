package com.buildwhiz.infra

import com.buildwhiz.infra.DynDoc._

import scala.jdk.CollectionConverters._

object BWMongoCloudTest extends App {

  private def copyRecords(collectionName: String): Unit = {
    val records: Seq[DynDoc] = BWMongoLocal(collectionName).find().asScala.toSeq
    println(f"Copying ${records.length}%d records of $collectionName")
    for (record <- records) {
      //println(record.asDoc)
      BWMongoDB3(collectionName).insertOne(record.asDoc)
    }
    val personsInCloud: Seq[DynDoc] = BWMongoLocal(collectionName).find().asScala.toSeq
    println(f"Stored ${personsInCloud.length}%d in $collectionName")
  }

  private def reportPersons(): Unit = {
    val persons: Seq[DynDoc] = BWMongoDB3.persons.find().asScala.toSeq
    for (person <- persons) {
      println(person.asDoc)
    }
    println(f"Person count: ${persons.length}%d")
  }

  private def reportCollections(): Unit = {
    val collectionNames = BWMongoDB3.collectionNames
    for (collName <- collectionNames) {
      val records: Seq[DynDoc] = BWMongoDB3(collName).find().asScala.toSeq
      println(f"$collName has ${records.length}%d records")
    }
    println(f"Collection count: ${collectionNames.length}%d")
  }

//  val collectionNames = Seq(/*"organizations","trace_log", "mails", "phases", "rfi_messages", "instance_info",
//    "document_category_master", */ "projects", "content_types_master", "role_category_mapping",
//    "document_master", "activities", /*"persons",*/ "roles_master")

//  for (collName <- collectionNames) {
//    copyRecords(collName)
//  }

  //println(BWMongoLocal.collectionNames.map(cn => s""""$cn"""").mkString(", "))
  //copyPersons()
  //copyRecords("document_category_master")

  for (db <- BWMongoDB3.databases)
    println(db.toJson)

  //reportCollections()
  // organizations, trace_log, mails, phases, rfi_messages, instance_info, document_category_master, projects,
  // content_types_master, role_category_mapping, document_master, activities, persons, roles_master
}
