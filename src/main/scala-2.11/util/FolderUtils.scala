package util

import java.io.{File, FileNotFoundException}
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import com.google.common.hash.Hashing
import com.google.common.io
import domain.FileChecksum
import domain.Types.FolderContent

import scala.util.Try

object FolderUtils {
  def filesInDir(dir: String): FolderContent = {
    val dirPath = Paths.get(dir)
    require(Files.isDirectory(dirPath))

    var fileList = List[FileChecksum]()

    class CustomFileVisitor extends SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        val relPath = dirPath.relativize(file)
        fileList = FileChecksum(relPath.toString, file.toAbsolutePath.toString, file.getFileName.toString, fileChecksum(file.toAbsolutePath.toString)) :: fileList

        super.visitFile(file, attrs)
      }
    }

    Files.walkFileTree(dirPath, new CustomFileVisitor)

    fileList.filterNot { case FileChecksum(_, path, _, _) => Files.isDirectory(Paths.get(path)) }
  }

  def fileChecksum(filePath: String, retries: Int = 3): String = {
    val result = Try(io.Files.hash(new File(filePath), Hashing.md5()).toString) recover {
      case e: FileNotFoundException => if (retries > 0) {
        Thread.sleep(50)
        fileChecksum(filePath, retries = retries - 1)
      } else {
        throw e
      }
    }

    result.getOrElse("")
  }

  def remoteFilesMissingLocallyByRelPath(localFolder: FolderContent, remoteFolder: FolderContent): FolderContent = {
    remoteFolder.filterNot { remoteFC => localFolder.exists(_.relPath == remoteFC.relPath) }
  }
}
