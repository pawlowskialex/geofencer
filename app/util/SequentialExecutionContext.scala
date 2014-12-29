package util

/**
 * Created by alex on 12/24/14.
 */

import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

/**
 * Queues the operations to run in sequence, regardless of the nature of
 * the underlying thread pool.
 */
class SequentialExecutionContext private(ec: ExecutionContext) extends ExecutionContext {
  val queue = new AtomicReference[Future[Unit]](Future.successful(()))

  def execute(runnable: Runnable): Unit = {
    val p = Promise[Unit]()

    @tailrec
    def add(): Future[_] = {
      val tail = queue get()

      if (!queue.compareAndSet(tail, p.future)) add()
      else tail
    }

    add().onComplete(_ => p complete Try(runnable.run()))(ec)
  }

  def reportFailure(cause: Throwable): Unit = ec reportFailure cause
}

object SequentialExecutionContext {
  def apply(ec: ExecutionContext) = new SequentialExecutionContext(ec)
}