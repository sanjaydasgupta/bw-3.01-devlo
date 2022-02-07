package com.buildwhiz.infra

import com.mongodb.client.{MongoClients, MongoCollection}
import org.bson.Document
import org.bson.types.ObjectId

import scala.jdk.CollectionConverters._
import scala.language.{dynamics, implicitConversions}

object BWMongoLocal extends Dynamic {

  val project430ForestOid = new ObjectId("586336f692982d17cfd04bf8")
  val rfiRequestOid = new ObjectId("56fe4e6bd5d8ad3da60d5d38")
  val rfiResponseOid = new ObjectId("56fe4e6bd5d8ad3da60d5d39")
  val submittalOid = new ObjectId("572456d4d5d8ad25eb8943a2")

  private lazy val db = mongoClient.getDatabase("BuildWhiz")

  def selectDynamic(cn: String): MongoCollection[Document] = db.getCollection(cn)

  def apply(collectionName: String): MongoCollection[Document] = db.getCollection(collectionName)

  def collectionNames: Seq[String] = db.listCollectionNames().asScala.toSeq

  private lazy val mongoClient = MongoClients.create()
}
