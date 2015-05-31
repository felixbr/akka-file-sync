package actors

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging}

class DelayActor(delay: Long) extends Actor with ActorLogging{
  private var lastMessageTimestamp: Option[Long] = None
  private var lastMessage: Option[AnyVal] = None

  override def receive: Receive = {
    case message =>
  }


}
