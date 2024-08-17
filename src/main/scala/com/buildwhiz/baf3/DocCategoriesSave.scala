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

  private def getCategoryKey(rec: DynDoc): String = rec.asDoc.entrySet().asScala.filter(_.getKey.matches("L[0-9]+")).
      toSeq.sortBy(_.getKey).map(_.getValue).mkString("/")

  private def accessGrantRecords(teamOid: ObjectId, projectOid: ObjectId): Seq[DynDoc] = {
    //val teamOidMatcher = Map($or -> Seq(Map("owner_team_id" -> teamOid), Map("granted_team_id" -> teamOid)))
    val teamOidMatcher = Map("owner_team_id" -> teamOid)
    val aggrPipe: Seq[Document] = Seq(
      Map("$match" -> Map($and -> Seq(teamOidMatcher, Map("project_id" -> projectOid)))),
      Map("$lookup" -> Map("from" -> "teams", "localField" -> "owner_team_id", "foreignField" -> "_id", "as" -> "team")),
      Map("$unwind" -> Map("path" -> "$team", "preserveNullAndEmptyArrays" -> true)),
      Map("$project" -> Map("doc_category_id" -> true, "granted_team_id" -> true,
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

      def saveCategories(categories: Many[Document], optSameNames: Option[Int] = None): Unit = {
        val updateResult = BWMongoDB3.teams.updateOne(Map("_id" -> teamOid, "project_id" -> projectOid),
          Map($set -> Map("google_folder_id" -> googleFolderId, "own_doc_categories" -> categories)))
        val delay = System.currentTimeMillis() - t0
        if (updateResult.getModifiedCount == 1) {
          val statusMessage = optSameNames match {
            case None => s"Stored ${categories.length} categories from " +
              s"Google folder '$googleFolderId' with team '$teamOid'"
            case Some(dupCount) => s"Stored ${categories.length} categories ($dupCount unchanged) " +
              s"from Google folder '$googleFolderId' with team '$teamOid'"
          }
          BWLogger.audit(getClass.getName, request.getMethod, "(time: $delay ms) " + statusMessage, request)
          response.getWriter.print(successJson(message = statusMessage))
        } else {
          val (isError, statusMessage) = optSameNames match {
            case None => (true, s"MongoDB error updateResult: $updateResult")
            case Some(dupCount) =>
              if (dupCount == categories.length) {
                (false, s"All ${categories.length} categories unchanged " +
                  s"in Google folder '$googleFolderId'")
              } else {
                (true, s"Stored ${categories.length} categories ($dupCount unchanged) " +
                  s"from Google folder '$googleFolderId' with team '$teamOid'")
              }

          }
          if (isError) {
            BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR: (time: $delay ms) $statusMessage", request)
            val errorJson = new Document("ok", 0).append("message", statusMessage).toJson
            response.getWriter.print(errorJson)
          } else {
            BWLogger.audit(getClass.getName, request.getMethod, s"EXIT-WARN: (time: $delay ms) $statusMessage", request)
            val returnJson = new Document("ok", 0).append("message", statusMessage).toJson
            response.getWriter.print(returnJson)
          }
        }
      }

      val teamRecord: DynDoc = BWMongoDB3.teams.find(Map("_id" -> teamOid)).head
      val optExistingGoogleFolderId: Option[String] = teamRecord.get[String]("google_folder_id")
      val existingAccessGrants: Seq[DynDoc] = accessGrantRecords(teamOid, projectOid)
      val googleFolderCategories: Many[Document] = foldersToRecords(folders).map(record => {
        val indexedPath = record.path[Seq[String]].zipWithIndex
        val categoryDoc = new Document("_id", new ObjectId()).append("editable", false)
        for (pc <- indexedPath) {
          categoryDoc.append(s"L${pc._2 + 1}", pc._1)
        }
        categoryDoc
      }).asJava

      if (googleFolderCategories.isEmpty) {
        BWLogger.log(getClass.getName, request.getMethod, "EXIT-WARN: No categories found", request)
        response.getWriter.print(successJson())
      } else {
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
              saveCategories(googleFolderCategories)
            }
          case Some(existingGoogleFolderId) =>
            if (existingGoogleFolderId != googleFolderId) {
              val errorMessage = s"Given folder-id ($googleFolderId) differs from existing folder-id ($existingGoogleFolderId)"
              BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR: $errorMessage", request)
              val rv = new Document("ok", 0).append("message", errorMessage)
              response.getWriter.print(rv.toJson)
            } else {
              val categoryKeyToIdMap = {
                val ownCategories: Seq[DynDoc] = teamRecord.own_doc_categories[Many[Document]]
                ownCategories.map(oc => (getCategoryKey(oc), oc._id[ObjectId])).toMap
              }
              var sameNames = 0
              for (category <- googleFolderCategories) {
                val categoryKey = getCategoryKey(category)
                if (categoryKeyToIdMap.contains(categoryKey)) {
                  category._id = categoryKeyToIdMap(categoryKey)
                  sameNames += 1
                }
              }
              saveCategories(googleFolderCategories, Some(sameNames))
            }
        }
      }
    } catch {
      case t: Throwable =>
        reportFatalException(t, getClass.getName, request, response)
    }
  }
}
