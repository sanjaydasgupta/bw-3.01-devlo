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
import com.google.api.services.drive.model.Permission
import java.io.{FileInputStream, File => javaFile}
import java.util.Collections

import com.google.api.client.http.FileContent

import scala.collection.JavaConverters._
import scala.annotation.tailrec

object GoogleDrive {
  private val storageFolderContainerId = "12b05HM6OzkAXNnnxIu3jMV3XBjq6Pey_"

  private val jsonFactory = JacksonFactory.getDefaultInstance
  private val SCOPES = Seq(DriveScopes.DRIVE, DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_METADATA).asJava
  //private val SCOPES = Seq(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE, DriveScopes.DRIVE_METADATA).asJava
  //private val SCOPES = Collections.singletonList(DriveScopes.DRIVE)

  private def getCredentials(httpTransport: NetHttpTransport): Credential = {
    BWLogger.log(getClass.getName, "getCredentials()", s"ENTRY")
    val tomcatDir = new javaFile("server").listFiles.filter(_.getName.startsWith("apache-tomcat-")).head
    val doNotTouchFolder = new javaFile(tomcatDir, "webapps/bw-3.01/WEB-INF/classes/do-not-touch")
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
    val query = Seq(s"\'$storageFolderContainerId\' in parents", s"name = '$storageFolderName'", "trashed = false",
      "mimeType = \'application/vnd.google-apps.folder\'").mkString(" and ")
    val storageFolderSearchResult = cachedDriveService.files().list().
        setFields("nextPageToken, files(id, name, size, mimeType, createdTime, modifiedTime)").
        setQ(query).execute()
    val storageFolderCandidates: Seq[File] = storageFolderSearchResult.getFiles.iterator().asScala.toSeq
    if (storageFolderCandidates.length != 1) {
      val message = s"Found ${storageFolderCandidates.length} folders named '$storageFolderName'"
      BWLogger.log(getClass.getName, "fetchStorageFolderId()", s"ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val storageFolder = storageFolderCandidates.head
    val theStorageFolderId = storageFolder.getId
    BWLogger.log(getClass.getName, "fetchStorageFolderId()", s"EXIT-OK ('$storageFolderName' -> '$theStorageFolderId')")
    theStorageFolderId
  }

  BWLogger.log(getClass.getName, "Storing 'storageFolderId'", s"ENTRY")
  private lazy val storageFolderId: String = fetchStorageFolderId()
  BWLogger.log(getClass.getName, "Storing 'storageFolderId'", s"EXIT")

  private def fetchChildFolderId(folderName: String): String = {
    BWLogger.log(getClass.getName, "fetchChildFolderId()", s"ENTRY")
    val query = Seq(s"\'$storageFolderId\' in parents", s"name = '$folderName'", "trashed = false",
      "mimeType = \'application/vnd.google-apps.folder\'").mkString(" and ")
    val fileFolderSearchResult = cachedDriveService.files().list().
        setFields("nextPageToken, files(id, name, size, mimeType, createdTime, modifiedTime)").
        setQ(query).execute()
    val fileFolderCandidates: Seq[File] = fileFolderSearchResult.getFiles.iterator().asScala.toSeq
    if (fileFolderCandidates.length != 1) {
      val message = s"Found ${fileFolderCandidates.length} folders named 'files'"
      BWLogger.log(getClass.getName, "fetchChildFolderId()", s"ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val filesFolder = fileFolderCandidates.head
    val theFilesFolderId = filesFolder.getId
    BWLogger.log(getClass.getName, "fetchChildFolderId()", s"EXIT-OK ('files' -> '$theFilesFolderId')")
    theFilesFolderId
  }

  BWLogger.log(getClass.getName, "Storing 'filesFolderId'", s"ENTRY")
  private lazy val filesFolderId: String = fetchChildFolderId("files")
  BWLogger.log(getClass.getName, "Storing 'filesFolderId'", s"EXIT")

  BWLogger.log(getClass.getName, "Storing 'usersFolderContainerId'", s"ENTRY")
  private lazy val usersFolderContainerId: String = fetchChildFolderId("users")
  BWLogger.log(getClass.getName, "Storing 'usersFolderContainerId'", s"EXIT")

  private val permissionAnyoneReader = new Permission().setId("AnyoneReader").setType("anyone").setRole("reader")

  def getUserFolderId(gDrivefolderName: String): String = {
    BWLogger.log(getClass.getName, s"getUserFolderId($gDrivefolderName)", s"ENTRY")
    val userFolderId = getOrCreateFolder(usersFolderContainerId, gDrivefolderName)
    val permissionRequest = new Permission().setType("anyone").setRole("reader").setAllowFileDiscovery(false)
    val permissionGrant = cachedDriveService.permissions().create(userFolderId, permissionRequest).execute()
    BWLogger.log(getClass.getName, s"getUserFolderId($gDrivefolderName)", s"EXIT-OK ($userFolderId)")
    userFolderId
  }

  def getOrCreateFolder(parentFolderId: String, folderName: String): String = {
    BWLogger.log(getClass.getName, s"getOrCreateFolder($parentFolderId, $folderName)", s"ENTRY")
    val query = Seq(s"\'$parentFolderId\' in parents", s"name = '$folderName'", "trashed = false",
      "mimeType = \'application/vnd.google-apps.folder\'").mkString(" and ")
    val searchResult = cachedDriveService.files().list().
        setFields("nextPageToken, files(id, name, size, mimeType, createdTime, modifiedTime)").
        setQ(query).execute()
    val fileFolderCandidates: Seq[File] = searchResult.getFiles.iterator().asScala.toSeq
    if (fileFolderCandidates.nonEmpty) {
      val folderId = fileFolderCandidates.head.getId
      BWLogger.log(getClass.getName, s"getOrCreateFolder($parentFolderId, $folderName)",
          s"EXIT-OK (1 of ${fileFolderCandidates.length}: $folderId)")
      folderId
    } else {
      val newFolderMetadata = new File().setParents(Collections.singletonList(parentFolderId)).
          setMimeType("application/vnd.google-apps.folder").setName(folderName)
      val newFolder = cachedDriveService.files().create(newFolderMetadata).setFields("id, parents").
          execute()
      val permissionRequest = new Permission().setType("anyone").setRole("reader").setAllowFileDiscovery(false)
      val permissionGrant = cachedDriveService.permissions().create(newFolder.getId, permissionRequest).execute()
      BWLogger.log(getClass.getName, s"getOrCreateFolder($parentFolderId, $folderName)", s"EXIT-OK (new: $newFolder)")
      newFolder.getId
    }
  }

  // https://developers.google.com/drive/api/v3/reference/files/list
  // https://developers.google.com/drive/api/v3/ref-search-terms
  // https://developers.google.com/drive/api/v3/reference/files
  // https://developers.google.com/drive/api/v3/folder#create
  // https://developers.google.com/drive/api/v3/reference/files/update
  // https://developers.google.com/drive/api/v3/reference/permissions

  private val pageSize = 100

  def listObjects(optPrefix: Option[String] = None): Seq[FileMetadata] = {
    BWLogger.log(getClass.getName, "listObjects()", s"ENTRY")
    val query = optPrefix match {
      case None => s"\'$filesFolderId\' in parents and trashed = false"
      case Some(prefix) => s"\'$filesFolderId\' in parents and trashed = false and name contains '$prefix'"
    }
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
    BWLogger.log(getClass.getName, "listObjects()", s"EXIT-OK (${objects.length} objects)")
    objects
  }

  def deleteObject(key: String): Unit = {
    BWLogger.log(getClass.getName, s"deleteObject($key)", s"ENTRY (key: '$key')")
    BWLogger.log(getClass.getName, s"deleteObject($key)", s"EXIT-OK (NoOp - Nothing deleted)")
  }

  def putObject(key: String, file: javaFile, properties: Map[String, String] = Map.empty): FileMetadata = {
    BWLogger.log(getClass.getName, s"putObject(key: $key, size: ${file.length})", s"ENTRY")
    val namedFiles = cachedDriveService.files().list.
        setQ(s"\'$filesFolderId\' in parents and name = '$key' and trashed = false").
        execute().getFiles
    if (namedFiles.nonEmpty) {
      val message = s"File named '$key' already exists"
      BWLogger.log(getClass.getName, s"putObject(key: $key, size: ${file.length})", s"ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val metadata = new File().setName(key).setParents(Seq(filesFolderId).asJava).setProperties(properties.asJava)
    val fileContent = new FileContent("application/octet-stream", file)
    val newFile = cachedDriveService.files().create(metadata, fileContent).execute()
    BWLogger.log(getClass.getName, s"putObject(key: $key, size: ${file.length})",
        s"EXIT-OK (created fileId: '${newFile.getId}', type: '${newFile.getMimeType}', size: ${newFile.getSize})")
    FileMetadata.fromFile(newFile).copy(size = file.length())
  }

  def getObject(key: String): InputStream = {
    BWLogger.log(getClass.getName, s"getObject($key)", s"ENTRY")
    val namedFiles = cachedDriveService.files().list.
        setQ(s"\'$filesFolderId\' in parents and name = '$key' and trashed = false").execute().getFiles
    val theFile = if (namedFiles.length == 1) {
      namedFiles.head
    } else {
      val message = s"Found ${namedFiles.length} files named '$key'"
      BWLogger.log(getClass.getName, s"getObject($key)", s"ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val inputStream: InputStream = cachedDriveService.files().get(theFile.getId).executeMediaAsInputStream()
    BWLogger.log(getClass.getName, s"getObject($key)",
        s"EXIT-OK (fileId: '${theFile.getId}', mime-type: '${theFile.getMimeType}', size: ${theFile.getSize})")
    inputStream
  }

  def updateObjectById(id: String, properties: Map[String, String]): Unit = {
    BWLogger.log(getClass.getName, s"updateObjectById(id: $id, properties: $properties)", s"ENTRY")
    val propertyMetadata = new File().setProperties(properties.asJava)
    val updatedFile = cachedDriveService.files().update(id, propertyMetadata).execute()
    if (id != updatedFile.getId) {
      BWLogger.log(getClass.getName, s"updateObjectById(id: $id, properties: $properties)",
        s"EXIT-ERROR (DIFFERENT fileIds: [$id, ${updatedFile.getId}], key: '${updatedFile.getName}')")
    } else {
      BWLogger.log(getClass.getName, s"updateObjectById(id: $id, properties: $properties)",
        s"EXIT-OK (id: '$id', key: '${updatedFile.getName}')")
    }
  }

  def updateObjectByKey(key: String, properties: Map[String, String]): Unit = {
    BWLogger.log(getClass.getName, s"updateObjectByKey(key: $key, properties: $properties)", s"ENTRY")
    val namedFiles = cachedDriveService.files().list.
        setQ(s"\'$filesFolderId\' in parents and name = '$key' and trashed = false").
        execute().getFiles
    val theFile = if (namedFiles.length == 1) {
      namedFiles.head
    } else {
      val message = s"Found ${namedFiles.length} files named '$key'"
      BWLogger.log(getClass.getName, s"updateObjectByKey(key: $key, properties: $properties)", s"ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val propertyMetadata = new File().setProperties(properties.asJava)
    val updatedFile = cachedDriveService.files().update(theFile.getId, propertyMetadata).execute()
    if (theFile.getId != updatedFile.getId)
      BWLogger.log(getClass.getName, s"updateObjectByKey(key: $key, properties: $properties)",
        s"EXIT-ERROR (DIFFERENT fileIds: [${theFile.getId}, ${updatedFile.getId}], key: '${updatedFile.getName}')")
    else
      BWLogger.log(getClass.getName, s"updateObjectByKey(key: $key, properties: $properties)",
        s"EXIT-OK (fileId: '${theFile.getId}', key: '${updatedFile.getName}')")
  }

  case class Summary(total: Int, orphans: Int, totalSize: Long, smallest: Long, biggest: Long,
      earliest: Long, latest: Long)

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
  def getSummary: Summary = {
    def getStats(acc: Summary = Summary(0, 0, 0, Long.MaxValue, 0, Long.MaxValue, 0)): Summary = {
      def combine(sum: Summary, file: FileMetadata): Summary = {
        val key = file.key
        val newOrphanCount = sum.orphans + (if (isOrphan(key)) 1 else 0)
        val size = file.size
        val newTotalSize = size + sum.totalSize
        val total = sum.total + 1
        val millis = file.modifiedTime
        val earliest = math.min(millis, sum.earliest)
        val latest = math.max(millis, sum.latest)
        val smallest = math.min(size, sum.smallest)
        val biggest = math.max(size, sum.biggest)
        Summary(total, newOrphanCount, newTotalSize, smallest, biggest, earliest, latest)
      }
      val listing = listObjects()
      val newMap: Summary = listing.foldLeft(acc)(combine)
      newMap
    }
    getStats()
  }

//  def main(args: Array[String]): Unit = {
//    val orphans = listOrphans
//    println(orphans.length)
//    println(orphans)
//  }
}