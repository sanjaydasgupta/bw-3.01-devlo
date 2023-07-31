package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.HttpUtils
import com.mongodb.client.model.UpdateOneModel
import org.bson.Document
import org.bson.types.{Decimal128, ObjectId}

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object Decimal128Handler extends HttpUtils {

  private def handleProgressInfo2(fieldName: String, records: Seq[DynDoc], output: String => Unit): Unit = {
    output(s"Updating ${records.length} records<br/>")
    val bulkUpdateList = records.map(completedRecord => {
      val oid = completedRecord._id[ObjectId]
      val completedQuantity: Double = completedRecord.get[Any](fieldName) match {
        case Some(d128: Decimal128) => d128.doubleValue()
        case Some(dbl: Double) => dbl
        case Some(dint: Int) => dint.doubleValue()
        case _ => 0.0
      }
      new UpdateOneModel[Document](new Document("_id", oid),
        new Document($set, new Document(s"${fieldName}2", completedQuantity)))
    })
    val bulkSetResult = BWMongoDB3.deliverables_progress_infos.bulkWrite(bulkUpdateList)
    if (bulkSetResult.getModifiedCount != bulkUpdateList.length) {
      output(s"""<font color="red">FAILED set '$fieldName': $bulkSetResult</font><br/>""")
    } else {
      output(s"""<font color="green">SUCCESS processing '$fieldName'</font><br/>""")
    }
  }

  private def handleFields(go: Boolean, output: String => Unit): Unit = {
    for (fieldName <- Seq("completed_quantity", "total_quantity", "percent_complete")) {
      val recordsToUpdate: Seq[DynDoc] = BWMongoDB3.deliverables_progress_infos.
        find(Map(fieldName -> Map($exists -> true)))
      if (recordsToUpdate.isEmpty) {
        output(s"""<font color="red">NO '$fieldName' fields found</font><br/>""")
      } else {
        output(s"Nbr of records with '$fieldName': ${recordsToUpdate.length}<br/>")
        if (go) {
          handleProgressInfo2(fieldName, recordsToUpdate, output)
        }
      }
    }
  }

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    val go = args.length >= 1 && args(0) == "GO"
    val writer = response.getWriter
    def output(s: String): Unit = writer.print(s)
    response.setContentType("text/html")
    output(s"<html><body>")

    output(s"${getClass.getName}:main() ENTRY<br/>")
    val user: DynDoc = getUser(request)
    if (!PersonApi.isBuildWhizAdmin(Right(user)) || user.first_name[String] != "Sanjay") {
      throw new IllegalArgumentException("Not permitted")
    }
    handleFields(go, output)
    output(s"${getClass.getName}:main() EXIT-OK<br/>")
    output("</body></html>")
  }

}
