package example

import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import traktor.TraktorAddress
import traktor.spawnFleet
import kotlin.random.Random

// USE ASK TO WAIT FOR ANSWERS or just DELAY until all traktors are done
const val useAsk = true

suspend fun main(): Unit = coroutineScope {
	println("USE ASK = $useAsk")

	val fleet = spawnFleet(
		"counters",
		this,
		coroutineContext,
		{ Counters() },
		{ newCounter(it) }
	)

	if (useAsk == false) {
		val now = System.currentTimeMillis()
		repeat(1_000_000) {
			val address = TraktorAddress(Random.nextInt(100).toString())
			fleet.tell(Counter.Inc(1) to address)
		}

		println("All traktors told in ${System.currentTimeMillis() - now} ms")       // usually < 500ms

		delay(10_000)

		println("10 seconds passed")

	} else {
		(0 until 1_000_000).map {
			val address = TraktorAddress(Random.nextInt(100).toString())
			fleet.ask(address, Counter.Inc(1))
		}.awaitAll()

		println("All traktors awaited")
	}

	fleet shout Counters.Checksum

	delay(100)

	fleet shout Counters.Reset

	println("Reset sent")

	delay(1_000)

	fleet shout Counters.Checksum

}