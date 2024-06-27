package com.buildwhiz.infra

import com.buildwhiz.infra.DynDoc.document2DynDoc
import com.buildwhiz.utils.BWLogger
import com.mongodb.client._
import com.mongodb._
import org.bson.Document
import org.bson.types.ObjectId

import scala.jdk.CollectionConverters._
import scala.language.{dynamics, implicitConversions}

class BWMongoDB(dbName: String, connectionStringLabel: String) extends Dynamic {

  val $addToSet = "$addToSet"
  val $and = "$and"
  val $elemMatch = "$elemMatch"
  val $each = "$each"
  val $eq = "$eq"
  val $exists = "$exists"
  val $gt = "$gt"
  val $gte = "$gte"
  val $in = "$in"
  val $lt = "$lt"
  val $lte = "$lte"
  val $ne = "$ne"
  val $not = "$not"
  val $options = "$options"
  val $or = "$or"
  val $pull = "$pull"
  val $pullAll = "$pullAll"
  val $push = "$push"
  val $regex = "$regex"
  val $rename = "$rename"
  val $set = "$set"
  val $unset = "$unset"
  val $where = "$where"

  val project430ForestOid = new ObjectId("586336f692982d17cfd04bf8")
  val rfiRequestOid = new ObjectId("56fe4e6bd5d8ad3da60d5d38")
  val rfiResponseOid = new ObjectId("56fe4e6bd5d8ad3da60d5d39")
  val submittalOid = new ObjectId("572456d4d5d8ad25eb8943a2")

  private lazy val db = mongoClient.getDatabase(dbName)

  implicit def findIterable2DynDocSeq(fi: FindIterable[Document]): Seq[DynDoc] = fi.asScala.map(document2DynDoc).toSeq
  implicit def aggregateIterable2DynDocSeq(aggregateIterable: AggregateIterable[Document]): Seq[DynDoc] =
    aggregateIterable.asScala.map(document2DynDoc).toSeq

  def selectDynamic(cn: String): MongoCollection[Document] = db.getCollection(cn)

  def apply(collectionName: String): MongoCollection[Document] = {
    collectionName.split("/") match {
      case Array(collName) => db.getCollection(collName)
      case Array(dbName, collName) => mongoClient.getDatabase(dbName).getCollection(collName)
    }
  }

  def collectionNames: Seq[String] = db.listCollectionNames().asScala.toSeq

  private lazy val mongoClient = sys.env.get(connectionStringLabel) match {
    case Some(mcs) => MongoClients.create(mcs)
    case None => MongoClients.create()
  }

  def databases: Seq[Document] = mongoClient.listDatabases.asScala.toSeq

  private val txnOptions: TransactionOptions = TransactionOptions.builder().readPreference(ReadPreference.primary()).
    readConcern(ReadConcern.LOCAL).writeConcern(WriteConcern.MAJORITY).build()

  def withTransaction[T](body: =>T): T = {
    val optionalClientSession = try {
      Some(mongoClient.startSession())
    } catch {
      case _: MongoClientException => None
      case t: Throwable => throw t
    }
    try {
      val transactionBody = new TransactionBody[T] {
        override def execute(): T = body
      }
      val (result, logMessage) = optionalClientSession match {
        case Some(session) =>
          (session.withTransaction(transactionBody, txnOptions), "SUCCESS with Transaction Commit")
        case None =>
          (transactionBody.execute(), "SUCCESS with NO Transaction")
      }
      BWLogger.log(getClass.getName, "withTransaction", logMessage)
      result
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "withTransaction", "Transaction Commit FAILURE")
        throw t
    } finally {
      optionalClientSession.foreach(_.close())
    }
  }

}
