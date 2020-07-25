package com.buildwhiz.tools.scripts

import java.io.{InputStream, InputStreamReader, PrintWriter}

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.utils.HttpUtils
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.DriveScopes
import java.util.Collections

import scala.collection.JavaConverters._
import java.io.{File => javaFile}


object MigrateAwsS3DataToGoogleDrive extends HttpUtils {

  private val jsonFactory = JacksonFactory.getDefaultInstance
  private val SCOPES = Collections.singletonList(DriveScopes.DRIVE_METADATA_READONLY)

  private def getCredentials(httpTransport: NetHttpTransport, credentialStream: InputStream, respWriter: PrintWriter):
      Credential = {
    respWriter.println("ENTRY getCredentials")
    val clientSecrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(credentialStream))
    respWriter.println("Got Client-Secrets")
    val builder = new GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, clientSecrets, SCOPES)
    respWriter.println("Got Builder")
    val tomcatDir = new javaFile("server").listFiles.filter(_.getName.startsWith("apache-tomcat-")).head
    val tokensFile = new javaFile(tomcatDir, "webapps/bw-dot-2.01/WEB-INF/classes/do-not-touch/tokens")
    val tokensFileExists = tokensFile.exists()
    respWriter.println(s"Tokens file exists: $tokensFileExists")
    val flow = builder.setDataStoreFactory(new FileDataStoreFactory(tokensFile)).setAccessType("offline").build()
    respWriter.println("Got Flow")
    val receiver = new LocalServerReceiver.Builder().setPort(8888).build()
    respWriter.println("Got Receiver")
    val credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    respWriter.println("EXIT getCredentials")
    credential
  }

  def checkDriveAccess(credentials: InputStream, respWriter: PrintWriter): Unit = {
    respWriter.println("ENTRY checkDriveAccess")
    val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    respWriter.println("Got httpTransport")
    val service = new Drive.Builder(httpTransport, jsonFactory, getCredentials(httpTransport, credentials, respWriter)).
        setApplicationName("Google Drive API Java Quickstart").build()
    respWriter.println("Got service")
    val result = service.files().list().setQ("\'12vpPmRRS750v1chrr3z7E0jyd8jcCZvi\' in parents").
        setPageSize(10).setFields("nextPageToken, files(id, name, size, mimeType)").execute()
    respWriter.println("Got result")
    val files: Iterator[File] = result.getFiles.iterator().asScala
    respWriter.println("Got files")
    for (file <- files) {
      val id = file.getId
      val name = file.getName
      val size = file.getSize
      val mimeType = file.getMimeType
      respWriter.println(s"\t$id: $name ($size) $mimeType")
    }
    respWriter.println("EXIT checkDriveAccess")
  }

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    val respWriter = response.getWriter
    respWriter.println("Starting Credentials Check")

    try {
      val user: DynDoc = getUser(request)
      if (!PersonApi.isBuildWhizAdmin(Right(user))) {
        respWriter.println("Only Admins are permitted")
        throw new IllegalArgumentException("Not permitted")
      }

      val token = getClass.getResourceAsStream("/do-not-touch/tokens/StoredCredential")
      val credentials = getClass.getResourceAsStream("/do-not-touch/drive-storage-demo.json")
      respWriter.println(s"credentials: $credentials, token: $token")

      if (credentials != null && token != null) {
        checkDriveAccess(credentials, respWriter)
      }
    } catch {
      case t: Throwable =>
        respWriter.println(s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        //BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
