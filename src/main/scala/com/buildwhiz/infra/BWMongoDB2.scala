package com.buildwhiz.infra

import java.util.{ArrayList => JArrayList}

import com.mongodb.MongoClient
import com.mongodb.client.{MongoCollection, MongoDatabase}
import com.mongodb.client.result.UpdateResult
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._
import scala.language.dynamics
import scala.util.{Success, Try}

object BWMongoDB2 extends Dynamic {

  lazy val mongoClient = new MongoClient()
  lazy val mongoDb: MongoDatabase = mongoClient.getDatabase("BuildWhiz")

//  val projectsCollection = mongoDb.getCollection("projects")
//  val phasesCollection = mongoDb.getCollection("phases")
//  val personsCollection = mongoDb.getCollection("persons")
//  val prerequisitesCollection = mongoDb.getCollection("prerequisites")
//
  type Documents = JArrayList[Document]
  type Many[T] = JArrayList[T]

  def selectDynamic(collectionName: String): BWAccessor = {
    val collection = mongoDb.getCollection(collectionName)
//    val arrayList = new JArrayList[Document]()
//    for (doc <- collection.find()) {
//      arrayList.add(new Document(doc))
//    }
    new BWAccessor(collection, None/*, arrayList*/)
  }

  private def makeFilter(filter: AnyRef): Document = filter match {
    case None => new Document
    case Some(f: AnyRef) => makeFilter(f)
    case oid: ObjectId => new Document("_id", oid)
    case oidString: String => new Document("_id", new ObjectId(oidString))
    case key: (String @ unchecked, AnyRef @ unchecked) => new Document(key._1, key._2)
    case keys: Map[String @ unchecked, AnyRef @ unchecked] => new Document(keys.asJava)
  }

  def applyDynamic(collectionName: String)(filter: AnyRef): BWAccessor = {
    val collection = mongoDb.getCollection(collectionName)
//    val selection = collection.find(makeFilter(filter))
//    val arrayList = new JArrayList[Document]()
//    for (doc <- selection) {
//      arrayList.add(new Document(doc))
//    }
    new BWAccessor(collection, Some(filter)/*, arrayList*/)
  }

  class BWAccessor(mongoCollection: MongoCollection[Document], filter: Option[AnyRef],
    /*documents: Many[Document], */path: List[AnyRef] = List.empty) extends Dynamic {

    def selectDynamic(key: String): BWAccessor =
      new BWAccessor(mongoCollection, filter/*, documents*/, path :+ key)

    def ?[T]: T = {
      val theDocuments = mongoCollection.find(makeFilter(filter))
      val documents: Many[Document] = new JArrayList[Document]()
      for (doc <- theDocuments.asScala) {
        //documents.add(new Document(doc))
        documents.add(doc)
      }
      var currentValue: AnyRef = documents
      for (pe <- path) (pe, currentValue) match {
        case (key: String, cv: Document) =>
          if (cv.containsKey(key)) currentValue = cv.get(key) else throw new Exception(s"No name '$key'")
        case (index: Integer, cv: JArrayList[AnyRef @ unchecked]) =>
          if (index >= cv.size || index < 0) throw new Exception(s"No index '$index'") else currentValue = cv.get(index)
        case _ => throw new Exception(s"Bad path element '$pe'")
      }
      currentValue.asInstanceOf[T]
    }

    def * : Seq[BWAccessor] =
      (0 until size).map(idx => new BWAccessor(mongoCollection, filter/*, documents*/, path :+ idx.asInstanceOf[AnyRef]))

    def size: Int = Try(?[Many[_]].size) match {
      case Success(s: Int) => s
      case _ => throw new Exception(s"Not a list '${path.last}'")
    }

    def < (value: Any): UpdateResult = {
      val pathString = path.drop(1).mkString(".")
      val filterString = makeFilter(filter)
      val update = new Document("$set", new Document(pathString, value))
      BWLogger.log("BWMongoDB", "<", "<", "filter" -> filterString.toString, "update" -> update.toString)
      //println(s"filter: $filterString, update: $update")
      mongoCollection.updateOne(filterString, update)
    }

    def += (value: Any): UpdateResult = {
      val pathString = path.drop(1).mkString(".")
      val filterString = makeFilter(filter)
      val update = new Document("$push", new Document(pathString, value))
      BWLogger.log("BWMongoDB", "+=", "+=", "filter" -> filterString.toString, "update" -> update.toString)
      //println(s"filter: $filterString, update: $update")
      mongoCollection.updateOne(filterString, update)
    }

    def ++= (value: Many[Any]): UpdateResult = {
      val pathString = path.drop(1).mkString(".")
      val filterString = makeFilter(filter)
      val update = new Document("$push", new Document(pathString, new Document("$each", value)))
      BWLogger.log("BWMongoDB", "++=", "++=", "filter" -> filterString.toString, "update" -> update.toString)
      //println(s"filter: $filterString, update: $update")
      mongoCollection.updateOne(filterString, update)
    }

    def +?= (value: Any): UpdateResult = {
      val pathString = path.drop(1).mkString(".")
      val filterString = makeFilter(filter)
      val update = new Document("$addToSet", new Document(pathString, value))
      BWLogger.log("BWMongoDB", "+?=", "+?=", "filter" -> filterString.toString, "update" -> update.toString)
      //println(s"filter: $filterString, update: $update")
      mongoCollection.updateOne(filterString, update)
    }

    def ++?= (value: Many[Any]): UpdateResult = {
      val pathString = path.drop(1).mkString(".")
      val filterString = makeFilter(filter)
      val update = new Document("$addToSet", new Document(pathString, new Document("$each", value)))
      BWLogger.log("BWMongoDB", "++?=", "++?=", "filter" -> filterString.toString, "update" -> update.toString)
      //println(s"filter: $filterString, update: $update")
      mongoCollection.updateOne(filterString, update)
    }

    def -= (value: Any): UpdateResult = {
      val pathString = path.drop(1).mkString(".")
      val filterString = makeFilter(filter)
      val update = new Document("$pull", new Document(pathString, value))
      BWLogger.log("BWMongoDB", "-=", "-=", "filter" -> filterString.toString, "update" -> update.toString)
      //println(s"filter: $filterString, update: $update")
      mongoCollection.updateOne(filterString, update)
    }

    def --= (value: Many[Any]): UpdateResult = {
      val pathString = path.drop(1).mkString(".")
      val filterString = makeFilter(filter)
      val update = new Document("$pulAll", new Document(pathString, new Document("$each", value)))
      BWLogger.log("BWMongoDB", "--=", "--=", "filter" -> filterString.toString, "update" -> update.toString)
      //println(s"filter: $filterString, update: $update")
      mongoCollection.updateOne(filterString, update)
    }

    def > : UpdateResult = {
      val pathString = path.drop(1).mkString(".")
      val filterString = makeFilter(filter)
      val update = new Document("$pop", new Document(pathString, 1))
      BWLogger.log("BWMongoDB", ">", ">", "filter" -> filterString.toString, "update" -> update.toString)
      //println(s"filter: $filterString, update: $update")
      mongoCollection.updateOne(filterString, update)
    }

    def >> : UpdateResult = {
      val pathString = path.drop(1).mkString(".")
      val filterString = makeFilter(filter)
      val update = new Document("$pop", new Document(pathString, -1))
      //println(s"filter: $filterString, update: $update")
      mongoCollection.updateOne(filterString, update)
    }

    def x: UpdateResult = {
      val pathString = path.drop(1).mkString(".")
      val filterString = makeFilter(filter)
      val update = new Document("$unset", new Document(pathString, 1))
      BWLogger.log("BWMongoDB", "x", "x", "filter" -> filterString.toString, "update" -> update.toString)
      //println(s"filter: $filterString, update: $update")
      mongoCollection.updateOne(filterString, update)
    }

  }

  def main(args: Array[String]): Unit = {
    val projects: Seq[BWAccessor] = BWMongoDB2.projects.*
    println(s"size: ${projects.size}")
    projects.foreach(p => println(p.name.?[String]))
    //println(BWMongoDB2.projects._0.name.size)
    //println(projects.?[Documents].size())
    println(projects.head.phases.*.head.name.?[String])
    println(projects(1).phases.*(1).name.?[String])
    //val st = projects._1.phases._1.name < "** Updated Again Phase Name **"
    //println(st)
  }

}
