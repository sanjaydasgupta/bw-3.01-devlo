package com.buildwhiz.infra

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.BWLogger
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.{Drive, DriveScopes}
import com.google.api.services.drive.model.File

import java.io.{FileInputStream, InputStream, InputStreamReader, File => javaFile}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

object GoogleFolderAdapter {

  private val jsonFactory = JacksonFactory.getDefaultInstance
  private val SCOPES = Seq(DriveScopes.DRIVE, DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_METADATA).asJava

  // https://developers.google.com/drive/api/v3/reference/files/list
  // https://developers.google.com/drive/api/v3/ref-search-terms
  // https://developers.google.com/drive/api/v3/reference/files
  // https://developers.google.com/drive/api/v3/folder#create
  // https://developers.google.com/drive/api/v3/reference/files/update
  // https://developers.google.com/drive/api/v3/reference/permissions

  private val pageSize = 100

  private def getCredentials(httpTransport: NetHttpTransport): Credential = {
    BWLogger.log(getClass.getName, "LOCAL", "getCredentials() ENTRY")
    val tomcatDir = new javaFile("server").listFiles.filter(_.getName.startsWith("apache-tomcat-")).head
    val doNotTouchFolder = new javaFile(tomcatDir, "webapps/bw-3.01/WEB-INF/classes/do-not-touch")
    if (!doNotTouchFolder.exists()) {
      val message = s"No such file: '${doNotTouchFolder.getAbsolutePath}'"
      BWLogger.log(getClass.getName, "LOCAL", s"getCredentials() ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val credentialsFile = new javaFile(doNotTouchFolder, "drive-storage-demo.json")
    if (!credentialsFile.exists()) {
      val message = s"No such file: '${credentialsFile.getAbsolutePath}'"
      BWLogger.log(getClass.getName, "getCredentials()", s"ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val tokensFile = new javaFile(doNotTouchFolder, "tokens")
    if (!tokensFile.exists() || !tokensFile.isDirectory) {
      val message = s"No such directory: '${tokensFile.getAbsolutePath}'"
      BWLogger.log(getClass.getName, "LOCAL", s"getCredentials() ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val credentialStream = new FileInputStream(credentialsFile)
    val clientSecrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(credentialStream))
    //respWriter.println("Got Client-Secrets")
    val builder = new GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, clientSecrets, SCOPES)
    //respWriter.println("Got Builder")
    //respWriter.println(s"Tokens file exists: $tokensFileExists")
    val flow = builder.setDataStoreFactory(new FileDataStoreFactory(tokensFile)).setAccessType("offline").build()
    //respWriter.println("Got Flow")
    val receiver = new LocalServerReceiver.Builder().setPort(8888).build()
    //respWriter.println("Got Receiver")
    val credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    BWLogger.log(getClass.getName, "LOCAL", "getCredentials() EXIT-OK")
    credential
  }

  private def driveService(): Drive = {
    BWLogger.log(getClass.getName, "LOCAL", "driveService() ENTRY")
    val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    val drive = new Drive.Builder(httpTransport, jsonFactory, getCredentials(httpTransport)).
      setApplicationName("GoogleDrive-Adapter").build()
    BWLogger.log(getClass.getName, "LOCAL", "driveService() EXIT-OK")
    drive
  }

  BWLogger.log(getClass.getName, "LOCAL", "Storing 'cachedDriveService' ENTRY")
  private lazy val cachedDriveService: Drive = driveService()
  BWLogger.log(getClass.getName, "LOCAL", "Storing 'cachedDriveService' EXIT-OK")

  def listObjects(folderId: String): Seq[FileMetadata] = {
    BWLogger.log(getClass.getName, "LOCAL", "listObjects() ENTRY")
    val query = s"\'$folderId\' in parents and trashed = false"
    val listFileQuery: Drive#Files#List = cachedDriveService.files().list().setPageSize(pageSize).setQ(query).
        setFields("nextPageToken, files(id, name, size, mimeType, createdTime, modifiedTime, properties)")

    @tailrec def iteratePageQuery(query: Drive#Files#List, acc: Seq[File] = Seq.empty): Seq[File] = {
      val queryResult = query.execute()
      val files: Seq[File] = queryResult.getFiles.iterator().asScala.toSeq
      val nextPageToken = queryResult.getNextPageToken
      if (nextPageToken == null) {
        acc ++ files
      } else {
        iteratePageQuery(listFileQuery.setPageToken(nextPageToken), acc ++ files)
      }
    }

    val objects: Seq[FileMetadata] = iteratePageQuery(listFileQuery).map(FileMetadata.fromFile)
    BWLogger.log(getClass.getName, "LOCAL", "listObjects() EXIT-OK (${objects.length} objects)")
    objects
  }

  def getObject(folderId: String, key: String): InputStream = {
    BWLogger.log(getClass.getName, "LOCAL", s"getObject($key) ENTRY")
    val namedFiles = cachedDriveService.files().list.
        setQ(s"\'$folderId\' in parents and name = '$key' and trashed = false").execute().getFiles
    val theFile = if (namedFiles.length == 1) {
      namedFiles.head
    } else {
      val message = s"Found ${namedFiles.length} files named '$key'"
      BWLogger.log(getClass.getName, "LOCAL", s"getObject($key) ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val inputStream: InputStream = cachedDriveService.files().get(theFile.getId).executeMediaAsInputStream()
    BWLogger.log(getClass.getName, "LOCAL", s"getObject($key)" +
        s"EXIT-OK (fileId: '${theFile.getId}', mime-type: '${theFile.getMimeType}', size: ${theFile.getSize})")
    inputStream
  }

}