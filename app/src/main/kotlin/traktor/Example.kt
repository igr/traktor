package traktor

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

suspend fun main(): Unit = coroutineScope {
	val fleet = spawnFleet(this, coroutineContext) { newCounter(it) }

	repeat(1_000_000) {
		val id = TraktorId(Random.nextInt(10).toString())
		fleet.tell(id, Counter.Inc(1))
	}

	delay(10_000)
	println("Database check: ${databaseCheck()}")
}