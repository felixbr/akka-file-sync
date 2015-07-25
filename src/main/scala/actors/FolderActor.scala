package actors

import java.nio.file.{Path, Paths}

import actors.FolderActor._
import actors.RemoteCommunicationActor.FilesNeededFromRemote
import akka.actor.{Address, ActorLogging}
import akka.typed._
import akka.typed.ScalaDSL._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import domain.Types.{FolderContent, FolderId, FolderPathAbs}
import domain._
import io.FileEventIterator
import stream.Debounce
import util.FolderUtils

import scala.concurrent.duration._

object FolderActor {
  def props(folderId: FolderId, dir: FolderPathAbs) = Props(new FolderActor(folderId, dir))

  sealed trait FolderActorMessages

  sealed trait Query extends FolderActorMessages
  case class RetrieveFolderContents(replyTo: akka.actor.ActorRef) extends Query
  case class FileExists(fileName: String, checksum: String, replyTo: akka.actor.ActorRef) extends Query

  sealed trait Answer
  case class FilesInFolder(comHub: String, folderId: FolderId, files: FolderContent) extends Answer

  sealed trait Command extends FolderActorMessages
  case class CheckFolderDiff(comHub: String, remoteFiles: FolderContent) extends Command
  case class HandleFileEvent(eventType: FileEventType, filePath: Path, checksum: String) extends Command

  def fileEventSource(dir: String) = {
    val fileEventIterator = new FileEventIterator(Paths.get(dir))

    Source[StreamEvent](() => fileEventIterator)
  }

  def nameFromFolderId(id: FolderId): String = s"$id-folderActor"
}


class FolderActor(folderId: String, dir: String) extends ActorLogging {
  implicit val mat = ActorMaterializer()

  log.info(s"folderActor startet for: $folderId -> $dir")

  val localIP = "127.0.0.1"
  val localAddress = Address("akka.tcp", "filesync", localIP, 2552)

  def comHub(implicit ctx: akka.typed.ActorContext) = ctx.self.path.parent.name

  startFileEventStream()
  broadcastFilesInFolder()

  val folderActor = ContextAware[FolderActorMessages] { ctx =>
    Static {
      case RetrieveFolderContents(replyTo) =>
        replyTo ! FilesInFolder(comHub, folderId, FolderUtils.filesInDir(dir))

      case FileExists(name, checksum, replyTo) =>
        replyTo ! FolderUtils.filesInDir(dir).exists(_.fileName == name)

      case CheckFolderDiff(remoteComHub, remoteChecksums) =>
        val localChecksums = FolderUtils.filesInDir(dir)
        val remoteFilesMissingLocal = FolderUtils.remoteFilesMissingLocallyByRelPath(localChecksums, remoteChecksums)

        //      log.debug(s"localChecksums: $localChecksums")
        //      log.debug(s"remoteChecksums: $remoteChecksums")

        if (remoteFilesMissingLocal.nonEmpty) {
          log.debug(s"remote files missing: $remoteFilesMissingLocal")
          ctx.parent ! FilesNeededFromRemote(remoteComHub, localAddress, folderId, remoteFilesMissingLocal)
        } else {
          log.debug("no remote files are missing")
        }

      case HandleFileEvent() =>
        broadcastFilesInFolder()
    }
  }

  def broadcastFilesInFolder()(implicit ctx: ActorContext) = {
    log.debug("sending broadcast: FilesInFolder")
    ctx.actorSelection("akka://filesync/user/com*") ! FilesInFolder(comHub, folderId, FolderUtils.filesInDir(dir))
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