package com.buildwhiz.api

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import org.bson.types.ObjectId
import org.bson.Document

import scala.collection.JavaConverters._

class DocumentCategory extends HttpServlet with RestUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val user: DynDoc = getUser(request)
    val rawRoles: Seq[String] = user.roles[Many[String]].asScala
    if (rawRoles.mkString("|").matches(".*BW-(Data-)?Admin.*")) {
      val categoryRecords: Seq[DynDoc] = BWMongoDB3.document_category_master.find()
      val allCategories = categoryRecords.sortBy(_.category[String])
      response.getWriter.println(allCategories.map(d => bson2json(d.asDoc)).mkString("[", ", ", "]"))
    } else {
      val roles = rawRoles.filter(r => r.split(":").length == 2 && r.split(":").count(_.length > 2) == 2)
      val categories: Seq[Document] = roles.flatMap(role => {
        val Array(roleCategory, roleName) =  role.split(":")
        val roleDocs: Seq[DynDoc] = BWMongoDB3.roles_master.find(Map("category" -> roleCategory, "name" -> roleName))
        val roleOids = roleDocs.map(_._id[ObjectId])
        val permittedMappingRecs: Seq[DynDoc] = BWMongoDB3.role_category_mapping.
          find(Map("role_id" -> Map("$in" -> roleOids), "permitted" -> true))
        val permittedCategoryOids = permittedMappingRecs.map(_.category_id[ObjectId])
        println(s"**** permittedCategoryOids: $permittedCategoryOids ****")
        val permittedCategoryRecs: Seq[DynDoc] = BWMongoDB3.document_category_master.
          find(Map("_id" -> Map("$in" -> permittedCategoryOids)))
        permittedCategoryRecs.map(_.asDoc)
      }).distinct.sortBy(_.get("category").asInstanceOf[String])
      response.getWriter.println(categories.map(bson2json).mkString("[", ", ", "]"))
    }
    response.setContentType("application/json")
    response.setStatus(HttpServletResponse.SC_OK)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestPost(request, response, "document_category_master")
  }

}