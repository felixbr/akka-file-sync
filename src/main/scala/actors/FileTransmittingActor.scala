package actors

import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files

import actors.FileTransmittingActor.{Listening, StartListening}
import akka.actor._
import akka.io.Tcp.{Bind, _}
import akka.io.{IO, Tcp}
import mixins.FolderLookup

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

object FileTransmittingActor {
  def props(folderId: String, relPath: String, comHub: String) = Props(new FileTransmittingActor(folderId, relPath, comHub))

  sealed trait Command
  case class StartListening(receiverRef: ActorRef) extends Command

  sealed trait Answers
  case class Listening(host: String, port: Int, fileSize: Long) extends Answers
}

class FileTransmittingActor(folderId: String, relPath: String, comHub: String) extends Actor with ActorLogging with FolderLookup {
  implicit val system = context.system

  val file: Future[File] = for (
    folderPath <- queryForLocalFolder(folderId, comHub);
    filePath = new File(folderPath).toPath.resolve(relPath)
  ) yield {
    log.debug(s"localFolder: $folderPath")
    log.debug(s"relPath: $relPath")
    log.debug(s"filePath: ${filePath.toString}")
    new File(filePath.toString)
  }

  val fileSize: Future[Long] = file.map { file =>
    Files.readAttributes(file.toPath, "size").get("size").asInstanceOf[Long]
  }

  val host = "0.0.0.0"
  val port = 12345
  IO(Tcp) ! Bind(self, new InetSocketAddress(host, port))

  override def receive: Receive = {
    case StartListening(receiverRef) =>
      for (
        f      <- file;
        length <- fileSize
      ) {
        log.debug(s"listening on $host:$port and waiting for $receiverRef")
        receiverRef ! Listening(host, port, length)
      }

    case Connected(remote, local) =>
      log.debug("connected from server")

      val handler = self
      val connection = sender()
      connection ! Register(handler)

      for (
        f      <- file;
        length <- fileSize
      ) {

        log.info(s"writing file to wire: ${f.getAbsolutePath} ($length bytes)")
        connection ! Tcp.WriteFile(f.getAbsolutePath, 0, length, Tcp.NoAck)
      }

    case Bound(localAddress) =>
    case CommandFailed(_: Bind) => context stop self
    case a => log.debug(a.toString)
  }
}
