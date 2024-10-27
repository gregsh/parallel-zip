package parallelZip

import java.io.*
import java.lang.invoke.MethodHandles
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ZipInputStreamEx : ZipInputStream {
    private val bos = BOS()
    private var entry: ZipEntry? = null

    constructor(input: InputStream) : super(ByteArrayInputStream(byteArrayOf())) {
        pushback.set(this, PIS(input, 512))
    }

    override fun getNextEntry(): ZipEntry? {
        if (entry != null) closeEntry()
        bos.reset()
        entry = super.getNextEntry()
        return entry
    }

    fun readCompressedBytes(): ByteArray {
        entry ?: error("No entry is open")
        closeEntry()
        val bytes = bos.toByteArray().also {
            this.entry = null
            bos.reset()
        }
        return bytes
    }

    private class BOS : ByteArrayOutputStream() {
        fun unwrite(num: Int) {
            count -= num
        }
    }

    private inner class PIS(stream: InputStream, size: Int) : PushbackInputStream(stream, size) {
        override fun read(): Int {
            val result = super.read()
            if (result != -1) bos.write(result)
            return result
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val result = super.read(b, off, len)
            bos.write(b, off, result)
            return result
        }

        override fun read(b: ByteArray): Int {
            return read(b, 0, b.size)
        }

        override fun unread(b: Int) {
            super.unread(b)
            bos.unwrite(1)
        }

        override fun unread(b: ByteArray, off: Int, len: Int) {
            super.unread(b, off, len)
            bos.unwrite(len)
        }

        override fun unread(b: ByteArray) {
            super.unread(b)
            bos.unwrite(b.size)
        }
    }
}

private val lookup = MethodHandles.privateLookupIn(FilterInputStream::class.java, MethodHandles.lookup())
private val pushback = lookup.findVarHandle(FilterInputStream::class.java, "in", InputStream::class.java)
