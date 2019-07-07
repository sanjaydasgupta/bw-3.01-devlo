package com.buildwhiz.infra

import java.net.InetAddress

import com.buildwhiz.infra.DynDoc.document2DynDoc
import com.mongodb.{MongoClient, MongoClientURI}
import com.mongodb.client.{FindIterable, MongoCollection}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._
import scala.language.dynamics
import scala.language.implicitConversions

object BWMongoDB3 extends Dynamic {

  val $addToSet = "$addToSet"
  val $and = "$and"
  val $elemMatch = "$elemMatch"
  val $each = "$each"
  val $eq = "$eq"
  val $exists = "$exists"
  val $in = "$in"
  val $not = "$not"
  val $options = "$options"
  val $or = "$or"
  val $pull = "$pull"
  val $pullAll = "$pullAll"
  val $push = "$push"
  val $regex = "$regex"
  val $set = "$set"
  val $unset = "$unset"
  val $where = "$where"

  val project430ForestOid = new ObjectId("586336f692982d17cfd04bf8")
  val rfiRequestOid = new ObjectId("56fe4e6bd5d8ad3da60d5d38")
  val rfiResponseOid = new ObjectId("56fe4e6bd5d8ad3da60d5d39")
  val submittalOid = new ObjectId("572456d4d5d8ad25eb8943a2")

  private lazy val db = mongoClient.getDatabase("BuildWhiz")

  implicit def findIterable2DynDocSeq(fi: FindIterable[Document]): Seq[DynDoc] = fi.asScala.map(document2DynDoc).toSeq

  def selectDynamic(cn: String): MongoCollection[Document] = db.getCollection(cn)

  def apply(collectionName: String): MongoCollection[Document] = db.getCollection(collectionName)

  def collectionNames: Seq[String] = db.listCollectionNames().asScala.toSeq

  // Select cloud MongoDB (for AWS main instance) or local MongoDB (for all other instances) ...
  // AWS instance hostname = "ip-172-31-40-237" (internal private ip address, not public address)
  private lazy val mongoClient = if (InetAddress.getLocalHost.getHostName == "ip-172-31-41-133")
    new MongoClient(new MongoClientURI("""mongodb://buildwhiz-free:bw2#mongofree@cluster0-shard-00-00-cxymj.mongodb.net:27017,cluster0-shard-00-01-cxymj.mongodb.net:27017,cluster0-shard-00-02-cxymj.mongodb.net:27017/test?ssl=true&replicaSet=Cluster0-shard-0&authSource=admin"""))
  else new MongoClient()

  def databases: Seq[Document] = mongoClient.listDatabases.asScala.toSeq
}
