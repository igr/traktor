package traktor

interface Mutable

internal interface FleetMessage

data class TrakorFleetMessage<M>(
	val address: TraktorAddress,
	val msg: M
) : FleetMessage

data class TraktorFleetMessageWithReply<M, R>(
	val address: TraktorAddress,
	val msg: M,
	val replyTo: ReplyRef<R>
) : FleetMessage

data class ShoutFleetMessage<F>(
	val msg: F
) : FleetMessage