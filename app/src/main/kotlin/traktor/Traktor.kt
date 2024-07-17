package traktor

@JvmInline
value class TraktorId(val value: String)

/**
 * ❇️ This is a Traktor, a state machine node that can receive messages and
 * update its state and transfer it to a new state machine node.
 * There are two states we are dealing with:
 * <li> S: the actual state value (e.g., a data class)
 * <li> Traktor: the state node in the state machine
 */
interface Traktor<M, S, T: Traktor<M, S, T>> {
	val id: TraktorId   // unique identifier of a state
	val value: S        // actual state value

	/**
	 * This method is called when a message is received.
	 */
	operator fun invoke(msg: M): T
}

/**
 * Message for specific Traktor.
 */
data class TraktorMessage<M>(
	val id: TraktorId,
	val msg: M,
)

infix fun <M> TraktorId.with(message: M): TraktorMessage<M> = TraktorMessage(this, message)

