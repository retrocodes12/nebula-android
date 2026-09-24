package com.nuvio.ckplayer

// What an address from someone else (an add-on, a watch party, the cloud's relay list) is allowed to name.

private val LAN_HOST = Regex("^(10(\\.\\d{1,3}){3}|192\\.168(\\.\\d{1,3}){2}|172\\.(1[6-9]|2\\d|3[01])(\\.\\d{1,3}){2})$")
private val WEB_URL = Regex("^https?://", RegexOption.IGNORE_CASE)

/** A private LAN address (10/8, 172.16/12, 192.168/16). The relay list comes from the cloud, and a profile someone else
    got into must not be able to route this TV's streams through a server of theirs. */
fun lanHost(h: String): Boolean = LAN_HOST.matches(h)

/** A web address, else "". Media from an add-on or a party is played through the same data source that opens file: and
    content: addresses, so anything but http(s) is dropped where it comes in. */
fun webUrl(u: String?): String = u?.trim()?.takeIf { WEB_URL.containsMatchIn(it) }.orEmpty()
