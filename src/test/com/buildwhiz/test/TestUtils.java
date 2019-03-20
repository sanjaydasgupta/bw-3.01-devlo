package com.buildwhiz.test;

import org.bson.Document;
import javax.servlet.http.*;
import org.mockito.Mockito;

class TestUtils extends Mockito {

    static HttpServletRequest getRequest(Document user) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("X-FORWARDED-FOR")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getSession(false)).thenReturn(null);
        when(request.getHeader("User-Agent")).thenReturn("user-agent");

        HttpSession httpSession = mock(HttpSession.class);
        when(httpSession.getAttribute("bw-user")).thenReturn(user);

        when(request.getSession()).thenReturn(httpSession);

        StringBuffer sb = new StringBuffer();
        sb.append("http://").append("8080");

        when(request.getRequestURL()).thenReturn(sb);

        when(request.getQueryString()).thenReturn("bw-dot-2.01/baf2/ProjectList");
        return request;
    }

}
