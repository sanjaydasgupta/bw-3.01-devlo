package com.buildwhiz.baf2

import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class CountryStateNames extends HttpServlet with HttpUtils {

  val stateNames: Map[String, Seq[String]] = Map(
    "CA" -> Seq(
      "Alberta", "British Columbia", "Manitoba", "Newfoundland and Labrador", "New Brunswick",
      "Nova Scotia", "Ontario", "Prince Edward Island", "Quebec", "Saskatchewan"
    ),
    "IN" -> Seq(
      "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh","Goa", "Gujrat", "Haryana",
      "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra",
      "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu",
      "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal"
    ),
    "US" -> Seq(
      "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado", "Connecticut", "Delaware", "Florida",
      "Georgia", "Hawaii", "Idaho", "Illinois", "Indiana", "Iowa", "Kansas", "Kentucky", "Louisiana", "Maine",
      "Maryland", "Massachusetts", "Michigan", "Minnesota", "Mississippi", "Missouri", "Montana", "Nebraska",
      "Nevada", "New Hampshire", "New Jersey", "New Mexico", "New York", "North Carolina", "North Dakota",
      "Ohio", "Oklahoma", "Oregon", "Pennsylvania", "Rhode Island", "South Carolina", "South Dakota", "Tennessee",
      "Texas", "Utah", "Vermont", "Virginia", "Washington", "West Virginia", "Wisconsin", "Wyoming"
    )
  )

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val countryCode = parameters("country_code")
      if (!stateNames.contains(countryCode))
        throw new IllegalArgumentException(s"Unknown country-code: '$countryCode'")
      val stateNameList = stateNames(countryCode)
      response.getWriter.print(stateNameList.sorted.mkString("[\"", "\", \"", "\"]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK (${stateNameList.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}