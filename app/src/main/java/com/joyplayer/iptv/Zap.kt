package com.joyplayer.iptv

/** Lets the fullscreen live player change channel (up / down, CH+ / CH-) inside the list the user was browsing. */
object LiveZap {
    var list: List<Channel> = emptyList()
    var all: List<Channel> = emptyList()       // every channel the user can see (for "last channel")
    var play: ((Channel) -> Unit)? = null      // opens the channel fullscreen
    var onSelect: ((Channel) -> Unit)? = null  // keeps the Live screen selection in sync
}
