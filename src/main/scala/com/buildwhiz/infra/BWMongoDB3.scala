package com.buildwhiz.infra

import com.mongodb.MongoClient
import com.mongodb.client.MongoCollection
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

import scala.language.dynamics
import scala.language.implicitConversions

object BWMongoDB3 extends Dynamic {

  type DocumentList = java.util.List[Document]
  type ObjectIdList = java.util.List[ObjectId]
  type ManyThings = java.util.List[_]

  class DynDoc(d: Document) extends Dynamic {
    def y: DynDoc = this
    def selectDynamic[T](fieldName: String): T = d.get(fieldName).asInstanceOf[T]
    def updateDynamic[T](fieldName: String)(value: T): AnyRef = d.put(fieldName, value.asInstanceOf[AnyRef])
    def asDoc: Document = d
    def ?(key: String): Boolean = d.containsKey(key)
    def remove(fieldName: String): Unit = d.remove(fieldName)
  }

  implicit def document2DynDoc(d: Document): DynDoc = new DynDoc(d)
  implicit def documentSeq2DynDocSeq(ds: Seq[Document]): Seq[DynDoc] = ds.map(new DynDoc(_))
  implicit def javaDocList2DynDocSeq(ds: DocumentList): Seq[DynDoc] = {
    val sd: Seq[Document] = ds.asScala
    sd.map(new DynDoc(_))
  }


  implicit def mapToDocument(map: Map[String, Any]): Document = {

    def seq2javaList(seq: Seq[_]): java.util.List[_] = seq.map({
      case m: Map[String, Any] @unchecked => mapToDocument(m)
      case s: Seq[_] => seq2javaList(s)
      case other => other
    }).asJava

    def pairs2document(seq: Seq[(String, Any)], document: Document = new Document()): Document = seq match {
      case Nil => document
      case head +: tail =>
        document.append(head._1, head._2 match {
          case m: Map[String, Any] @unchecked => mapToDocument(m)
          case seq: Seq[_] => seq2javaList(seq)
          case other => other
        })
        pairs2document(tail, document)
    }

    pairs2document(map.toSeq)
  }

  private lazy val mongoClient = new MongoClient()
  private lazy val db = mongoClient.getDatabase("BuildWhiz")

  def selectDynamic(cn: String): MongoCollection[Document] = db.getCollection(cn)

  def apply(collectionName: String): MongoCollection[Document] = db.getCollection(collectionName)

  def collectionNames: Seq[String] = db.listCollectionNames().asScala.toSeq
}
