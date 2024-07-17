package example

import traktor.Traktor
import traktor.TraktorId

// respawn a new counter
fun newCounter(id: TraktorId): Counter {
	val value = database[id] ?: 0
	return Counter(id.value, value)
}

// update the counter value
fun newCounter(id: TraktorId, value: Int): Counter {
	database[id] = value
	return Counter(id.value, value)
}

class Counter(
	name: String,
	override val value: Int
) : Traktor<Counter.Message, Int, Counter> {
	override val id: TraktorId = TraktorId(name)

	// messages
	sealed interface Message
	data class Inc(val by: Int) : Message
	data object Reset : Message

	// state machine

	override operator fun invoke(msg: Message): Counter {
		return when (msg) {
			is Inc -> {
				println("Counter $id: $value + ${msg.by}")
				newCounter(id, value + msg.by)
			}
			is Reset -> {
				println("Counter $id: reset")
				newCounter(id, 0)
			}
		}
	}
}
