package traktor

interface Faktor<M, T: Faktor<M, T>> {

	/**
	 * This method is called when a message is received.
	 */
	operator fun invoke(msg: M): T
}
