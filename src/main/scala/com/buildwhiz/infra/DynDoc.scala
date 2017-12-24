package com.buildwhiz.infra

import org.bson.Document

import scala.collection.JavaConverters._
import scala.language.dynamics
import scala.language.implicitConversions

class DynDoc(d: Document) extends Dynamic {
  def y: DynDoc = this
  def selectDynamic[T](fieldName: String): T = if (has(fieldName))
    d.get(fieldName).asInstanceOf[T]
  else
    throw new IllegalArgumentException(s"No field named '$fieldName'")
  def updateDynamic[T](fieldName: String)(value: T): AnyRef = d.put(fieldName, value.asInstanceOf[AnyRef])
  def asDoc: Document = d
  def has(key: String): Boolean = d.containsKey(key)
  def remove(fieldName: String): Unit = d.remove(fieldName)
}

object DynDoc {
  type Many[T] = java.util.List[T]

  implicit def document2DynDoc(d: Document): DynDoc = new DynDoc(d)

  implicit def documentSeq2DynDocSeq(ds: Seq[Document]): Seq[DynDoc] = ds.map(new DynDoc(_))

  implicit def many2seq[T](many: Many[T]): Seq[T] = many.asScala

  implicit def javaDocList2DynDocSeq(ds: Many[Document]): Seq[DynDoc] = {

    import org.bson.Document

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

}

