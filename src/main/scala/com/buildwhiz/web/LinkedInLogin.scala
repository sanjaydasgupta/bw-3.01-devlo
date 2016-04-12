package com.buildwhiz.web

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class LinkedInLogin extends HttpServlet {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val url = new StringBuilder("https://www.linkedin.com/uas/oauth2/authorization")
    url.append("?response_type=code")
    url.append("&client_id=75rniv37k7ekug")
    url.append("&redirect_uri=https%3A%2F%2Fec2-52-10-9-236.us-west-2.compute.amazonaws.com%3A8443%2Fbuildwhiz-main%2Flinkedin-callback")
    url.append("&state=987654321")
    url.append("&scope=r_basicprofile")
    response.sendRedirect(url.toString())
  }

}
