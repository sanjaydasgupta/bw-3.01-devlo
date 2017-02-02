package com.buildwhiz.infra.scripts

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import org.bson.Document

import scala.collection.JavaConverters._

object MasterContentAndExtensionRecords {

  private val data =
    """586fa8c792982d2627b2fd96 CAD dwg dxf ifc rvt nwd
      |586fa8c792982d2627b2fd98 Excel xls xlsx csv
      |586fa8c792982d2627b2fd99 Image bmp gif jpg jpeg png tif tiff
      |586fa8c792982d2627b2fd9b PDF pdf
      |586fa8c792982d2627b2fd9d PPT ppt pptx pps
      |586fa8c792982d2627b2fd9f Text txt
      |586fa8c792982d2627b2fda1 Word doc docx rtf
      |586fa8c792982d2627b2fda2 XML xml
      |586fa8c792982d2627b2fda3 ZIP zip rar""".stripMargin

  def main(args: Array[String]): Unit = {
    println(s"Initial size of content_types_master: ${BWMongoDB3.content_types_master.count()}")
    BWMongoDB3.content_types_master.drop()
    val data2 = data.split("\n").toSeq.map(line => line.split("\\s+").toSeq).map(a => (a.head, a(1), a.drop(2)))
    //data2.foreach(println)
    val docs: DocumentList = data2.map(t => Map("_id" -> t._1, "type" -> t._2, "extensions" -> t._3.asJava)).
      map(t => new Document(t)).asJava
    BWMongoDB3.content_types_master.insertMany(docs)
    println(s"Final size of content_types_master: ${BWMongoDB3.content_types_master.count()}")
  }

}
