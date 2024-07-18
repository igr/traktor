package traktor

interface Faktor<F> {

	/**
	 * Faktor behavior.
	 */
	operator fun invoke(msg: F): Faktor<F>
}
