package com.sumanonline.sumanmovies.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.sumanonline.sumanmovies.data.models.StreamingServer

class ServerSelectionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sumanmovies_server_prefs", Context.MODE_PRIVATE)

    fun getSelectedServer(mediaId: String): StreamingServer {
        val saved = prefs.getString("server_$mediaId", null) ?: return StreamingServer.MOVIEBOX
        return StreamingServer.fromId(saved)
    }

    fun setSelectedServer(mediaId: String, server: StreamingServer) {
        prefs.edit().putString("server_$mediaId", server.id).apply()
    }

    fun clearSelection(mediaId: String) {
        prefs.edit().remove("server_$mediaId").apply()
    }
}
