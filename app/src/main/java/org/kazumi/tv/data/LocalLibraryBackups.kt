package org.kazumi.tv.data
import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID

class LocalLibraryBackups(context:Context) {
    private val directory=File(context.filesDir,"library-backups")
    fun list():List<File> = directory.listFiles()?.filter { it.isFile && it.name.matches(Regex("[0-9]+-[a-f0-9-]+\\.json")) }?.sortedByDescending { it.name } ?: emptyList()
    fun save(raw:String):File {
        LibraryArchiveCodec.read(raw)
        check(directory.isDirectory || directory.mkdirs())
        val file=File(directory,"${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
        val atomic=AtomicFile(file); val stream=atomic.startWrite()
        try { stream.write(raw.toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch(e:Exception) { atomic.failWrite(stream); throw e }
        list().drop(5).forEach { check(it.delete()) { "备份已保存，但清理旧副本失败" } }
        return file
    }
    fun read(file:File):String {
        require(file.canonicalFile.parentFile==directory.canonicalFile && file in list())
        require(file.length()<=LibraryArchiveCodec.MAX_CHARS*4L)
        return file.bufferedReader().use { BoundedText.read(it,LibraryArchiveCodec.MAX_CHARS) }.also { LibraryArchiveCodec.read(it) }
    }
}
