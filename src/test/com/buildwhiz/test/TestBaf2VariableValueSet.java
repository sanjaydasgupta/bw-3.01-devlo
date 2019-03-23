package com.buildwhiz.test;

import com.buildwhiz.baf2.PersonApi;
import com.buildwhiz.baf2.VariableValueSet;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;

import static org.junit.Assert.assertTrue;

public class TestBaf2VariableValueSet extends Mockito {

    @Test
    public void setTimerDuration() throws Exception {
        Document userSanjayDasgupta = PersonApi.personById(new ObjectId("56f124dfd5d8ad25b1325b40")).asDoc();
        //Document userPrabhasKejriwal = PersonApi.personById(new ObjectId("56f124dfd5d8ad25b1325b3e")).asDoc();
        HttpServletRequest request = TestUtils.postRequest(userSanjayDasgupta);
        HashMap parameterMap = new HashMap<String, String[]>();
        parameterMap.put("process_id", new String[] {"5c966923cd36dc27f078e98f"});
        parameterMap.put("label", new String[] {"Condition"});
        parameterMap.put("bpmn_name", new String[] {"Phase-With-Variables-and-Timers"});
        parameterMap.put("value", new String[] {"true"});
        when(request.getParameterMap()).thenReturn(parameterMap);
        HttpServletResponse response = mock(HttpServletResponse.class);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        new VariableValueSet().doPost(request, response);

        verify(request, atLeast(1)).getParameterMap();
        verify(request, atLeast(1)).getSession();
        verify(request, atLeast(1)).getHeader("X-FORWARDED-FOR");
        verify(request, atLeast(0)).getHeader("User-Agent");
        verify(request.getSession(), atLeast(1)).getAttribute("bw-user");
        writer.flush(); // it may not have been flushed yet...
        String output = stringWriter.toString();
        //assertTrue(output.startsWith("[{"));
        //assertTrue(output.endsWith("}]"));
    }

}