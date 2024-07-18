package traktor

@JvmInline
value class TraktorAddress(val value: String)

/**
 * ❇️ This is a Traktor, a state machine node that can receive messages and
 * update its state and transfer it to a new state machine node.
 * There are two states we are dealing with:
 * <li> S: the actual state value (e.g., a data class)
 * <li> Traktor: the state node in the state machine
 */
interface Traktor<M> {
	val address: TraktorAddress   // unique data state address

	/**
	 * This method is called when a message is received.
	 */
	operator fun invoke(msg: M): Traktor<M>
}


// todo add delegate/wrapper over traktor for additional control