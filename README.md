# Parallel Zip on JVM

Zipping tons of files on one core in a multicore/SSD/cloud era is a massive waste of time.

A zip file is just an array of entries and a central directory at the end of a file.

We cannot write to a zip file in parallel, but we can compress data in parallel in memory.

Last but not least, nobody wants to reimplement zip logic from scratch
or use an unsupported third-party zip library.
We reuse standard `java.util.zip.ZipOutputStream` and 
`java.util.zip.ZipInputStream` in the presented approach.

# Algorithm

1. Collect all zip entries and their bytes for each input file in parallel.
   For each input file:
   - Get a `ByteArrayOutputStream` and a `ZipOutputStream` on top of it
   - Write an entry to the zip stream skipping the central directory using `SingleEntryZipOutputStream`.
   - Get the bytes from the byte stream

```java
      var zipEntries = ConcurrentHashMap<ZipEntry, byte[]>();

      // for each input file in parallel:
      var out = new ByteArrayOutputStream();
      var zipEntry = new ZipEntry(filePathRelativeToZipRoot);
      try (var zip = new SingleEntryZipOutputStream(out)) {
        try (var fileStream = Files.newInputStream(filePath)) {
          zip.putNextEntry(zipEntry);
          fileStream.transferTo(zip);
          zip.closeEntry();
        }
      }
      zipEntries.put(zipEntry, out.toByteArray());
```

2. Write all entries and bytes sequentially to a target zip file:
   - Get a `FileOutputStream` and a `ZipOutputStream` on top of it
   - Write bytes of all entries to the file stream updating the zip stream state
   - Write the central directory by closing the zip stream as usual

```java
    try (var os = Files.newOutputStream(zipFile)) {
      try (var zip = new ZipOutputStream(os)) {
         var offset = 0L;
         for (Map.Entry<ZipEntry, byte[]> o : zipEntries.entrySet()) {
           var zipEntry = o.getKey();
           var bytes = o.getValue();
           zip.xEntries.add(new XEntry(zipEntry, offset)); // via reflection
           os.write(bytes);
           offset += bytes.length;
         }
         zip.offset = offset; // via reflection
         // write the central directory on close
      }
    }
```

# Notes

1. Java Reflection is used to work around missing Java API.
   **To avoid that in the future, we must request such an API**

2. The algorithm takes roughly the same amount of memory as the target zip file.
   We can start writing to disk when new zip entries are ready, applying backpressure to control memory consumption

3. It's the compression that takes most of the time.
   We can generate already compressed data in parallel in various data generation tasks. 
   Then, saving it to disk will take very little time

4. We **merge zip files** without repacking using [the same technique](src/main/kotlin/parallelZip/ZipInputStreamEx.kt)

# Results

Zipping `12.06 GB of 175,866 items` to a `1.14 GB` zip file on a MacBook M2 Max in seconds:

| Mode       | Seconds |
|------------|---------|
| Sequential | 151     |
| Parallel   | 18      |



A fully functional parallel zip in pure Java [(source)](src/main/java/parallelZip/MainJava.java):

```shell
./gradlew runJava --args="<out.zip> <file-or-dir> .." 
```

A fully functional parallel zip in Kotlin [(source)](src/main/kotlin/parallelZip/MainKotlin.kt):

```shell
./gradlew runKotlin --args="<out.zip> <file-or-dir> .."
```

Sequential zipping for comparison in pure Java [(source)](src/main/java/parallelZip/Sequential.java):

```shell
./gradlew runSequential --args="<out.zip> <file-or-dir> .."
```

# FatJar and Native Image

| Distributions        | Type                   | Size  |
|----------------------|------------------------|-------|
| fatJar               | Jar file with all deps | 3 MB  |
| GraalVM Native Image | Native executable      | 10 MB |


```shell
# build the fatJar
./gradlew fatJar

# run the fatJar via java -jar
java -jar build/libs/parallel-zip.jar <out.zip> <file-or-dir> ..

# build the GraalVM Native Image executable from the fatJar
<GRAALVM_HOME>/bin/native-image -jar ./build/libs/parallel-zip.jar

# run the GraalVM Native Image executable
./parallel-zip <out.zip> <file-or-dir> ..
```