package parallelZip;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainJava {
  /** @noinspection UseOfSystemOutOrSystemErr, ResultOfMethodCallIgnored */
  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.out.println("Usage: parallelZip out.zip file-or-dir ...");
      System.exit(0);
    }

    var zipFile = Paths.get(args[0].replaceFirst("~", System.getProperty("user.home"))).toAbsolutePath();
    var toZip = Arrays.stream(args).skip(1).map(it -> Paths.get(it.replaceFirst("~", System.getProperty("user.home"))).toAbsolutePath());
    zipFile.getParent().toFile().mkdirs();

    var zipEntries = new ConcurrentHashMap<ZipEntry, byte[]>();
    var startTime = System.nanoTime();
    try (var pool = ForkJoinPool.commonPool()) {
      toZip.forEach(root -> {
        var rootParent = root.getParent();
        try (var pathStream = Files.walk(root)) {
          pathStream.filter(o -> !Files.isDirectory(o)).forEach(
            path -> pool.execute(() -> {
              if (path.getFileName().toString().endsWith(".zip")) {
                addZipFileEntriesToMap(path, zipEntries);
              }
              else {
                addZipEntryToMap(
                  rootParent == null ? path : rootParent.relativize(path),
                  path, zipEntries);
              }
            }));
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
      pool.awaitQuiescence(Long.MAX_VALUE, java.util.concurrent.TimeUnit.SECONDS);
    }

    System.out.println(zipEntries.size() + " entries loaded and compressed");
    // combine all the entries into a single zip, sorted by name
    writeZipEntriesToZip(zipFile, zipEntries.entrySet().stream()
      .sorted(Comparator.comparing(o -> o.getKey().getName())).toList());
    System.out.println(zipFile + " created in " + (System.nanoTime() - startTime) / 1e9 + " sec");
  }

  private static void addZipEntryToMap(Path relativePath, Path path, ConcurrentHashMap<ZipEntry, byte[]> zipEntries) {
    var out = new ByteArrayOutputStream();
    var zipEntry = new ZipEntry(relativePath.toString());
    // skip the central directory writing by _not_ closing the zip stream
    try (var zip = new SingleEntryZipOutputStream(out)) {
      try (var fileStream = new BufferedInputStream(Files.newInputStream(path))) {
        zip.putNextEntry(zipEntry);
        fileStream.transferTo(zip);
        zip.closeEntry();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    zipEntries.put(zipEntry, out.toByteArray());
  }

  private static void addZipFileEntriesToMap(Path path, ConcurrentHashMap<ZipEntry, byte[]> zipEntries) {
    try (var zip = new ZipInputStreamEx(new BufferedInputStream(Files.newInputStream(path)))) {
      while (true) {
        var zipEntry = zip.getNextEntry();
        if (zipEntry == null) break;
        zipEntries.put(zipEntry, zip.readCompressedBytes());
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** @noinspection unchecked*/
  private static void writeZipEntriesToZip(Path zipFile, List<Map.Entry<ZipEntry, byte[]>> list) throws Exception {
    // a zip is just its entries and a central directory at the end
    try (var os = new BufferedOutputStream(Files.newOutputStream(zipFile))) {
      try (var zip = new ZipOutputStream(os)) {
        var xEntries = (Vector<Object>)varEntries.get(zip);
        var offset = 0L;
        for (var item : list) {
          var zipEntry = item.getKey();
          var bytes = item.getValue();
          xEntries.add(xEntryConstructor.newInstance(zipEntry, offset));
          os.write(bytes);
          offset += bytes.length;
        }
        varWritten.set(zip, offset);
        // write the central directory on close
      }
    }
  }

  // a bit of JVM reflection to work around the missing JDK APIs
  private static final MethodHandles.Lookup lookup;
  private static final VarHandle varEntries;
  private static final VarHandle varWritten;
  private static final Constructor<?> xEntryConstructor;
  static {
    try {
      lookup = MethodHandles.privateLookupIn(ZipOutputStream.class, MethodHandles.lookup());
      varEntries = lookup.findVarHandle(ZipOutputStream.class, "xentries", Vector.class);
      varWritten = lookup.findVarHandle(ZipOutputStream.class, "written", long.class);
      xEntryConstructor = Class.forName("java.util.zip.ZipOutputStream$XEntry")
        .getDeclaredConstructor(ZipEntry.class, long.class);
      xEntryConstructor.setAccessible(true);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}