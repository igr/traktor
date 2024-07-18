package example

import traktor.Faktor
import traktor.Mutable
import traktor.TraktorAddress

// simulate a database
// VERY IMPORTANT: this map is not a ConcurrentHashMap!
// The engine is designed to be thread-safe, so we don't need to worry about it!
internal val database = mutableMapOf<TraktorAddress, Int>()

/**
 * Simply sums all the values in the database to verify that
 * all processes have been executed correctly.
 */
fun databaseCheck(): Int {
	return database.values.reduce(Int::plus)
}

class Counters(
	// messages
) : Faktor<Counters.Message> {
	sealed interface Message
	data object Reset : Message, Mutable
	data object Checksum : Message

	override operator fun invoke(msg: Message): Counters {
		return when (msg) {
			is Reset -> {
				database.entries
					.filter { it.value > 50 }
					.forEach { (addr, _) -> database[addr] = 0 }
				this
			}
			is Checksum -> {
				println("Database check: ${databaseCheck()}")
				this
			}
		}
	}

}