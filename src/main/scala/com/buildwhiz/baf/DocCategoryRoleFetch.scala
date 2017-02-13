package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class DocCategoryRoleFetch extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val roleOid = new ObjectId(parameters("role_id"))
      val allCategories: Seq[DynDoc] = BWMongoDB3.document_category_master.find()
      val categoriesWithPermissions = allCategories.sortBy(_.category[String].toLowerCase).map(cat => {
        val isPermitted: Seq[DynDoc] = BWMongoDB3.role_category_mapping.
          find(Map("category_id" -> cat._id[ObjectId], "role_id" -> roleOid, "permitted" -> true))
        cat.permitted = isPermitted.nonEmpty
        cat.asDoc
      })
      response.getWriter.println(categoriesWithPermissions.map(bson2json).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
    }
  }
}
