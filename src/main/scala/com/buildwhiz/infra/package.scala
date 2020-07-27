package com.buildwhiz

package object infra {
  case class FileMetadata(key: String, size: Long, mimeType: String = null, created: Long = 0, lastModified: Long = 0)
}
