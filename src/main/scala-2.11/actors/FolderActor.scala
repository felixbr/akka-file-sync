package actors

import java.nio.file.{Path, Paths}

import actors.FolderActor._
import actors.RemoteCommunicationActor.FilesNeededFromRemote
import akka.actor._
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl._
import domain.Types.{FolderContent, FolderId, FolderPathAbs}
import domain._
import io.FileEventIterator
import stream.Debounce
import util.FolderUtils

import scala.concurrent.duration._


object FolderActor {
  def props(folderId: FolderId, dir: FolderPathAbs) = Props(new FolderActor(folderId, dir))

  sealed trait Query
  case object RetrieveFolderContents extends Query
  case class CheckFolderDiff(comHub: String, remoteFiles: FolderContent) extends Query
  case class FileExists(fileName: String, checksum: String) extends Query

  sealed trait Answer
  case class FilesInFolder(comHub: String, folderId: FolderId, files: FolderContent) extends Answer

  sealed trait Command
  case class HandleFileEvent(eventType: FileEventType, filePath: Path, checksum: String) extends Command

  def fileEventSource(dir: String) = {
    val fileEventIterator = new FileEventIterator(Paths.get(dir))

    Source[StreamEvent](() => fileEventIterator)
  }

  def nameFromFolderId(id: FolderId): String = s"$id-folderActor"
}

class FolderActor(folderId: String, dir: String) extends Actor with ActorLogging {
  implicit val mat = ActorFlowMaterializer()

  log.info(s"folderActor startet for: $folderId -> $dir")

  val localIP = "127.0.0.1"
  val localAddress = Address("akka.tcp", "filesync", localIP, 2552)
  val comHub = self.path.parent.name
  println(s"$comHub: ${self.path}")

  startFileEventStream()
  broadcastFilesInFolder()

  override def receive: Receive = {
    case RetrieveFolderContents =>
      sender() ! FilesInFolder(comHub, folderId, FolderUtils.filesInDir(dir))

    case CheckFolderDiff(remoteComHub, remoteChecksums) =>
      val localChecksums = FolderUtils.filesInDir(dir)
      val remoteFilesMissingLocal = FolderUtils.remoteFilesMissingLocallyByRelPath(localChecksums, remoteChecksums)

//      log.debug(s"localChecksums: $localChecksums")
//      log.debug(s"remoteChecksums: $remoteChecksums")

      if (remoteFilesMissingLocal.nonEmpty) {
        log.debug(s"remote files missing: $remoteFilesMissingLocal")
        context.parent ! FilesNeededFromRemote(remoteComHub, localAddress, folderId, remoteFilesMissingLocal)
      } else {
        log.debug("no remote files are missing")
      }

    case FileExists(name, checksum) =>
      sender() ! FolderUtils.filesInDir(dir).exists(_.fileName == name)

    case HandleFileEvent =>
//      context.parent ! FolderChanged(localAddress, folderId)
      broadcastFilesInFolder()
  }

  def broadcastFilesInFolder() = {
    log.debug("sending broadcast: FilesInFolder")
    context.actorSelection("akka://filesync/user/com*") ! FilesInFolder(comHub, folderId, FolderUtils.filesInDir(dir))
  }

  def triggerFileEvent(event: FileEvent) = {
    log.debug(s"triggering file event: $event")
    self ! HandleFileEvent(event.eventType, event.filePath, event.checksum)
  }

  def startFileEventStream() = {
    val g = FlowGraph.closed() { implicit builder =>
      import FlowGraph.Implicits._

      val tickSource = Source[StreamEvent](initialDelay = 1.seconds, interval = 1.seconds, Tick)

//      val streamDelay = Flow[FileEvent].map(e => Future { Thread.sleep(2.seconds.toMillis); e })

      val merge = builder.add(Merge[StreamEvent](2))

      val streamDebounce = Flow[StreamEvent].transform(Debounce(5.seconds))

      val streamFilter = Flow[StreamEvent].filter(_.isInstanceOf[FileEvent]).map[FileEvent](_.asInstanceOf[FileEvent])

      val sink = Sink.foreach[FileEvent](_ => broadcastFilesInFolder())

                            tickSource ~> merge
      FolderActor.fileEventSource(dir) ~> merge ~> streamDebounce ~> streamFilter ~> sink
    }
    g.run()
  }
}