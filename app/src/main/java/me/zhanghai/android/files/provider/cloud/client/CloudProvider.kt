package me.zhanghai.android.files.provider.cloud.client

import me.zhanghai.android.files.BuildConfig

enum class CloudProvider(
    val scheme: String,
    val host: String,
    val displayName: String,
    val authEndpoint: String,
    val tokenEndpoint: String,
    val scope: String
) {
    GDRIVE(
        scheme = "gdrive",
        host = "drive.google.com",
        displayName = "Google Drive",
        authEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
        tokenEndpoint = "https://oauth2.googleapis.com/token",
        scope = "https://www.googleapis.com/auth/drive"
    ),
    ONEDRIVE(
        scheme = "onedrive",
        host = "onedrive.live.com",
        displayName = "OneDrive",
        authEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
        tokenEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
        scope = "Files.ReadWrite.All offline_access User.Read"
    ),
    DROPBOX(
        scheme = "dropbox",
        host = "dropbox.com",
        displayName = "Dropbox",
        authEndpoint = "https://www.dropbox.com/oauth2/authorize",
        tokenEndpoint = "https://api.dropboxapi.com/oauth2/token",
        scope = "files.metadata.write files.metadata.read files.content.write files.content.read account_info.read"
    );

    val defaultClientId: String
        get() = when (this) {
            GDRIVE -> BuildConfig.GDRIVE_CLIENT_ID
            ONEDRIVE -> BuildConfig.ONEDRIVE_CLIENT_ID
            DROPBOX -> BuildConfig.DROPBOX_CLIENT_ID
        }

    val defaultClientSecret: String
        get() = when (this) {
            GDRIVE -> BuildConfig.GDRIVE_CLIENT_SECRET
            ONEDRIVE -> BuildConfig.ONEDRIVE_CLIENT_SECRET
            DROPBOX -> BuildConfig.DROPBOX_CLIENT_SECRET
        }

    companion object {
        val SCHEMES = entries.map { it.scheme }

        fun fromScheme(scheme: String): CloudProvider =
            entries.first { it.scheme.equals(scheme, ignoreCase = true) }
    }
}

