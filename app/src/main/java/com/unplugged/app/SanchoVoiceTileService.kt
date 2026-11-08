package com.unplugged.app

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class SanchoVoiceTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            setActivityLaunchForClick(voiceSessionPendingIntent(this@SanchoVoiceTileService, 0))
            label = getString(R.string.quick_settings_voice_tile_label)
            icon = Icon.createWithResource(this@SanchoVoiceTileService, R.drawable.sancho_icon_tile)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
