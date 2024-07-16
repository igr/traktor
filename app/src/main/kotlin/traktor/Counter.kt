package traktor

import java.util.concurrent.ConcurrentHashMap

private val database = ConcurrentHashMap<TraktorId, Int>()
fun databaseCheck(): Int {
	return database.values.reduce(Int::plus)
}

// respawn a new counter
fun newCounter(id: TraktorId): Counter {
	val value = database[id] ?: 0
	return Counter(id.id, value)
}
// update the counter value
fun newCounter(id: TraktorId, value: Int): Counter {
	database[id] = value
	return Counter(id.id, value)
}

class Counter(
	name: String,
	override val value: Int
) : Traktor<Int> {
	override val id: TraktorId = TraktorId(name)

	// messages

	data class Inc(val by: Int) : Message.Command
	data object Reset : Message.Command


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
			else -> {
				println("Counter $id: unknown message")
				this
			}
		}
	}
}
