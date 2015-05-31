package stream

import akka.actor.ActorContext
import akka.event.Logging
import akka.stream.stage._
import domain.{FileEvent, StreamEvent, Tick}

import scala.concurrent._
import scala.concurrent.duration._

object Debounce {
  def apply[A <: StreamEvent](delay: Duration)(implicit context: ActorContext): () => PushStage[A, A] = () => new Debounce[A](delay)
}

class Debounce[A <: StreamEvent](delay: Duration)(implicit ctx: ActorContext) extends PushStage[A, A] {
  val log = Logging(ctx.system, "DebounceLogger")

  private var lastValue: Option[A] = None
  private var lastTimestamp: Long = 0

  override def onPush(elem: A, ctx: Context[A]): SyncDirective = elem.asInstanceOf[StreamEvent] match {
    case _ if ctx.isFinishing =>
      lastValue.map(ctx.pushAndFinish).getOrElse(ctx.finish())

    case Tick =>
//      log.debug(s"${System.currentTimeMillis() - lastTimestamp}")
      if (isOverDelayTime) {
        pushValue(ctx)
      } else {
        ctx.pull()
      }

    case FileEvent(_, _, _) =>
      log.debug(s"onPush: $elem")
      resetCounterWith(elem)
      ctx.pull()
  }

  def isOverDelayTime: Boolean = lastTimestamp != 0 && (System.currentTimeMillis() - lastTimestamp) > delay.toMillis

  def resetCounterWith(elem: A) = {
    if (elem.isInstanceOf[FileEvent]) {
      resetCounter()
      lastValue = Some(elem)
    }
  }

  def resetCounter() = lastTimestamp = System.currentTimeMillis()

  def pushValue(ctx: Context[A]): SyncDirective = {
    if (lastValue.isDefined) log.debug(s"pushing last value: $lastValue")

    val directive = lastValue.map(ctx.push).getOrElse(ctx.pull())
    lastValue = None
    directive
  }

  override def onUpstreamFinish(ctx: Context[A]): TerminationDirective = ctx.absorbTermination()
}

object DebounceHelper {

  // https://gist.github.com/viktorklang/5409467
  def interruptableFuture[T](fun: Future[T] => T)(implicit ex: ExecutionContext): (Future[T], () => Boolean) = {
    val p = Promise[T]()
    val f = p.future
    val lock = new Object
    var currentThread: Thread = null
    def updateCurrentThread(newThread: Thread): Thread = {
      val old = currentThread
      currentThread = newThread
      old
    }
    p tryCompleteWith Future {
      val thread = Thread.currentThread
      lock.synchronized {
        updateCurrentThread(thread)
      }
      try fun(f) finally {
        val wasInterrupted = lock.synchronized {
          updateCurrentThread(null)
        } ne thread
        //Deal with interrupted flag of this thread in desired
      }
    }

    (f, () => lock.synchronized {
      Option(updateCurrentThread(null)) exists {
        t =>
          t.interrupt()
          p.tryFailure(new CancellationException)
      }
    })
  }
}