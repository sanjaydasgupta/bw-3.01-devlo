package com.buildwhiz.etc

import java.io.File
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWLogger
import com.buildwhiz.{DateTimeUtils, HttpUtils}

import scala.sys.process._
import com.buildwhiz.infra.{AmazonS3 => AS3}

class SystemMonitor extends HttpServlet with HttpUtils with DateTimeUtils {

  private def amazonS3(option: String, response: HttpServletResponse, tz: String): Unit = {
    option match {
      case "summary" =>
        val summary = AS3.getSummary
        val (count, orphans, size, smallest, biggest, earliest, latest) =
          (summary.total, summary.orphans, summary.totalSize, summary.smallest, summary.biggest, summary.earliest, summary.latest)
        val lines = Seq(Seq("Count", "Orphans", "Total Size (Bytes)", "Smallest (Bytes)", "Biggest (Bytes)", "Earliest Time", "Latest Time"),
          Seq(count, orphans, by3(size), by3(smallest), by3(biggest), dateTimeString(earliest, Some(tz)), dateTimeString(latest, Some(tz))))
        val json = lines.map(_.mkString("[\"", "\", \"", "\"]")).mkString("[", ", ", "]")
        response.getWriter.print(json)
      case "orphans" =>
      case _ =>
    }
    response.setContentType("application/json")
  }

  private def dfh(response: HttpServletResponse): Unit = {
    val output: String = "df -h".!!
    val lines = output.split("\n").map(_.trim).filter(_.nonEmpty)
    val fieldedLines = lines.map(_.split("\\s+"))
    val fieldedLines2 = fieldedLines.head.init +: fieldedLines.tail
    val json = fieldedLines2.map(_.mkString("[\"", "\", \"", "\"]")).mkString("[", ", ", "]")
    response.getWriter.print(json)
    response.setContentType("application/json")
  }

//  private def javaThreads(response: HttpServletResponse, tz: String): Unit = {
//    val stackTraces = Thread.getAllStackTraces.asScala.toList
//    val data: List[Seq[String]] = stackTraces./*filter(_._1.getState != Thread.State.RUNNABLE).*/
//        flatMap(t => t._2.map(st => Seq(s"${t._1.getName} (${t._1.getId})", t._1.getState, t._1.isAlive, t._1.isDaemon, st.getClassName.replaceAll("\\.", " "),
//          st.getLineNumber, st.isNativeMethod))).map(row => row.map(_.toString))
//    val lines: Seq[Seq[String]] = Seq("Name", "State", "is-Alive", "is-Daemon", "Class Name", "Line #", "is-Native") +: data
//    val json = lines.map(_.mkString("[\"", "\", \"", "\"]")).mkString("[", ", ", "]")
//    response.getWriter.print(json)
//    response.setContentType("application/json")
//  }
//
  private def javaMgmt(response: HttpServletResponse, tz: String): Unit = {
    import java.lang.management._
    val startTime = ManagementFactory.getRuntimeMXBean.getStartTime
    val systemLoadAverage = ManagementFactory.getOperatingSystemMXBean.getSystemLoadAverage
    //val memBean = ManagementFactory.getMemoryMXBean
    //val heapMemoryUsage = memBean.getHeapMemoryUsage
    //val nonHeapMemoryUsage = memBean.getNonHeapMemoryUsage
    val runtime = sys.runtime
    val processors = runtime.availableProcessors()
    val freeMemory = runtime.freeMemory()
    val maxMemory = runtime.maxMemory()
    val threadCount = Thread.activeCount()
    val lines = Seq(Seq("Start Time", "Sys Avg Load", "Max Memory", "Free Memory", "Thread Count", "Processors"),
      Seq(dateTimeString(startTime, Some(tz)), systemLoadAverage, by3(maxMemory), by3(freeMemory), threadCount, processors))
    val json = lines.map(_.mkString("[\"", "\", \"", "\"]")).mkString("[", ", ", "]")
    response.getWriter.print(json)
    response.setContentType("application/json")
  }

  private def tomcat(response: HttpServletResponse, directory: String): Unit = {
    val canonDir = new File(directory).getCanonicalFile
    val allFiles = (new File(canonDir, "..") +: canonDir.listFiles).
      sortWith((a, b) => a.getName.toLowerCase < b.getName.toLowerCase)
    val fileData = allFiles.map(file => {
      val name = if (file.isDirectory)
        file.getName
      else
        file.getName //s"""<a href=\"${file.getName}\" target=\"_blank\">${file.getName}</a>"""
      Seq(name, if (file.isDirectory) "Y" else "", if (file.isDirectory) "-" else by3(file.length))
    })
    val output = Seq("Name", "Directory", "Size") +: fileData
    val json = output.map(_.mkString("[\"", "\", \"", "\"]")).mkString("[", ", ", "]")
    //BWLogger.log(getClass.getName, "tomcat-json", json)
    response.getWriter.print(json)
    response.setContentType("application/json")
  }

  private def topbn(response: HttpServletResponse, filtered: Boolean): Unit = {
    val output: String = "top -b -n 1".!!
    val lines = output.split("\n").map(_.trim).filter(_.nonEmpty).dropWhile(line => !line.startsWith("PID"))
    val filteredLines = lines.head +: lines.tail.
        filter(line => if (filtered) line.matches("(?i).*(mongo|java).*") else true)
    val fieldedLines = filteredLines.map(_.split("\\s+"))
    val json = fieldedLines.map(_.mkString("[\"", "\", \"", "\"]")).mkString("[", ", ", "]")
    response.getWriter.print(json)
    response.setContentType("application/json")
  }

  private def vmstat(response: HttpServletResponse): Unit = {
    val output: String = "vmstat".!!
    val lines = output.split("\n").map(_.trim).tail
    val fieldedLines = lines.map(_.split("\\s+"))
    val fieldedLines2 = fieldedLines.head +: fieldedLines.tail.map(_.map(field => by3(field.toLong)))
    val json = fieldedLines2.map(_.mkString("[\"", "\", \"", "\"]")).mkString("[", ", ", "]")
    response.getWriter.print(json)
    response.setContentType("application/json")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val tomcatRe ="tomcat-(.+)".r
    val amazonS3Re ="as3-(.+)".r
    try {
      val tz = getUser(request).get("tz").asInstanceOf[String]
      parameters("command") match {
        case "dfh" => dfh(response)
        case "topbn-mj" => topbn(response, filtered = true)
        case "topbn-all" => topbn(response, filtered = false)
        case tomcatRe(dir) => tomcat(response, dir)
        case amazonS3Re(option) => amazonS3(option, response, tz)
        case "java" => javaMgmt(response, tz)
        //case "java-threads" => javaThreads(response, tz)
        case "vmstat" => vmstat(response)
        case _ =>
      }
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
