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

@JvmInline
value class TraktorId(val id: String)

/**
 * ❇️ This is a Traktor, a state machine node that can receive messages and
 * update its state and transfer it to a new state machine node.
 * There are two states we are dealing with:
 * <li> S: the actual state value (e.g., a data class)
 * <li> Traktor: the state node in the state machine
 */
interface Traktor<S> {
	val id: TraktorId   // unique identifier of a state
	val value: S        // actual state value

	/**
	 * This method is called when a message is received.
	 */
	operator fun invoke(msg: Message): Traktor<S>       // todo too broad return type
}

/**
 * ❇️ Message markers for the Traktor
 * We need to differentiate between Query and Command messages.
 * Query messages are read-only and do not change the state of the Traktor.
 * Query messages can run in parallel.
 * Command messages are read-write and can change the state of the Traktor.
 * Command messages are executed sequentially (one at a time).
 * In the future, we can optimize the Command messages to run in parallel
 * when they do not interfere with each other (!)
 *
 * TODO Not used at the moment.
 */
sealed interface Message {
	interface Query : Message
	interface Command : Message
}

/**
 * ❇️ A Traktor factory function that creates a new Traktor with a given state.
 */
fun <T : Traktor<*>> spawnFleet(
	scope: CoroutineScope,
	context: CoroutineContext,
	newTraktor: (TraktorId) -> T,
): FleetRef {
	val mailbox = Channel<Pair<TraktorId, Message.Command>>(capacity = Channel.UNLIMITED)

	scope.launch(context) {
		Fleet(scope, context, mailbox, newTraktor).run()
	}
	return FleetRef(mailbox)
}

/**
 * ❇️ Fleet reference to send messages to the Fleet.
 */
class FleetRef(
	private val mailbox: Channel<Pair<TraktorId, Message.Command>>,
) {
	suspend fun tell(id: TraktorId, msg: Message.Command) {
		mailbox.send(id to msg)
	}
}

/**
 * ❇️ Fleet is the main orchestrator of the Traktors.
 * It receives messages from the FleetRef and forwards them to the Traktors.
 * It also creates new Traktors when needed.
 */
class Fleet<T : Traktor<*>>(
	private val scope: CoroutineScope,
	private val context: CoroutineContext,
	private val receiveChannel: Channel<Pair<TraktorId, Message.Command>>,
	private val newTraktor: (TraktorId) -> T,
) {

	private val fleet = ConcurrentHashMap<TraktorId, T>()
	private val todoMessages = ConcurrentLinkedQueue<Pair<TraktorId, Message.Command>>()
	private val locks = ConcurrentHashMap<TraktorId, Semaphore>()   // todo put together with fleet

	private suspend fun runTraktor(id: TraktorId, cmd: Message.Command) {
		val semaphore = locks.computeIfAbsent(id) { Semaphore(1) }
		semaphore.acquire()
		try {
			val traktor = fleet.computeIfAbsent(id) { newTraktor(id) }
			val newTraktor = traktor(cmd) as T
			fleet[id] = newTraktor
		} finally {
			semaphore.release()
		}
	}

	/**
	 * This is where Fleet starts processing messages.
	 */
	suspend fun run() {
		// it first launches the messageProcessor
		launchMessageProcessor()
		while (true) {
			val msg = receiveChannel.receive()
			// all received messages are put right into a queue
			// this queue could be persisted
			todoMessages.add(msg)
		}
	}

	private fun messageProcessor() {
		while (true) {
			val message = todoMessages.poll()
			if (message == null) {
				sleep(100)
				continue
			}

			val traktorId = message.first
			val cmd = message.second

			launchTraktor(traktorId, cmd)

//			when (cmd) {
//				is Message.Query -> {
//					launchTraktor(traktorId, cmd)
//				}
//				is Message.Command -> {
//					fleet.clear()
//					launchTraktor(traktorId, cmd)
//				}
//			}
		}
	}

	private fun launchMessageProcessor() {
		val coroutineContext = CoroutineName("fleet-message-processor") + Dispatchers.Default
		scope.launch(coroutineContext) {
			messageProcessor()
		}.invokeOnCompletion { it?.printStackTrace() }
	}

	private fun launchTraktor(id: TraktorId, cmd: Message.Command) {
		val coroutineContext = CoroutineName("traktor-" + id.id) + Dispatchers.Default

		scope.launch(coroutineContext) {
			runTraktor(id, cmd)
		}.invokeOnCompletion { it?.printStackTrace() }
	}
}