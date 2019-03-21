package com.buildwhiz.test;

import com.buildwhiz.baf2.PersonApi;
import com.buildwhiz.baf2.DocumentList;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestBaf2DocumentList extends Mockito {

    @Test
    public void findWithProjectId() throws Exception {
        Document userPrabhasKejriwal = PersonApi.personById(new ObjectId("56f124dfd5d8ad25b1325b3e")).asDoc();
        //Document userSanjayDasgupta = PersonApi.personById(new ObjectId("56f124dfd5d8ad25b1325b40")).asDoc();
        //Document userTester2 = PersonApi.personById(new ObjectId("5acf514e4ac8efe4e19e91b4")).asDoc();
        HttpServletRequest request = TestUtils.getRequest(userPrabhasKejriwal);
        HashMap parameterMap = new HashMap<String, String[]>();
        parameterMap.put("project_id", new String[] {"586336f692982d17cfd04bf8"});
        when(request.getParameterMap()).thenReturn(parameterMap);
        HttpServletResponse response = mock(HttpServletResponse.class);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        new DocumentList().doGet(request, response);

        verify(request, atLeast(1)).getSession();
        verify(request, atLeast(1)).getHeader("X-FORWARDED-FOR");
        verify(request, atLeast(0)).getHeader("User-Agent");
        verify(request.getSession(), atLeast(1)).getAttribute("bw-user");
        writer.flush(); // it may not have been flushed yet...
        String output = stringWriter.toString();
        assertTrue(output.startsWith("[{"));
        assertTrue(output.endsWith("}]"));
    }

    @Test
    public void findWithoutProjectId() throws Exception {
        Document userPrabhasKejriwal = PersonApi.personById(new ObjectId("56f124dfd5d8ad25b1325b3e")).asDoc();
        //Document userSanjayDasgupta = PersonApi.personById(new ObjectId("56f124dfd5d8ad25b1325b40")).asDoc();
        //Document userTester2 = PersonApi.personById(new ObjectId("5acf514e4ac8efe4e19e91b4")).asDoc();
        HttpServletRequest request = TestUtils.getRequest(userPrabhasKejriwal);
        HttpServletResponse response = mock(HttpServletResponse.class);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        new DocumentList().doGet(request, response);

        verify(request, atLeast(1)).getSession();
        verify(request, atLeast(1)).getHeader("X-FORWARDED-FOR");
        verify(request, atLeast(0)).getHeader("User-Agent");
        verify(request.getSession(), atLeast(1)).getAttribute("bw-user");
        writer.flush(); // it may not have been flushed yet...
        String output = stringWriter.toString();
        assertEquals(output, "[]");
    }

    @Test
    public void findNoProjects() throws Exception {
        Document userNoOne = PersonApi.personById(new ObjectId("56f124dfd5d8ad25b1325b41")).asDoc();
        HttpServletRequest request = TestUtils.getRequest(userNoOne);
        HttpServletResponse response = mock(HttpServletResponse.class);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        new DocumentList().doGet(request, response);

        verify(request, atLeast(1)).getSession();
        verify(request, atLeast(1)).getHeader("X-FORWARDED-FOR");
        verify(request, atLeast(0)).getHeader("User-Agent");
        verify(request.getSession(), atLeast(1)).getAttribute("bw-user");
        writer.flush(); // it may not have been flushed yet...
        String output = stringWriter.toString();
        assertEquals(output, "[]");
    }
}