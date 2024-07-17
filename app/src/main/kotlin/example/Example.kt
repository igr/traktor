package example

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import traktor.TraktorId
import traktor.spawnFleet
import traktor.with
import kotlin.random.Random

suspend fun main(): Unit = coroutineScope {
	val fleet = spawnFleet(this, coroutineContext) { newCounter(it) }

	repeat(1_000_000) {
		val id = TraktorId(Random.nextInt(100).toString())
		fleet tell (id with Counter.Inc(1))
	}

	delay(10_000)
	println("Database check: ${databaseCheck()}")
}