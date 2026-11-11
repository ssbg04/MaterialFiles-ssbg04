package me.zhanghai.android.files.provider.cloud

import java8.nio.channels.FileChannel
import java8.nio.channels.SeekableByteChannel
import java8.nio.file.AccessMode
import java8.nio.file.CopyOption
import java8.nio.file.DirectoryStream
import java8.nio.file.FileAlreadyExistsException
import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.FileSystemAlreadyExistsException
import java8.nio.file.FileSystemNotFoundException
import java8.nio.file.LinkOption
import java8.nio.file.NoSuchFileException
import java8.nio.file.OpenOption
import java8.nio.file.Path
import java8.nio.file.ProviderMismatchException
import java8.nio.file.StandardOpenOption
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileAttribute
import java8.nio.file.attribute.FileAttributeView
import java8.nio.file.spi.FileSystemProvider
import me.zhanghai.android.files.provider.cloud.client.Authority
import me.zhanghai.android.files.provider.cloud.client.Client
import me.zhanghai.android.files.provider.cloud.client.CloudProvider
import me.zhanghai.android.files.provider.common.ByteStringPath
import me.zhanghai.android.files.provider.common.DelegateSchemeFileSystemProvider
import me.zhanghai.android.files.provider.common.PathListDirectoryStream
import me.zhanghai.android.files.provider.common.PathObservable
import me.zhanghai.android.files.provider.common.PathObservableProvider
import me.zhanghai.android.files.provider.common.Searchable
import me.zhanghai.android.files.provider.common.WalkFileTreeSearchable
import me.zhanghai.android.files.provider.common.WatchServicePathObservable
import me.zhanghai.android.files.provider.common.decodedPathByteString
import me.zhanghai.android.files.provider.common.toAccessModes
import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.provider.common.toCopyOptions
import me.zhanghai.android.files.provider.common.toLinkOptions
import me.zhanghai.android.files.provider.common.toOpenOptions
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

object CloudFileSystemProvider : FileSystemProvider(), PathObservableProvider, Searchable {
    private val HIDDEN_FILE_NAME_PREFIX = ".".toByteString()

    private val fileSystems = mutableMapOf<Authority, CloudFileSystem>()

    private val lock = Any()

    override fun getScheme(): String = CloudProvider.GDRIVE.scheme

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem {
        uri.requireSameScheme()
        val authority = uri.cloudAuthority
        synchronized(lock) {
            if (fileSystems[authority] != null) {
                throw FileSystemAlreadyExistsException(authority.toString())
            }
            return newFileSystemLocked(authority)
        }
    }

    internal fun getOrNewFileSystem(authority: Authority): CloudFileSystem =
        synchronized(lock) { fileSystems[authority] ?: newFileSystemLocked(authority) }

    private fun newFileSystemLocked(authority: Authority): CloudFileSystem {
        val fileSystem = CloudFileSystem(this, authority)
        fileSystems[authority] = fileSystem
        return fileSystem
    }

    override fun getFileSystem(uri: URI): FileSystem {
        uri.requireSameScheme()
        val authority = uri.cloudAuthority
        return synchronized(lock) { fileSystems[authority] }
            ?: throw FileSystemNotFoundException(authority.toString())
    }

    internal fun removeFileSystem(fileSystem: CloudFileSystem) {
        val authority = fileSystem.authority
        synchronized(lock) { fileSystems.remove(authority) }
    }

    override fun getPath(uri: URI): Path {
        uri.requireSameScheme()
        val authority = uri.cloudAuthority
        val path = uri.decodedPathByteString
            ?: throw IllegalArgumentException("URI must have a path")
        return getOrNewFileSystem(authority).getPath(path)
    }

    private fun URI.requireSameScheme() {
        val scheme = scheme
        require(scheme in CloudProvider.SCHEMES) {
            "URI scheme $scheme must be in ${CloudProvider.SCHEMES}"
        }
    }

    private val URI.cloudAuthority: Authority
        get() {
            val provider = CloudProvider.fromScheme(scheme)
            val username = userInfo.orEmpty()
            return Authority(provider, username)
        }

    @Throws(IOException::class)
    override fun newInputStream(file: Path, vararg options: OpenOption): InputStream {
        file as? CloudPath ?: throw ProviderMismatchException(file.toString())
        val openOptions = options.toOpenOptions()
        openOptions.checkForCloud()
        if (openOptions.write) {
            throw UnsupportedOperationException(StandardOpenOption.WRITE.toString())
        }
        if (openOptions.append) {
            throw UnsupportedOperationException(StandardOpenOption.APPEND.toString())
        }
        if (openOptions.truncateExisting) {
            throw UnsupportedOperationException(StandardOpenOption.TRUNCATE_EXISTING.toString())
        }
        if (openOptions.create || openOptions.createNew) {
            val existing = Client.getMetadataOrNull(file)
            if (openOptions.createNew && existing != null) {
                throw FileAlreadyExistsException(file.toString())
            }
            if (existing == null) {
                Client.makeFile(file)
            }
        }
        return Client.get(file)
    }

    @Throws(IOException::class)
    override fun newOutputStream(file: Path, vararg options: OpenOption): OutputStream {
        file as? CloudPath ?: throw ProviderMismatchException(file.toString())
        val optionsSet = mutableSetOf(*options)
        if (optionsSet.isEmpty()) {
            optionsSet += StandardOpenOption.CREATE
            optionsSet += StandardOpenOption.TRUNCATE_EXISTING
        }
        optionsSet += StandardOpenOption.WRITE
        val openOptions = optionsSet.toOpenOptions()
        openOptions.checkForCloud()
        if (!openOptions.truncateExisting && !openOptions.createNew) {
            throw UnsupportedOperationException("Missing ${StandardOpenOption.TRUNCATE_EXISTING}")
        }
        val existing = Client.getMetadataOrNull(file)
        if (openOptions.createNew && existing != null) {
            throw FileAlreadyExistsException(file.toString())
        }
        if (!(openOptions.create || openOptions.createNew) && existing == null) {
            throw NoSuchFileException(file.toString())
        }
        return Client.put(file)
    }

    @Throws(IOException::class)
    override fun newFileChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): FileChannel {
        file as? CloudPath ?: throw ProviderMismatchException(file.toString())
        options.toOpenOptions().checkForCloud()
        if (attributes.isNotEmpty()) {
            throw UnsupportedOperationException(attributes.contentToString())
        }
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun newByteChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): SeekableByteChannel {
        file as? CloudPath ?: throw ProviderMismatchException(file.toString())
        val openOptions = options.toOpenOptions()
        openOptions.checkForCloud()
        if (openOptions.write && !openOptions.truncateExisting) {
            throw UnsupportedOperationException("Missing ${StandardOpenOption.TRUNCATE_EXISTING}")
        }
        if (openOptions.write || openOptions.create || openOptions.createNew) {
            val existing = Client.getMetadataOrNull(file)
            if (openOptions.createNew && existing != null) {
                throw FileAlreadyExistsException(file.toString())
            }
            if (existing == null) {
                if (!(openOptions.create || openOptions.createNew)) {
                    throw NoSuchFileException(file.toString())
                }
                Client.makeFile(file)
            }
        }
        if (attributes.isNotEmpty()) {
            throw UnsupportedOperationException(attributes.contentToString())
        }
        return Client.openByteChannel(file, openOptions.append)
    }

    @Throws(IOException::class)
    override fun newDirectoryStream(
        directory: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> {
        directory as? CloudPath ?: throw ProviderMismatchException(directory.toString())
        @Suppress("UNCHECKED_CAST")
        val paths = Client.listChildren(directory) as List<Path>
        return PathListDirectoryStream(paths, filter)
    }

    @Throws(IOException::class)
    override fun createDirectory(directory: Path, vararg attributes: FileAttribute<*>) {
        directory as? CloudPath ?: throw ProviderMismatchException(directory.toString())
        if (attributes.isNotEmpty()) {
            throw UnsupportedOperationException(attributes.contentToString())
        }
        Client.createDirectory(directory)
    }

    override fun createSymbolicLink(link: Path, target: Path, vararg attributes: FileAttribute<*>) {
        link as? CloudPath ?: throw ProviderMismatchException(link.toString())
        when (target) {
            is CloudPath, is ByteStringPath -> {}
            else -> throw ProviderMismatchException(target.toString())
        }
        if (attributes.isNotEmpty()) {
            throw UnsupportedOperationException(attributes.contentToString())
        }
        throw UnsupportedOperationException()
    }

    override fun createLink(link: Path, existing: Path) {
        link as? CloudPath ?: throw ProviderMismatchException(link.toString())
        existing as? CloudPath ?: throw ProviderMismatchException(existing.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun delete(path: Path) {
        path as? CloudPath ?: throw ProviderMismatchException(path.toString())
        Client.delete(path)
    }

    override fun readSymbolicLink(link: Path): Path {
        link as? CloudPath ?: throw ProviderMismatchException(link.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun copy(source: Path, target: Path, vararg options: CopyOption) {
        source as? CloudPath ?: throw ProviderMismatchException(source.toString())
        target as? CloudPath ?: throw ProviderMismatchException(target.toString())
        val copyOptions = options.toCopyOptions()
        CloudCopyMove.copy(source, target, copyOptions)
    }

    @Throws(IOException::class)
    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        source as? CloudPath ?: throw ProviderMismatchException(source.toString())
        target as? CloudPath ?: throw ProviderMismatchException(target.toString())
        val copyOptions = options.toCopyOptions()
        CloudCopyMove.move(source, target, copyOptions)
    }

    override fun isSameFile(path: Path, path2: Path): Boolean {
        path as? CloudPath ?: throw ProviderMismatchException(path.toString())
        return path == path2
    }

    override fun isHidden(path: Path): Boolean {
        path as? CloudPath ?: throw ProviderMismatchException(path.toString())
        val fileName = path.fileNameByteString ?: return false
        return fileName.startsWith(HIDDEN_FILE_NAME_PREFIX)
    }

    override fun getFileStore(path: Path): FileStore {
        path as? CloudPath ?: throw ProviderMismatchException(path.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        path as? CloudPath ?: throw ProviderMismatchException(path.toString())
        val accessModes = modes.toAccessModes()
        if (accessModes.write) {
            throw UnsupportedOperationException(AccessMode.WRITE.toString())
        }
        if (accessModes.execute) {
            throw UnsupportedOperationException(AccessMode.EXECUTE.toString())
        }
        Client.getMetadata(path)
    }

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption
    ): V? {
        if (!supportsFileAttributeView(type)) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return getFileAttributeView(path, *options) as V
    }

    internal fun supportsFileAttributeView(type: Class<out FileAttributeView>): Boolean =
        type.isAssignableFrom(CloudFileAttributeView::class.java)

    @Throws(IOException::class)
    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A {
        if (!type.isAssignableFrom(BasicFileAttributes::class.java)) {
            throw UnsupportedOperationException(type.toString())
        }
        @Suppress("UNCHECKED_CAST")
        return getFileAttributeView(path, *options).readAttributes() as A
    }

    private fun getFileAttributeView(path: Path, vararg options: LinkOption): CloudFileAttributeView {
        path as? CloudPath ?: throw ProviderMismatchException(path.toString())
        val linkOptions = options.toLinkOptions()
        return CloudFileAttributeView(path, linkOptions.noFollowLinks)
    }

    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption
    ): Map<String, Any> {
        path as? CloudPath ?: throw ProviderMismatchException(path.toString())
        throw UnsupportedOperationException()
    }

    override fun setAttribute(
        path: Path,
        attribute: String,
        value: Any,
        vararg options: LinkOption
    ) {
        path as? CloudPath ?: throw ProviderMismatchException(path.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun observe(path: Path, intervalMillis: Long): PathObservable {
        path as? CloudPath ?: throw ProviderMismatchException(path.toString())
        return WatchServicePathObservable(path, intervalMillis)
    }

    @Throws(IOException::class)
    override fun search(
        directory: Path,
        query: String,
        intervalMillis: Long,
        listener: (List<Path>) -> Unit
    ) {
        directory as? CloudPath ?: throw ProviderMismatchException(directory.toString())
        WalkFileTreeSearchable.search(directory, query, intervalMillis, listener)
    }
}

val OneDriveFileSystemProvider =
    DelegateSchemeFileSystemProvider(CloudProvider.ONEDRIVE.scheme, CloudFileSystemProvider)

val DropboxFileSystemProvider =
    DelegateSchemeFileSystemProvider(CloudProvider.DROPBOX.scheme, CloudFileSystemProvider)

