package parallelZip

import java.io.OutputStream
import java.util.zip.ZipOutputStream

class SingleEntryZipOutputStream(out: OutputStream) : ZipOutputStream(out) {
    override fun finish() {
        closeEntry()
    }
}