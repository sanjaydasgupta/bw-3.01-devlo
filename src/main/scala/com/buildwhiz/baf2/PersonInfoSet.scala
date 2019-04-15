package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import org.bson.Document

import scala.collection.JavaConverters._

class PersonInfoSet extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {

      val parameterMap = getParameterMap(request)

      val personOid = new ObjectId(parameterMap("person_id"))
      val person = PersonApi.personById(personOid)

      val noop = (s: String) => s
      def rating2int(rating: String): Int = {
        if (rating.matches("[1-5]"))
          rating.toInt
        else
          throw new IllegalArgumentException(s"bad rating: '$rating'")
      }
      val workEmailIndex = person.emails[Many[Document]].indexWhere(_.`type`[String] == "work")
      if (workEmailIndex == -1)
        throw new IllegalArgumentException("Work email not pre-defined in user record")
      val workPhoneIndex = person.phones[Many[Document]].indexWhere(_.`type`[String] == "work")
      if (workPhoneIndex == -1)
        throw new IllegalArgumentException("Work phone not pre-defined in user record")

      val parameterConverters: Map[String, (String => Any, String)] = Map(
        ("first_name", (noop, "first_name")),
        ("last_name", (noop, "last_name")),
        ("work_address", (noop, "work_address")),
        ("rating", (rating2int, "rating")),
        ("skills", (_.split(",").map(_.trim).toSeq.filter(_.trim.nonEmpty).asJava, "skills")),
        ("years_experience", (_.toDouble, "years_experience")),
        ("active", (_.toBoolean, "active")),
        ("work_email", (noop, s"emails.$workEmailIndex.email")),
        ("work_phone", (noop, s"phones.$workPhoneIndex.phone")),
        ("person_id", (new ObjectId(_), "person_id"))
      )
      val unknownParameters = parameterMap.keySet.toArray.filterNot(parameterConverters.contains)
      if (unknownParameters.nonEmpty)
        throw new IllegalArgumentException(s"""Unknown parameter(s): ${unknownParameters.mkString(", ")}""")

      val parameterValues = parameterConverters.map(pc => {
        val paramName = pc._1
        val paramConverter = pc._2._1
        val fieldName = pc._2._2
        val exists = parameterMap.contains(paramName)
        (paramName, paramConverter, fieldName, exists)
      }).filter(_._4).map(t => (t._3, t._2(parameterMap(t._1))))

      val (personIdAndValue, parameterNamesAndValues) = parameterValues.partition(_._1 == "person_id")

      if (parameterNamesAndValues.isEmpty)
        throw new IllegalArgumentException("No parameters found")

      val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> personIdAndValue.head._2),
          Map("$set" -> parameterNamesAndValues.toMap))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

      response.setStatus(HttpServletResponse.SC_OK)
      val message = s"Changed ${person.first_name[String]} ${person.last_name[String]}'s " +
          s"parameters: ${parameterNamesAndValues.toMap}"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}