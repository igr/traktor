package example

import example.Counter.Message
import traktor.Mutable
import traktor.Traktor
import traktor.TraktorAddress

// respawn a new counter
fun newCounter(address: TraktorAddress): Counter {
	val value = database[address] ?: 0
	return Counter(address, value)
}

// update the counter value
fun newCounter(address: TraktorAddress, value: Int): Counter {
	database[address] = value
	return Counter(address, value)
}

class Counter(
	override val address: TraktorAddress,
	private val value: Int
) : Traktor<Message> {

	// messages
	sealed interface Message
	data class Inc(val by: Int) : Message, Mutable
	data object Reset : Message, Mutable

	// state machine

	override operator fun invoke(msg: Message): Counter {
		return when (msg) {
			is Inc -> {
				println("Counter $address: $value + ${msg.by}")
				newCounter(address, value + msg.by)
			}
			is Reset -> {
				println("Counter $address: reset")
				newCounter(address, 0)
			}
		}
	}
}
