package traktor

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * ❇️ Fleet reference for sending messages to the Fleet.
 */
class FleetRef<M, F> internal constructor(
	private val mailbox: Channel<TraktorMessage<M>>,
	private val faktor: Channel<F>,
){

	suspend infix fun tell(msg: TraktorMessage<M>) {
		mailbox.send(msg)
	}

	suspend infix fun tell(msg: F) {
		faktor.send(msg)
	}
}

/**
 * ❇️ Fleet is the main orchestrator of the Traktors.
 * It receives messages from the FleetRef and forwards them to the Traktors.
 * It also creates new Traktors when needed.
 */
class Fleet<M, F, T : Traktor<M, *, T>>(
	private val scope: CoroutineScope,
	private val context: CoroutineContext,
	private val faktorChannel: Channel<F>,
	private val receiveChannel: Channel<TraktorMessage<M>>,
	newFaktor: () -> Faktor<F, *>,
	private val newTraktor: (TraktorId) -> T,
) {

	// Traktors that are already in memory.
	// They might be running or not.
	private val fleet = ConcurrentHashMap<TraktorId, T>()

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
		runFaktorLoop()
		while (true) {
			val msg = receiveChannel.receive()
			launchTraktor(msg)
		}
	}

	private fun launchTraktor(msg: TraktorMessage<M>) {
		val coroutineContext = CoroutineName("traktor-" + msg.id.value) + Dispatchers.Default

		scope.launch(coroutineContext) {
			runTraktor(msg)
		}.invokeOnCompletion { it?.printStackTrace() }
	}

	// 🟧 FAKTOR

	private var faktor: Faktor<F, *> = newFaktor()

	private fun runFaktorLoop() {
		scope.launch(context) {
			while (true) {
				val msg = faktorChannel.receive()
				faktor = faktor(msg) as Faktor<F, *>    // todo
			}
		}
	}
}

/**
 * ❇️ A Traktor factory function that creates a new Traktor with a given state.
 */
fun <M, F, T : Traktor<M, *, *>> spawnFleet(
	scope: CoroutineScope,
	context: CoroutineContext,
	newFaktor: () -> Faktor<F, *>,
	newTraktor: (TraktorId) -> T,
): FleetRef<M, F> {
	val mailbox = Channel<TraktorMessage<M>>(capacity = Channel.UNLIMITED)
	val faktorChannel = Channel<F>(capacity = Channel.UNLIMITED)

	scope.launch(context) {
		Fleet(scope, context, faktorChannel, mailbox, newFaktor, newTraktor).run()
	}
	return FleetRef(mailbox, faktorChannel)
}
