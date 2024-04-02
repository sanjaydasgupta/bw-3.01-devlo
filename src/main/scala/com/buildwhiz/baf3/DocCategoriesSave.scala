package com.buildwhiz.baf3

import com.buildwhiz.baf2.{DocumentApi, PersonApi, ProjectApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId
import scala.jdk.CollectionConverters._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DocCategoriesSave extends HttpServlet with HttpUtils with MailUtils {

  private def foldersToRecords(folders: Seq[DynDoc], path: Seq[String] = Seq.empty[String]): Seq[DynDoc] = {
    folders.flatMap(folder => {
      val folderPath = folder.name[String] +: path
      val children: Seq[DynDoc] = folder.children[Many[Document]]
      if (children.nonEmpty) {
        foldersToRecords(children, folderPath)
      } else {
        folder.path = folderPath.reverse
        Seq(folder)
      }
    })
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val parameterString = getStreamData(request)
      BWLogger.log(getClass.getName, request.getMethod, s"Parameter-String: $parameterString", request)
      val postData: DynDoc = Document.parse(parameterString)
      val projectOid = new ObjectId(postData.project_id[String])
      val teamOid = new ObjectId(postData.team_id[String])
      val googleFolderId = postData.google_folder_id[String]
      val folders: Seq[DynDoc] = postData.folders[Many[Document]]
      val categories: Many[Document] = foldersToRecords(folders).map(_.path[Seq[String]]).map(path => {
        val indexedPath = path.zipWithIndex
        val categoryDoc = new Document("_id", new ObjectId()).append("editable", false)
        for (pc <- indexedPath) {
          categoryDoc.append(s"L${pc._2 + 1}", pc._1)
        }
        categoryDoc
      }).asJava
      val categoriesString = categories.map(_.asDoc.toJson)
      BWLogger.log(getClass.getName, request.getMethod, s"INFO: $categoriesString", request)
      val updateResult = BWMongoDB3.teams.updateOne(Map("_id" -> teamOid, "project_id" -> projectOid),
          Map($push -> Map("own_doc_categories" -> Map($each -> categories)),
          $set -> Map("google_folder_id" -> googleFolderId)))
      if (updateResult.getModifiedCount != 1) {
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      }
      val logMessage = s"Stored ${categories.length} categories from Google folder '$googleFolderId' with team '$teamOid'"
      BWLogger.audit(getClass.getName, request.getMethod, logMessage, request)
      response.getWriter.print(successJson())
      response.setContentType("application/json")
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
