package traktor

interface Faktor<F, T: Faktor<F, T>> {

	/**
	 * This method is called when a message is received.
	 */
	operator fun invoke(msg: F): T
}
