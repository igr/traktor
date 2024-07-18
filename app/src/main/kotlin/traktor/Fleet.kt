package traktor

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * ❇️ Fleet is the main orchestrator of the Traktors.
 * It receives messages from the FleetRef and forwards them to the Traktors.
 * It also creates new Traktors when needed.
 */
internal class Fleet<M, F>(
	private val name: String,
	private val scope: CoroutineScope,
	private val context: CoroutineContext,
	private val receiveChannel: Channel<FleetMessage>,
	newFaktor: () -> Faktor<F>,
	private val newTraktor: (TraktorAddress) -> Traktor<M>,
) {

	// Traktors that are already in memory.
	// They might be running or not.
	// This map may be size-bounded, using MRU or LRU eviction policies.
	// It's up to this engine to decide.
	// For now, we store only one Traktor per TraktorId; this should be changed.
	// We should store multiple Traktors per TraktorId, and the engine should
	// e.g. round-robin between them.
	private val fleet = ConcurrentHashMap<TraktorAddress, Traktor<M>>()

	// this is the master lock for the fleet
	private val fleetLock = Semaphore(1)

	// locks for each currently RUNNING tractor
	private val locks = ConcurrentHashMap<TraktorAddress, Semaphore>()

	private suspend fun runFaktor(msg: F) {
		if (msg is Mutable) {
			// mutable fleet messages are locking the whole fleet
			fleetLock.acquire()
			// mutable messages are clearing the fleet (!)
			fleet.clear()
		}

		faktor = faktor(msg)

		if (msg is Mutable) {
			fleetLock.release()
		}
	}

	private suspend fun runTraktor(id: TraktorAddress, cmd: M) {

		// we need to atomically acquire the lock for the Traktor
		// using a concurrent hash map is not enough
		fleetLock.acquire()
		val semaphore = locks.computeIfAbsent(id) { Semaphore(1) }
		semaphore.acquire()
		fleetLock.release()

		try {
			val traktor = fleet.computeIfAbsent(id) { newTraktor(id) }
			val newTraktor = traktor(cmd)

			// nije bitno da li traktor mutira ili ne, pošto se svakako menja instanca

			fleet[id] = newTraktor      // todo there could be MORE than one Tractor for the same data - ne moze ako se uvek menja instanca!!!!!!!!!!!
		} finally {
			locks.remove(id)
			semaphore.release()
		}
	}

	/**
	 * This is where Fleet starts processing messages.
	 */
	suspend fun run() {
		while (true) {
			val msg = receiveChannel.receive()
			when (msg) {
				is ShoutFleetMessage<*> -> launchFaktor(msg.msg as F)
				is TrakorFleetMessage<*> ->
					launchTraktor(msg.address, msg.msg as M)
						.invokeOnCompletion { it?.printStackTrace() }
				is TraktorFleetMessageWithReply<*, *> ->
					launchTraktorWithReply(msg.address, msg.msg as M, msg.replyTo as ReplyRef<Unit>)
						.invokeOnCompletion { it?.printStackTrace() }
			}
		}
	}

	private fun launchTraktor(addr: TraktorAddress, msg: M): Job {
		val coroutineContext = CoroutineName("fleet.${name}.traktor.${addr.value}") + Dispatchers.Default
		return scope.launch(coroutineContext) {
			runTraktor(addr, msg)
		}
	}
	private fun launchTraktorWithReply(addr: TraktorAddress, msg: M, replyRef: ReplyRef<Unit>): Job {
		val coroutineContext = CoroutineName("fleet.${name}.traktor." + addr.value) + Dispatchers.Default
		return scope.launch(coroutineContext) {
			runTraktor(addr, msg)
			replyRef.reply()
		}
	}

	private fun launchFaktor(msg: F) {
		val coroutineContext = CoroutineName("fleet.${name}.faktor") + Dispatchers.Default
		scope.launch(coroutineContext) {
			runFaktor(msg)
		}.invokeOnCompletion { it?.printStackTrace() }
	}

	// 🟧 FAKTOR

	private var faktor: Faktor<F> = newFaktor()
}

/**
 * ❇️ A Traktor factory function that creates a new Traktor with a given state.
 */
fun <M, F> spawnFleet(
	name: String,
	scope: CoroutineScope,
	context: CoroutineContext,
	newFaktor: () -> Faktor<F>,
	newTraktor: (TraktorAddress) -> Traktor<M>,
): FleetRef<M, F> {
	val mailbox = Channel<FleetMessage>(capacity = Channel.UNLIMITED)

	scope.launch(context) {
		Fleet(name, scope, context, mailbox, newFaktor, newTraktor).run()
	}
	return FleetRef(name, mailbox, scope)
}
