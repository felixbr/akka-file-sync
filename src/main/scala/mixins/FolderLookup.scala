package mixins

import actors.RemoteCommunicationActor.{FolderFromId, FolderPath}
import akka.actor.{Actor, ActorContext, ActorLogging}
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._

trait FolderLookup { this: Actor with ActorLogging =>

  def queryForLocalFolder(folderId: String, comHub: String)(implicit ctx: ActorContext): Future[String] = {
    implicit val timeout = Timeout(3.seconds)

    val localFolderActor = ctx.actorSelection(s"/user/$comHub")
    log.debug(s"localFolderActor: $localFolderActor")

    val response = localFolderActor ? FolderFromId(folderId)
    response.onFailure {
      case e: AskTimeoutException => log.error(s"FolderFromId request timed out: ${e.getMessage}")
      case e: Throwable => throw e
    }
    response.map {
      case FolderPath(path) => path
    }
  }
}
