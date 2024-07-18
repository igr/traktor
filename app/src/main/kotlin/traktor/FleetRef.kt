package traktor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout

/**
 * ❇️ Fleet reference for sending messages to the Fleet.
 */
class FleetRef<M, F> internal constructor(
	val name: String,
	private val mailbox: Channel<FleetMessage>,
	private val cc: CoroutineScope,
) {

	/**
	 * Tell a message to a Traktor.
	 */
	suspend fun tell(pair: Pair<M, TraktorAddress>) = tell(pair.second, pair.first)

	/**
	 * Tell a message to a Traktor.
	 */
	suspend fun tell(traktorAddress: TraktorAddress, msg: M) {
		mailbox.send(TrakorFleetMessage(traktorAddress, msg))
	}

	/**
	 * Shout a message to the Fleet.
	 */
	suspend infix fun shout(msg: F) {
		mailbox.send(ShoutFleetMessage(msg))
	}

	/**
	 * Ask traktor for a reply.
	 */
	suspend fun <M> ask(
		traktorAddress: TraktorAddress,
		msg: M,
		timeoutInMillis: Long = 10_000L,
	): Deferred<Unit> {
		val receiveChannel = Channel<Unit>(capacity = Channel.RENDEZVOUS)
//		val cc = CoroutineName("fleet.${name}.reply.traktor.${traktorAddress.value}") + Dispatchers.Default
		return cc.async {
			try {
				withTimeout(timeoutInMillis) {
					mailbox.send(
						TraktorFleetMessageWithReply(traktorAddress, msg, ReplyRef(receiveChannel))
					)
					receiveChannel.receive()//.also { println("received") }
				}
			} finally {
				receiveChannel.close()
			}
		}
	}
}

/**
 * Reply reference used for replying to a message.
 */
class ReplyRef<R> internal constructor(
	private val mailbox: Channel<R>,
) {
	suspend fun reply(msg: R) {
		mailbox.send(msg)
	}

	/**
	 * Reply with a no value, indicating that traktor has answered.
	 */
	suspend fun reply() {
		mailbox.send(true as R)
	}
}