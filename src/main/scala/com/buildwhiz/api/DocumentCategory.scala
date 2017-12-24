package com.buildwhiz.api

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.BWLogger
import org.bson.types.ObjectId
import org.bson.Document

class DocumentCategory extends HttpServlet with RestUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    val user: DynDoc = getUser(request)
    val rawRoles: Seq[String] = user.roles[Many[String]]
    if (rawRoles.mkString("|").matches(".*BW-(Data-)?Admin.*")) {
      val categoryRecords: Seq[DynDoc] = BWMongoDB3.document_category_master.find()
      val allCategories = categoryRecords.sortBy(_.category[String])
      response.getWriter.println(allCategories.map(d => bson2json(d.asDoc)).mkString("[", ", ", "]"))
    } else {
      val applicableRoles = rawRoles.filter(role => {
        val parts = role.split(":")
        parts.forall(_.length > 2) && (parts.length == 2 || (parts.length == 3 && parts.head.matches("view|edit")))
      })
      val theRoles = parameters.get("role") match {
        case Some("edit") => applicableRoles.filter(_.startsWith("edit:")).map(_.substring(5))
        case Some("view") | None => applicableRoles.map(r => if (r.matches("^(view|edit):.+")) r.substring(5) else r)
        case _ => Nil
      }
      val categories: Seq[Document] = theRoles.flatMap(role => {
        val Array(roleCategory, roleName) =  role.split(":")
        val roleDocs: Seq[DynDoc] = BWMongoDB3.roles_master.find(Map("category" -> roleCategory, "name" -> roleName))
        val roleOids = roleDocs.map(_._id[ObjectId])
        val permittedMappingRecs: Seq[DynDoc] = BWMongoDB3.role_category_mapping.
          find(Map("role_id" -> Map("$in" -> roleOids), "permitted" -> true))
        val permittedCategoryOids = permittedMappingRecs.map(_.category_id[ObjectId])
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
    val category: DynDoc = handleRestPost(request, response, "document_category_master")
    val categoryNameAndId = s"'${category.category[String]}' (${category._id[ObjectId]})"
    BWLogger.audit(getClass.getName, "handlePost", s"""Added category $categoryNameAndId""", request)
  }

}