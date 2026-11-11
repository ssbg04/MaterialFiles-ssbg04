package me.zhanghai.android.files.storage

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.provider.cloud.client.Authentication
import me.zhanghai.android.files.provider.cloud.client.Authority
import me.zhanghai.android.files.provider.cloud.createCloudRootPath
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.putArgs
import kotlin.random.Random

@Parcelize
class CloudServer(
    override val id: Long,
    override val customName: String?,
    val authority: Authority,
    val authentication: Authentication,
    val relativePath: String
) : Storage() {
    constructor(
        id: Long?,
        customName: String?,
        authority: Authority,
        authentication: Authentication,
        relativePath: String
    ) : this(id ?: Random.nextLong(), customName, authority, authentication, relativePath)

    override val iconRes: Int
        @DrawableRes
        get() = R.drawable.cloud_icon_white_24dp

    override fun getDefaultName(context: Context): String {
        val base = if (authority.username.isNotEmpty()) {
            "${authority.provider.displayName} (${authority.username})"
        } else {
            authority.provider.displayName
        }
        return if (relativePath.isNotEmpty()) "$base/$relativePath" else base
    }

    override val description: String
        get() = authority.toString()

    override val path: Path
        get() = authority.createCloudRootPath().resolve(relativePath)

    override fun createEditIntent(): Intent =
        EditCloudServerActivity::class.createIntent().putArgs(EditCloudServerFragment.Args(this))
}

