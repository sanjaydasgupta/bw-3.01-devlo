package com.buildwhiz.obsolete

import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

object DocumentCategoryRecordsRedo2 extends App {

  val csv = """Document Category,Management,,,Design,,,,,,,,,,,,,Construction,,,,,,,,,,
      |,Owner,Project Manager,Site Manager,Architect,Structural Engineer,Civil Engineer,Surveyor,Mechanical Engineer,Electrical Engineer,Plumbing Engineer,Landscape Designer,Building Scientist,Geotechnical Engineer,Interior Designer,Energy Conservationist,Arborist,Foundation,Framer,Plumber,Electrician,Mechanic,Civil Contractor,Concrete,Drywall,Roofer,Stucco,Siding
      |Architecture,X,X,X,X,X,X,X,X,X,X,X,X,X,X,X,X,X,X,X,X,X,X,X,X,X,X,X
      |Structure,X,X,X,X,X,X,,X,X,X,,X,X,,X,,X,X,X,X,X,,X,,,,
      |Interior Design,X,X,X,X,,,,X,X,X,,,,X,X,,,X,X,X,X,,,,,,
      |Landscape Design,X,X,X,X,,X,,,X,X,X,X,,X,,,,,X,X,,X,,,,,
      |Civil ,X,X,X,X,X,X,,,,,,X,X,,,,,,,,,X,X,,,,
      |Mechanical Design,X,X,X,X,X,X,,X,X,X,,,,X,X,,,,,X,X,,,,,,
      |Electrical Design,X,X,X,X,,,,,X,,,,,X,X,,,,X,X,,,,,,,
      |Plumbing Design,X,X,X,X,,,,,,,,,,X,,,,,,,,,,,,,""".stripMargin

  private def store(categories: Seq[(String, Seq[(String, String)])]): Unit = {
    val withRoleIds: Seq[(String, Seq[ObjectId])] = categories.map(c => {
      (c._1, c._2.map(c2 => {
        BWMongoDB3.roles_master.find(Map("category" -> c2._1, "name" -> c2._2)).asScala.head.y._id[ObjectId]}))
    })
    val docsWithIds = withRoleIds.map(d => {
      val doc = new Document("category", d._1)
      doc.put("roleIds", d._2.asJava)
      doc
    })

    println(s"Original document_categories count: ${BWMongoDB3.document_category_master.count()}")
    BWMongoDB3.document_category_master.insertMany(docsWithIds.asJava)
    println(s"Final document_categories count: ${BWMongoDB3.document_category_master.count()}")
  }

  val lines: Seq[String] = csv.split("\n").map(_.trim)
  val roleCategories: Seq[String] = lines.head.split(",", -1).tail.foldLeft(Nil: Seq[String])((seq, e) =>
    if (e.isEmpty) seq.head +: seq else e +: seq).reverse
  val roles: Seq[String] = lines.tail.head.split(",", -1).tail.toSeq
  println(roles)
  val documentDefinitions = lines.drop(2).map(_.split(",")).map(dd =>
    dd.head.trim -> dd.tail.toSeq.zipWithIndex.filter(_._1.nonEmpty).map(t => (roleCategories(t._2), roles(t._2))))
  store(documentDefinitions)
}
