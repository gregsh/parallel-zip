@file:JvmName("MainKotlin")
package parallelZip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.lang.invoke.MethodHandles
import java.nio.file.Files
import java.nio.file.Path
import java.util.Vector
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.relativeTo
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: parallelZip out.zip file-or-dir ...")
        exitProcess(0)
    }
    val zipFile = Path(args[0].replaceFirst("~", System.getProperty("user.home"))).absolute()
    val toZip = args.drop(1).map { Path(it.replaceFirst("~", System.getProperty("user.home"))).absolute() }
    zipFile.parent.toFile().mkdirs()

    val zipEntries = ConcurrentHashMap<ZipEntry, ByteArray>()
    val startTime = System.nanoTime()
    runBlocking(Dispatchers.Default) {
        toZip.forEach { root ->
            Files.walk(root).filter { !it.isDirectory() }.forEach { path ->
                launch {
                    val relativePath = (if (root.parent == null) path else path.relativeTo(root.parent))
                    if (path.name.endsWith(".zip")) {
                        addZipFileEntriesToMap(path, zipEntries)
                    }
                    else {
                        addZipEntryToMap(relativePath, path, zipEntries)
                    }
                }
            }
        }
    }
    println("${zipEntries.size} entries loaded and compressed")
    // combine all the entries into a single zip, sorted by name
    writeZipEntriesToZip(zipFile, zipEntries.entries.sortedBy { it.key.name }.toList())
    println("$zipFile created in ${(System.nanoTime() - startTime) / 1e9} sec")
}

private fun addZipEntryToMap(relativePath: Path, path: Path, zipEntries: ConcurrentHashMap<ZipEntry, ByteArray>) {
    val out = ByteArrayOutputStream()
    val zipEntry = ZipEntry(relativePath.toString())
    // skip the central directory writing by _not_ closing the zip stream
    ZipOutputStream(out).let {
        it.putNextEntry(zipEntry)
        path.inputStream().buffered().use { s -> s.copyTo(it) }
        it.closeEntry()
    }
    zipEntries[zipEntry] = out.toByteArray()
}

private fun addZipFileEntriesToMap(path: Path, zipEntries: ConcurrentHashMap<ZipEntry, ByteArray>) {
    val zip = ZipInputStreamEx(path.inputStream().buffered())
    while (true) {
        val zipEntry = zip.getNextEntry() ?: break
        zipEntries[zipEntry] = zip.readCompressedBytes()
    }
}

@Suppress("UNCHECKED_CAST")
private fun writeZipEntriesToZip(zipFile: Path, list: List<Map.Entry<ZipEntry, ByteArray>>) {
    // a zip is just its entries and a central directory at the end
    zipFile.outputStream().buffered().use { os ->
        val zip = ZipOutputStream(os)
        val xEntries = varEntries.get(zip) as Vector<Any>
        var offset = 0L
        list.forEach { (zipEntry, bytes) ->
            xEntries.add(xEntryConstructor.newInstance(zipEntry, offset))
            os.write(bytes)
            offset += bytes.size.toLong()
        }
        // write the central directory
        varWritten.set(zip, offset)
        zip.close()
    }
}

// a bit of JVM reflection to work around the missing JDK APIs
private val lookup = MethodHandles.privateLookupIn(ZipOutputStream::class.java, MethodHandles.lookup())
private val varEntries = lookup.findVarHandle(ZipOutputStream::class.java, "xentries", Vector::class.java)
private val varWritten = lookup.findVarHandle(ZipOutputStream::class.java, "written", Long::class.java)
private val xEntryConstructor = Class.forName("java.util.zip.ZipOutputStream\$XEntry")
    .getDeclaredConstructor(ZipEntry::class.java, Long::class.java).apply { isAccessible = true }
