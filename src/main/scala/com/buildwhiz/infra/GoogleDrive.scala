package com.buildwhiz.infra

import com.buildwhiz.infra.BWMongoDB3._
import org.bson.Document
import org.bson.types.ObjectId
import java.io.{InputStream, InputStreamReader}

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.BWLogger
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

import java.io.{FileInputStream, File => javaFile}

import com.google.api.client.http.FileContent

import scala.collection.JavaConverters._

object GoogleDrive {
  private val storageFolderContainerId = "12b05HM6OzkAXNnnxIu3jMV3XBjq6Pey_"

  private val jsonFactory = JacksonFactory.getDefaultInstance
  private val SCOPES = Seq(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_METADATA).asJava
  //private val SCOPES = Seq(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE, DriveScopes.DRIVE_METADATA).asJava
  //private val SCOPES = Collections.singletonList(DriveScopes.DRIVE)

  private def getCredentials(httpTransport: NetHttpTransport): Credential = {
    BWLogger.log(getClass.getName, "getCredentials()", s"ENTRY")
    val tomcatDir = new javaFile("server").listFiles.filter(_.getName.startsWith("apache-tomcat-")).head
    val doNotTouchFolder = new javaFile(tomcatDir, "webapps/bw-dot-2.01/WEB-INF/classes/do-not-touch")
    if (!doNotTouchFolder.exists()) {
      val message = s"No such file: '${doNotTouchFolder.getAbsolutePath}'"
      BWLogger.log(getClass.getName, "getCredentials()", s"ERROR ($message)")
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
      BWLogger.log(getClass.getName, "getCredentials()", s"ERROR ($message)")
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
    BWLogger.log(getClass.getName, "getCredentials()", s"EXIT-OK")
    credential
  }

  private def driveService(): Drive = {
    BWLogger.log(getClass.getName, "driveService()", s"ENTRY")
    val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    val drive = new Drive.Builder(httpTransport, jsonFactory, getCredentials(httpTransport)).
        setApplicationName("GoogleDrive-Connector").build()
    BWLogger.log(getClass.getName, "driveService()", s"EXIT-OK")
    drive
  }

  BWLogger.log(getClass.getName, "Storing 'cachedDriveService'", s"ENTRY")
  private lazy val cachedDriveService: Drive = driveService()
  BWLogger.log(getClass.getName, "Storing 'cachedDriveService'", s"EXIT-OK")

  private def fetchStorageFolderId(): String = {
    BWLogger.log(getClass.getName, "fetchStorageFolderId()", s"ENTRY")
    val info: DynDoc = BWMongoDB3.instance_info.find().head
    val storageFolderName = if (info.has("storage_folder_name")) {
      info.storage_folder_name[String]
    } else {
      val message = "Not found 'storage_folder_name' in instance-info"
      BWLogger.log(getClass.getName, "fetchStorageFolderId()", s"ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val storageFolderSearchResult = cachedDriveService.files().list().setPageSize(10).
        setFields("nextPageToken, files(id, name, size, mimeType, createdTime, modifiedTime)").
        setQ(s"\'$storageFolderContainerId\' in parents and name = '$storageFolderName' and trashed = false").execute()
    val storageFolderCandidates: Seq[File] = storageFolderSearchResult.getFiles.iterator().asScala.toSeq
    if (storageFolderCandidates.length != 1) {
      val message = s"Found ${storageFolderCandidates.length} folders named '$storageFolderName'"
      BWLogger.log(getClass.getName, "fetchStorageFolderId()", s"ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val storageFolder = storageFolderCandidates.head
    if (storageFolder.getMimeType != "application/vnd.google-apps.folder") {
      val message = s"Entry named '$storageFolderName' is not a folder"
      BWLogger.log(getClass.getName, "fetchStorageFolderId()", s"ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val theStorageFolderId = storageFolder.getId
    BWLogger.log(getClass.getName, "fetchStorageFolderId()", s"EXIT-OK ('$storageFolderName' -> '$theStorageFolderId')")
    theStorageFolderId
  }

  BWLogger.log(getClass.getName, "Storing 'storageFolderId'", s"ENTRY")
  private lazy val storageFolderId: String = fetchStorageFolderId()
  BWLogger.log(getClass.getName, "Storing 'storageFolderId'", s"EXIT")

  // https://developers.google.com/drive/api/v3/reference/files/list
  // https://developers.google.com/drive/api/v3/ref-search-terms
  // https://developers.google.com/drive/api/v3/reference/files
  // https://developers.google.com/drive/api/v3/folder#create

  def listObjects(): Seq[FileMetadata] = {
    BWLogger.log(getClass.getName, "listObjects()", s"ENTRY")
    val result = cachedDriveService.files().list().setPageSize(10).
        setFields("nextPageToken, files(id, name, size, mimeType, createdTime, modifiedTime)").
        setQ(s"\'$storageFolderId\' in parents and trashed = false").execute()
    val files: Seq[File] = result.getFiles.iterator().asScala.toSeq
    val objects = files.map(FileMetadata.fromFile)
    BWLogger.log(getClass.getName, "listObjects()", s"EXIT-OK (${objects.length} objects)")
    objects
  }

  def listObjects(prefix: String): Seq[FileMetadata] = listObjects().filter(_.key.startsWith(prefix))

  def deleteObject(key: String): Unit = {
    BWLogger.log(getClass.getName, "deleteObject()", s"ENTRY (key: '$key')")
    BWLogger.log(getClass.getName, "deleteObject()", s"EXIT-OK (NoOp - Nothing deleted)")
  }

  def putObject(key: String, file: javaFile): FileMetadata = {
    BWLogger.log(getClass.getName, s"putObject(key: $key, size: ${file.length})", s"ENTRY")
    val namedFiles = cachedDriveService.files().list.
        setQ(s"\'$storageFolderId\' in parents and name = '$key' and trashed = false").
        execute().getFiles
    if (namedFiles.nonEmpty) {
      val message = s"File named '$key' already exists"
      BWLogger.log(getClass.getName, "putObject()", s"ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val metadata = new File().setName(key).setParents(Seq(storageFolderId).asJava)
    val fileContent = new FileContent("application/octet-stream", file)
    val newFile = cachedDriveService.files().create(metadata, fileContent).execute()
    BWLogger.log(getClass.getName, "putObject()",
        s"EXIT-OK (created fileId: '${newFile.getId}', type: '${newFile.getMimeType}', size: ${newFile.getSize})")
    FileMetadata.fromFile(newFile)
  }

  def getObject(key: String): InputStream = {
    BWLogger.log(getClass.getName, "getObject()", s"ENTRY")
    val namedFiles = cachedDriveService.files().list.
        setQ(s"\'$storageFolderId\' in parents and name = '$key' and trashed = false").execute().getFiles
    val theFile = if (namedFiles.length == 1) {
      namedFiles.head
    } else {
      val message = s"Found ${namedFiles.length} files named '$key'"
      BWLogger.log(getClass.getName, "getObject()", s"ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val inputStream: InputStream = cachedDriveService.files().get(theFile.getId).executeMediaAsInputStream()
    BWLogger.log(getClass.getName, "getObject()",
        s"EXIT-OK (fileId: '${theFile.getId}', type: '${theFile.getMimeType}', size: ${theFile.getSize})")
    inputStream
  }
//
//  case class Summary(total: Int, orphans: Int, totalSize: Long, smallest: Long, biggest: Long, earliest: Long, latest: Long)
//
  def isOrphan(key: String): Boolean = {
    try {
      val parts = key.split("-")
      val (projOid, docOid, timestamp) = (new ObjectId(parts(0)), new ObjectId(parts(1)),
        java.lang.Long.parseLong(parts(2), 16))
      val doc: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> docOid, "project_id" -> projOid)).head
      val versions: Seq[DynDoc] = doc.versions[Many[Document]]
      !versions.exists(_.timestamp[Long] == timestamp)
    } catch {
      case _: Throwable => true
    }
  }
//
//  def listOrphans: Seq[String] = {
//    val lor = new ListObjectsV2Request()
//    lor.setRequestCredentialsProvider(credentialsProvider)
//    lor.setBucketName(bucketName)
//    @tailrec
//    def once(acc: Seq[String] = Nil): Seq[String] = {
//      val listing = s3Client.listObjectsV2(lor)
//      val keys = listing.getObjectSummaries.asScala.map(_.getKey)
//      val orphans = keys.filter(isOrphan) ++ acc
//      if (listing.isTruncated) {
//        lor.setContinuationToken(listing.getContinuationToken)
//        once(orphans)
//      } else
//        orphans
//    }
//    once()
//  }
//
//  def getSummary: Summary = {
//    val lor = new ListObjectsV2Request()
//    lor.setRequestCredentialsProvider(credentialsProvider)
//    lor.setBucketName(bucketName)
//    def getStats(acc: Summary = Summary(0, 0, 0, Long.MaxValue, 0, Long.MaxValue, 0)): Summary = {
//      def combine(sum: Summary, s3: S3ObjectSummary): Summary = {
//        val key = s3.getKey
//        val newOrphanCount = sum.orphans + (if (isOrphan(key)) 1 else 0)
//        val size = s3.getSize
//        val newTotalSize = size + sum.totalSize
//        val total = sum.total + 1
//        val millis = s3.getLastModified.getTime
//        val earliest = math.min(millis, sum.earliest)
//        val latest = math.max(millis, sum.latest)
//        val smallest = math.min(size, sum.smallest)
//        val biggest = math.max(size, sum.biggest)
//        Summary(total, newOrphanCount, newTotalSize, smallest, biggest, earliest, latest)
//      }
//      val listing = s3Client.listObjectsV2(lor)
//      val summaries = listing.getObjectSummaries
//      val newMap: Summary = summaries.asScala.foldLeft(acc)(combine)
//      if (listing.isTruncated) {
//        lor.setContinuationToken(listing.getContinuationToken)
//        getStats(newMap)
//      }
//      newMap
//    }
//    getStats()
//  }
//
//  def main(args: Array[String]): Unit = {
//    val orphans = listOrphans
//    println(orphans.length)
//    println(orphans)
//  }
}