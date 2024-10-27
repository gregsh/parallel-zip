package parallelZip;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Sequential {
  /**
   * @noinspection UseOfSystemOutOrSystemErr, ResultOfMethodCallIgnored
   */
  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.out.println("Usage: parallelZip out.zip file-or-dir ...");
      System.exit(0);
    }

    var zipFile = Paths.get(args[0].replaceFirst("~", System.getProperty("user.home"))).toAbsolutePath();
    var toZip = Arrays.stream(args).skip(1).map(it -> Paths.get(it.replaceFirst("~", System.getProperty("user.home"))).toAbsolutePath());
    zipFile.getParent().toFile().mkdirs();

    var count = new AtomicInteger();
    var startTime = System.nanoTime();
    try (var zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)))) {
      toZip.forEach(root -> {
        var rootParent = root.getParent();
        try (var pathStream = Files.walk(root)) {
          pathStream.filter(o -> !Files.isDirectory(o)).forEach(path -> {
            var relativePath = rootParent == null ? path : rootParent.relativize(path);
            try (var fileStream = new BufferedInputStream(Files.newInputStream(path))) {
              zip.putNextEntry(new ZipEntry(relativePath.toString()));
              fileStream.transferTo(zip);
              zip.closeEntry();
              count.incrementAndGet();
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }
    System.out.println(count.get() + " entries loaded and compressed");
    System.out.println(zipFile + " created in " + (System.nanoTime() - startTime) / 1e9 + " sec");
  }
}