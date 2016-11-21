package com.buildwhiz.web

import javax.script._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import org.camunda.bpm.engine._

import scala.io.Source

class REPL extends HttpServlet {
  import REPL._
  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val pw = response.getWriter
    pw.println("The GET response ...")
    response.setStatus(HttpServletResponse.SC_OK)
  }

  private val printlnFunction = "function(x) {printWriter.println(x.toString() + \"<br/>\");}"

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    var input = Source.fromInputStream(request.getInputStream).getLines().mkString("\n")
    if ((input.contains("importPackage(") || input.contains("importClass(")) && 
        !input.contains("load(\"nashorn:mozilla_compat.js\");"))
      input = "load(\"nashorn:mozilla_compat.js\");\n" + input
    try {
      val jsBindings = jsEngine.getContext.getBindings(ScriptContext.GLOBAL_SCOPE)
      jsBindings.put("defaultProcessEngine", ProcessEngines.getDefaultProcessEngine)
      jsBindings.put("printWriter", response.getWriter)
      jsBindings.put("println", jsEngine.eval(printlnFunction))
      jsEngine.eval(input)
    } catch {
      case t: Throwable => response.getWriter.println(s"Exception: ${t.getClass.getSimpleName}(${t.getMessage})")
    }
    response.setStatus(HttpServletResponse.SC_OK)
  }

}

object REPL {
  val jsEngine: ScriptEngine = new ScriptEngineManager().getEngineByName("javascript")
}