package com.buildwhiz.api

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import org.bson.types.ObjectId
import org.bson.Document

import scala.collection.JavaConverters._

class DocumentCategory extends HttpServlet with RestUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val user: DynDoc = getUser(request)
    val rawRoles: Seq[String] = user.roles[Many[String]].asScala
    if (rawRoles.mkString("|").matches(".*BW-(Data-)?Admin.*")) {
      val allCategories: Seq[Document] = BWMongoDB3.document_category_master.find().asScala.toSeq
      response.getWriter.println(allCategories.map(bson2json).mkString("[", ", ", "]"))
    } else {
      val roles = rawRoles.filter(r => r.split(":").length == 2 && r.split(":").count(_.length > 2) == 2)
      val categories: Seq[Document] = roles.flatMap(role => {
        val Array(roleCategory, roleName) =  role.split(":")
        val roleDocs: Seq[DynDoc] = BWMongoDB3.roles_master.find(Map("category" -> roleCategory, "name" -> roleName)).
          asScala.toSeq
        val roleOids = roleDocs.map(_._id[ObjectId])
        val permittedMappingRecs: Seq[DynDoc] = BWMongoDB3.role_category_mapping.
          find(Map("role_id" -> Map("$in" -> roleOids), "permitted" -> true)).asScala.toSeq
        val permittedCategoryOids = permittedMappingRecs.map(_.category_id[ObjectId])
        println(s"**** permittedCategoryOids: $permittedCategoryOids ****")
        val permittedCategoryRecs: Seq[DynDoc] = BWMongoDB3.document_category_master.
          find(Map("_id" -> Map("$in" -> permittedCategoryOids))).asScala.toSeq
        permittedCategoryRecs.sortBy(_.category[String]).map(_.asDoc)
      }).distinct
      response.getWriter.println(categories.map(bson2json).mkString("[", ", ", "]"))
    }
    response.setContentType("application/json")
    response.setStatus(HttpServletResponse.SC_OK)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestPost(request, response, "document_category_master")
  }

}