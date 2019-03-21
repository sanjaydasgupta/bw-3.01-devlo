package com.buildwhiz.test;

import static org.junit.Assert.*;
import java.io.*;
import javax.servlet.http.*;
import com.buildwhiz.baf2.PersonApi;
import com.buildwhiz.baf2.ProjectList;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.mockito.Mockito;

public class TestBaf2ProjectList extends Mockito {

    @Test
    public void findSomeProjects() throws Exception {
        Document userSanjayDasgupta = PersonApi.personById(new ObjectId("56f124dfd5d8ad25b1325b40")).asDoc();
        HttpServletRequest request = TestUtils.getRequest(userSanjayDasgupta);
        HttpServletResponse response = mock(HttpServletResponse.class);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        new ProjectList().doGet(request, response);

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
    public void findNoProjects() throws Exception {
        Document userNoOne = PersonApi.personById(new ObjectId("56f124dfd5d8ad25b1325b41")).asDoc();
        HttpServletRequest request = TestUtils.getRequest(userNoOne);
        HttpServletResponse response = mock(HttpServletResponse.class);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        new ProjectList().doGet(request, response);

        verify(request, atLeast(1)).getSession();
        verify(request, atLeast(1)).getHeader("X-FORWARDED-FOR");
        verify(request, atLeast(0)).getHeader("User-Agent");
        verify(request.getSession(), atLeast(1)).getAttribute("bw-user");
        writer.flush(); // it may not have been flushed yet...
        String output = stringWriter.toString();
        assertEquals(output, "[]");
    }
}