import java.nio.file.Path

package domain {
  object Types {
    type FolderId = String
    type FolderPathAbs = String

    type FolderContent = List[FileChecksum]
  }

  case class FileChecksum(relPath: String, absPath: String, fileName: String, checksum: String)


  sealed trait StreamEvent
  case class FileEvent(eventType: FileEventType, filePath: Path, checksum: String) extends StreamEvent
  case object Tick extends StreamEvent

  sealed trait FileEventType
  case object FileCreated extends FileEventType
  case object FileChanged extends FileEventType
  case object FileDeleted extends FileEventType
}
