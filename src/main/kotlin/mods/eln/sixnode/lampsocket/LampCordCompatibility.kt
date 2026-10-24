package mods.eln.sixnode.lampsocket

import mods.eln.transparentnode.cord.CordRating

/** A bulb holder does not choose its plug by the bulb currently in the socket.
 * Actual supply voltage still reaches the bulb and its existing protection. */
object LampCordCompatibility {
    fun accepts(rating: CordRating, nominal: Double) = rating == CordRating.V120 ||
        rating == CordRating.V240 || rating.industrial && rating.accepts(nominal)
}
