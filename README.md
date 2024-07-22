# Parallel Zip on JVM

Zipping tons of files on one core in a multicore/SSD/cloud era is a massive waste of time.

A zip file is just an array of entries and a central directory at the end of a file.

We cannot write to a zip file in parallel, but we can compress data in parallel in memory.


# Algorithm

1. Collect all zip entries and their bytes for each input file in parallel.
   For each input file:
   - Get a `ByteArrayOutputStream` and a `ZipOutputStream` on top of it
   - Write an entry to a zip stream. Do not close it to avoid writing an unneeded central directory
   - Get the bytes from the byte stream

```java
      var zipEntries = ConcurrentHashMap<ZipEntry, byte[]>();

      // for each input file in parallel:
      var out = new ByteArrayOutputStream();
      var zipEntry = new ZipEntry(filePathRelativeToZipRoot);
      var zip = new ZipOutputStream(out);
      try (var fileStream = Files.newInputStream(filePath)) {
        zip.putNextEntry(zipEntry);
        fileStream.transferTo(zip);
        zip.closeEntry();
      }
      zipEntries.put(zipEntry, out.toByteArray());
```

2. Write all entries and bytes sequentially to a target zip file:
   - Get a `FileOutputStream` and a `ZipOutputStream` on top of it
   - Write bytes of all entries to a file stream updating zip stream state
   - Write the central directory by closing the zip stream

```java
    try (var os = Files.newOutputStream(zipFile)) {
      var zip = new ZipOutputStream(os);
      var offset = 0L;
      for (Map.Entry<ZipEntry, byte[]> o : zipEntries.entrySet()) {
        var zipEntry = o.getKey();
        var bytes = o.getValue();
        zip.xEntries.add(new XEntry(zipEntry, offset)); // via reflection
        os.write(bytes);
        offset += bytes.length;
      }
      zip.offset = offset; // via reflection
      zip.close();
    }
```

**Note 1**: Java Reflection is used to work around missing Java API.
**To avoid that in the future, we must request such an API.**

**Note 2**: We can merge zips without repacking using the same technique.

# Results

Zipping `12.06 GB of 175,866 items` to a `1.14 GB` zip file on a MacBook M2 Max in seconds:

| Mode       | Seconds |
|------------|---------|
| Sequential |     174 |
| Parallel   |      31 |



A fully functional parallel zip in pure Java ([full source](src/main/java/parallelZip/MainJava.java)):

```shell
gradle runJava <out.zip> <file-or-dir> .. 
```

A fully functional parallel zip in Kotlin ([full source](src/main/kotlin/parallelZip/MainKotlin.kt)):

```shell
gradle runKotlin <out.zip> <file-or-dir> ..
```
