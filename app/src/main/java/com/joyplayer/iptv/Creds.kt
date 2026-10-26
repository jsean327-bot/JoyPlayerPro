package com.joyplayer.iptv

/**
 * Favorites, history and "resume" positions are saved with the channel / movie link. Links of an Xtream
 * account contain the username and password, so before saving they are replaced by placeholders
 * ({U} / {P}) and filled in again when the account is open. Nothing secret stays in those saved lists, and
 * they keep working after the password changes.
 */
object Creds {
    @Volatile private var user = ""
    @Volatile private var pass = ""

    fun set(l: SavedList?) {
        user = l?.user.orEmpty()
        pass = l?.pass.orEmpty()
    }

    fun strip(s: String): String {
        val u = user
        val p = pass
        if (u.isBlank() || p.isBlank() || !s.contains("/$u/$p/")) return s
        val rx = Regex("(/(?:live|movie|series|timeshift)/)" + Regex.escape(u) + "/" + Regex.escape(p) + "/")
        return rx.replace(s) { m -> m.groupValues[1] + "{U}/{P}/" }
    }

    fun fill(s: String): String {
        if (!s.contains("/{U}/{P}/") || user.isBlank() || pass.isBlank()) return s
        return s.replace("/{U}/{P}/", "/$user/$pass/")
    }
}
