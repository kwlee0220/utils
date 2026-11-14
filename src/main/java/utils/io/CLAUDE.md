# utils.io

File and stream helpers over `java.nio.file.Files` / `InputStream`, plus a few things the JDK lacks. User guide (Korean): [README.md](README.md).

Users outside this library: `IOUtils` and `FileUtils` everywhere (common, mdt, marmot, jarvey); `FilePath`/`LfsPath` only in jarvey, which implements `FilePath` in `jarvey.HdfsPath` (two identical copies, in `jarvey/` and `jarvey.common/` — change both); `LogTailer` in mdt-manager; `InputStreamFromOutputStream` in marmot.data `PBRecordStreams`; `SuppliableInputStream`/`LimitedInputStream`/`StreamClosedException` in utils.grpc and its copy under `marmot.data/src/main/java/utils/grpc/stream`. marmot and jarvey cannot be built with gradle here — check them with javac (see [../func/CLAUDE.md](../func/CLAUDE.md)).

## Contracts

- **`IOUtils` file I/O** takes the target file first (`readString(File[,Charset])`, `readAllBytes(File)`, `writeString(File, String[, Charset])`, `write(File, byte[])`, `write(File, InputStream)`). No charset ⇒ UTF-8, never the platform default. Methods taking a stream never close it. `copy(InputStream, OutputStream)` / `copy(Reader, Writer)` = `transferTo`. `readFully` throws `EOFException` on a short read; `readUpTo` returns `-1` only when nothing was read. `decompress` throws `DataFormatException` on truncated input or a preset-dictionary stream; `compress`/`decompress` always `end()` the (de)inflater. `readLineUtf8(RandomAccessFile[, requireNewline])` is still public but no longer used by `LogTailer`.
- **`IOUtils.CopyStream`** (`copyAsync`): `bufferSize` must be > 0. `cancelWork()` closes the input stream to wake a blocked `read`; a copy that observes the cancel request (or fails because of that close) ends **cancelled**, not completed. The input is always closed at the end; the output only with `closeOutputStreamOnFinished(true)`.
- **`InputStreamFromOutputStream`**: at pipe EOF it waits for the pump (`waitForFinished`) and decides from its result — completed ⇒ `-1`, failed ⇒ `IOException` (cause as-is if it is an `IOException`; `Error`s rethrown), cancelled ⇒ `IOException` caused by `CancellationException`. A pipe error while the pump has already failed reports the pump's cause with the pipe error suppressed. Contract on hand-written pumps: close the output when done or failed, and finish soon after closing. `of(CheckedConsumer<OutputStream>)` builds a pump that satisfies this (runs on its own thread, closes the output in `finally`).
- **`SuppliableInputStream`**: `supply` returns `false` (chunk dropped) after `endOfSupply`; throws `StreamClosedException` after `close`. `create(n)` requires `n > 0`. Queue is an `ArrayDeque` whose head is the chunk being read (removed when the next one is fetched — `m_current` counts fetched chunks). An interrupted read restores the interrupt flag and throws `InterruptedIOException` with the cause. `toString()` reads under the guard.
- **`LogTailer`**: listener is given to the builder (`listener(...)`, checked in `build()`); `LogTailerListener` is a functional interface (rewind/silence default to `true`). Reads 8KB blocks; `LineAssembler` splits on `\n`/`\r\n`/`\r` across block boundaries and keeps the unterminated tail. Silence = file length unchanged since the previous poll (the first poll never reports silence). `sampleInterval` must be positive. Rewind detection is by length only.
- **`FilePath`**: `streamChildFilePaths()` never returns `null` (`NoSuchFileException` / `NotDirectoryException`); `walkTree` inherits that. `isRegular()` means "name does not start with `_` or `.`" (Hadoop hidden-file rule), not "regular file". `path(String)` is a new path on the same file system, unrelated to `this`. `getParent()` is `@Nullable`. `getTotalLength()` propagates a failing `getLength()` instead of skipping it.
- **`LfsPath`**: `equals`/`hashCode` on `File` (path string). `create(false)` uses `CREATE_NEW` ⇒ `FileAlreadyExistsException`; `create`/`append`/`renameTo` create missing parents with `Files.createDirectories` and fail loudly. `renameTo(dst, false)` leaves the existence check to `Files.move`. `mkdirs()` = `File.mkdirs()`.
- **`FileUtils`**: `walk(start, glob)` follows symlinks, matches the glob against the file name only and skips paths without a name; `walk(start)` does not follow links. Both return an `FStream` wrapping `Files.walk` — callers close it. `findExecutable` returns `FOption<File>` (empty when `PATH` is unset or nothing matches).
- **`EnvironmentFileLoader`**: `KEY=VALUE`, split at the first `=`, key and value trimmed, empty key is an error, surrounding double quotes stripped; `${...}` substitution via `StrSubstitutor`. `load()` returns an insertion-ordered `Map`. No single quotes / `export` / trailing comments.
- **`TempFile`**: `create([prefix, suffix])`, `getPath()`/`getFile()`; `close()` = `Files.deleteIfExists` (failure is an `IOException`).
- **`Serializables`**: only `serialize` / `deserialize(bytes[, ObjectInputFilter])`. Use the filter overload for untrusted input.

## Conventions here

- Static utility classes (`IOUtils`, `FileUtils`, `Serializables`) are `final` with a private constructor that throws `AssertionError`.
- Argument checks use `utils.Preconditions`, not Guava's.

## Tests

`src/test/java/utils/io`: `IOUtilsTest`, `FileUtilsTest`, `LfsPathTest`, `LogTailerTest`, `InputStreamFromOutputStreamTest`, `SuppliableInputStreamTest`, `EnvironmentFileLoaderTest`, `TempFileTest`, `ZipFileTest`.
