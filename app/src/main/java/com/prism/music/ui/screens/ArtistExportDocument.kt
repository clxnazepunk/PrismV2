package com.prism.music.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

internal data class ArtistExportRequest(val mime: String, val name: String)

/** MIME travels with each launch, including SVG originals; no stale contract from a previous role. */
internal class ArtistExportDocument : ActivityResultContract<ArtistExportRequest, Uri?>() {
    override fun createIntent(context: Context, input: ArtistExportRequest): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mime).putExtra(Intent.EXTRA_TITLE, input.name)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data.takeIf { resultCode == Activity.RESULT_OK }
}
