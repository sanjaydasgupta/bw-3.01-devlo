package com.buildwhiz.baf3

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId
import scala.jdk.CollectionConverters._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DocCategoriesSave extends HttpServlet with HttpUtils with MailUtils {

  private def getCategoryKey(rec: DynDoc): String = rec.asDoc.keySet().asScala.filter(_.matches("L[0-9]+")).toSeq.
      sorted.mkString("/")

  private def getExistingCategoryKeys(teamRec: DynDoc): Set[String] = {
    val ownCategories = teamRec.own_doc_categories[Many[Document]]
    ownCategories.map(getCategoryKey).toSet
  }

  private def accessGrantRecords(teamOid: ObjectId, projectOid: ObjectId): Seq[DynDoc] = {
    val teamOidMatcher = Map($or -> Seq(Map("owner_team_id" -> teamOid), Map("granted_team_id" -> teamOid)))
    val aggrPipe: Seq[Document] = Seq(
      Map("$match" -> Map($and -> Seq(teamOidMatcher, Map("project_id" -> projectOid)))),
      Map("$lookup" -> Map("from" -> "teams", "localField" -> "owner_team_id", "foreignField" -> "_id", "as" -> "team")),
      Map("$unwind" -> Map("path" -> "$team", "preserveNullAndEmptyArrays" -> true)),
      Map("$project" -> Map("doc_category_id" -> true, "granted_team_id" -> true, "owner_team_id" -> true,
          "own_doc_categories" -> "$team.own_doc_categories"))
    )
    val grantInfos: Seq[DynDoc] = BWMongoDB3.folder_access_grants.aggregate(aggrPipe.asJava)
    for (grantInfo <- grantInfos) {
      val ownCategories: Seq[DynDoc] = grantInfo.own_doc_categories[Many[Document]]
      val ownCategory = ownCategories.find(oc => oc._id[ObjectId] == grantInfo.doc_category_id[ObjectId])
      grantInfo.category_key = ownCategory.map(getCategoryKey) match {
        case Some(categoryName) => categoryName
        case None => "???"
      }
      grantInfo.remove("own_doc_categories")
    }
    grantInfos
  }

  private def foldersToRecords(folders: Seq[DynDoc], path: Seq[String] = Seq.empty[String]): Seq[DynDoc] = {
    folders.flatMap(folder => {
      val folderPath = folder.name[String] +: path
      val children: Seq[DynDoc] = folder.children[Many[Document]]
      folder.path = folderPath.reverse
      if (children.nonEmpty) {
        folder +: foldersToRecords(children, folderPath)
      } else {
        Seq(folder)
      }
    })
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    response.setContentType("application/json")
    val t0 = System.currentTimeMillis()
    try {
      val parameterString = getStreamData(request)
      BWLogger.log(getClass.getName, request.getMethod, s"Parameter-String: $parameterString", request)
      val postData: DynDoc = Document.parse(parameterString)
      val projectOid = new ObjectId(postData.project_id[String])
      val teamOid = new ObjectId(postData.team_id[String])
      val googleFolderId = postData.google_folder_id[String]
      val folders: Seq[DynDoc] = postData.folders[Many[Document]]

      val teamRecord: DynDoc = BWMongoDB3.teams.find(Map("_id" -> teamOid)).head
      val optExistingGoogleFolderId: Option[String] = teamRecord.get[String]("google_folder_id")
      val existingAccessGrants: Seq[DynDoc] = accessGrantRecords(teamOid, projectOid)
      val categories: Many[Document] = foldersToRecords(folders).map(_.path[Seq[String]]).map(path => {
        val indexedPath = path.zipWithIndex
        val categoryDoc = new Document("_id", new ObjectId()).append("editable", false)
        for (pc <- indexedPath) {
          categoryDoc.append(s"L${pc._2 + 1}", pc._1)
        }
        categoryDoc
      }).asJava

      optExistingGoogleFolderId match {
        case None =>
          if (existingAccessGrants.nonEmpty) {
            val existingCategories = existingAccessGrants.map(ag => {
              val grantedTeamOid = ag.granted_team_id[ObjectId]
              val grantedTeamName = TeamApi.teamById(grantedTeamOid).team_name[String]
              val grantedCategoryName = ag.category_key[String]
              s"[category: '$grantedCategoryName', team: '$grantedTeamName']"
            }).mkString(", ")
            val errorMessage = s"Access grants exist for: $existingCategories"
            BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR: $errorMessage", request)
            val rv = new Document("ok", 0).append("message", errorMessage)
            response.getWriter.print(rv.toJson)
          } else {
            val updateResult = BWMongoDB3.teams.updateOne(Map("_id" -> teamOid, "project_id" -> projectOid),
              Map($set -> Map("google_folder_id" -> googleFolderId, "own_doc_categories" -> categories)))
            if (updateResult.getModifiedCount == 1) {
              val delay = System.currentTimeMillis() - t0
              val logMessage = s"(time: $delay ms) Stored ${categories.length} categories from Google folder '$googleFolderId' with team '$teamOid'"
              BWLogger.audit(getClass.getName, request.getMethod, logMessage, request)
              response.getWriter.print(successJson())
            } else {
              val errorMessage = s"MongoDB error updateResult: $updateResult"
              BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR: $errorMessage", request)
              val rv = new Document("ok", 0).append("message", errorMessage)
              response.getWriter.print(rv.toJson)
            }
          }
        case Some(existingGoogleFolderId) =>
          if (existingGoogleFolderId != googleFolderId) {
            val errorMessage = s"Given folder-id ($googleFolderId) differs from existing folder-id ($existingGoogleFolderId)"
            BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR: $errorMessage", request)
            val rv = new Document("ok", 0).append("message", errorMessage)
            response.getWriter.print(rv.toJson)
          } else {
            val existingCategoryKeys = getExistingCategoryKeys(teamRecord)
            val newCategories = categories.filterNot(cat => existingCategoryKeys.contains(getCategoryKey(cat)))
            val updateResult = BWMongoDB3.teams.updateOne(Map("_id" -> teamOid, "project_id" -> projectOid),
              Map($push -> Map("own_doc_categories" -> Map($each -> newCategories)),
                $set -> Map("google_folder_id" -> googleFolderId)))
            if (updateResult.getModifiedCount != 1) {
              val errorMessage = s"MongoDB error updateResult: $updateResult"
              BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR: $errorMessage", request)
              val rv = new Document("ok", 0).append("message", errorMessage)
              response.getWriter.print(rv.toJson)
            } else {
              val delay = System.currentTimeMillis() - t0
              val logMessage = s"(time: $delay ms) Stored ${categories.length} categories from Google folder '$googleFolderId' with team '$teamOid'"
              BWLogger.audit(getClass.getName, request.getMethod, logMessage, request)
              response.getWriter.print(successJson())
            }
          }
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
