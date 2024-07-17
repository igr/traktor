package traktor

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.lang.Thread.sleep
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

/**
 * ❇️ Fleet is the main orchestrator of the Traktors.
 * It receives messages from the FleetRef and forwards them to the Traktors.
 * It also creates new Traktors when needed.
 */
class Fleet<M, T : Traktor<M, *, T>>(
	private val scope: CoroutineScope,
	private val context: CoroutineContext,
	private val receiveChannel: Channel<TraktorMessage<M>>,
	private val newTraktor: (TraktorId) -> T,
) {

	private val fleet = ConcurrentHashMap<TraktorId, T>()
	private val todo = ConcurrentLinkedQueue<TraktorMessage<M>>()

	// locks for each currently RUNNING tractor
	private val locks = ConcurrentHashMap<TraktorId, Semaphore>()
	private val fleetLock = Semaphore(1)

	private suspend fun runTraktor(msg: TraktorMessage<M>) {
		val id = msg.id
		val cmd = msg.msg

		// we need to atomically acquire the lock for the Traktor
		// using a concurrent hash map is not enough
		fleetLock.acquire()
		val semaphore = locks.computeIfAbsent(id) { Semaphore(1) }
		semaphore.acquire()
		fleetLock.release()

		try {
			val traktor = fleet.computeIfAbsent(id) { newTraktor(id) }
			val newTraktor = traktor(cmd)
			fleet[id] = newTraktor
		} finally {
			locks.remove(id)
			semaphore.release()
		}
	}

	/**
	 * This is where Fleet starts processing messages.
	 */
	suspend fun run() {
		// it first launches the messageProcessor
		scope.launch(context) {
			messageProcessor()
		}.invokeOnCompletion { it?.printStackTrace() }
		while (true) {
			val msg = receiveChannel.receive()
			// all received messages are put right into a queue
			// this queue could be persisted
			todo.add(msg)
		}
	}

	private fun messageProcessor() {
		while (true) {
			val message = todo.poll()
			if (message == null) {
				sleep(100)
				continue
			}

			launchTraktor(message)
		}
	}

	private fun launchMessageProcessor() {
		val coroutineContext = CoroutineName("fleet-message-processor") + Dispatchers.Default
		scope.launch(coroutineContext) {
			messageProcessor()
		}.invokeOnCompletion { it?.printStackTrace() }
	}

	private fun launchTraktor(msg: TraktorMessage<M>) {
		val coroutineContext = CoroutineName("traktor-" + msg.id.value) + Dispatchers.Default

		scope.launch(coroutineContext) {
			runTraktor(msg)
		}.invokeOnCompletion { it?.printStackTrace() }
	}
}

/**
 * ❇️ Fleet reference to send messages to the Fleet.
 */
class FleetRef<M>(
	private val mailbox: Channel<TraktorMessage<M>>,
) {
	suspend infix fun tell(msg: TraktorMessage<M>) {
		mailbox.send(msg)
	}
}

/**
 * ❇️ A Traktor factory function that creates a new Traktor with a given state.
 */
fun <M, T : Traktor<M, *, *>> spawnFleet(
	scope: CoroutineScope,
	context: CoroutineContext,
	newTraktor: (TraktorId) -> T,
): FleetRef<M> {
	val mailbox = Channel<TraktorMessage<M>>(capacity = Channel.UNLIMITED)

	scope.launch(context) {
		Fleet(scope, context, mailbox, newTraktor).run()
	}
	return FleetRef(mailbox)
}
