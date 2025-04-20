# Maven Indexer CLI

**Maven Indexer CLI** is a Kotlin-based command-line utility that crawls and indexes artifacts
from [Maven Central](https://repo1.maven.org/maven2/), storing them in a local SQLite database. It supports full and
partial indexing, version resolution, and full text search capabilities using FTS5.

---

## 🚀 How to Run

Run the CLI:

```bash
./gradlew run --args="<your-command-args>"
```

Or run the generated jar:

```bash
java -jar build/libs/maven-indexer-cli.jar <your-command-args>
```

---

## 📦 CLI Commands

You can combine multiple commands and specify logging level with `--logLevel`.

### `-i`, `--index`

Index all dependencies from Maven Central.
This method will crawl through each artifact in Maven Central and get its meta-information.

**Estimated time: ~40 minutes.**

---

### `-ig <groupId>`, `--indexGroup <groupId>`

Index all dependencies for a particular groupId from Maven Central.

* Example input: `io.ktor`

This method can be used if you don't wish to wait until the whole Maven Central finishes indexing, and should work
significantly faster than `index`.

---

### `-ia <group:artifactId>`, `--indexArtifact <group:artifactId>`

Index a single artifact.

* Input format: `group:artifactId`
* Example input: `foo.bar:xyz`

---

### `-ic <filePath>`, `--indexFromCsv <filePath>`

Index from a CSV file.  
The CSV should contain two columns: `namespace` (groupId) and `name` (artifactId).  
This will index all listed artifacts as well as their KMP targets.

For example, for a row with `ktor-network`, it will index:

- `ktor-network-js`
- `ktor-network-jvm`
- `ktor-network-iosx64`, etc.

---

### `-r`, `--refresh`

Refresh already indexed artifact versions.  
This action is faster than re-indexing the entire Maven Central and can be performed periodically.

* **Estimated time: ~15 minutes.**

---

### `-s <query>`, `--search <query>`

Performs local search for an artifact by name (either group or artifactId).
You should `index` the project before running search, as otherwise local storage will be empty.

* Example input:
    * `kotlin`
    * `mockito android`
    * `io.ktor:ktor-network`

---

### `-t <group:artifactId>`, `--targets <group:artifactId>`

Find a single artifact’s available **Kotlin Multiplatform Targets**.  
You should `index` the project before running search, as otherwise local storage will be empty.

Input format: `group:artifactId`

---

### `-v <group:artifactId>`, `--versions <group:artifactId>`

List all available versions of an artifact.  
You should `index` the project before running search, as otherwise local storage will be empty.

Input format: `group:artifactId`

---

### `-l <level>`, `--logLevel <level>`

Specifies desired log level.  
Available values: `debug`, `info`, `warn`, `error` (default: `warn`)

---

## ⚙️ Technical Details

This program uses asynchronous parallelism to speed up crawling and indexing, and relies on FTS5 for efficient text
search by artifact and group identifiers.
The system ensures thread-safe storage operations, supports extraction of
Kotlin Multiplatform targets, and provides structured progress reporting during indexing.

The tool performs asynchronous breadth-first traversal of the Maven Central repository, starting from the root
directory. It follows all nested paths, collecting URLs that point to artifact directories. For each valid artifact
path—identified by the presence of a `maven-metadata.xml` file—it extracts the group ID and artifact ID from the
directory structure. These coordinates are used to download and parse metadata containing all available versions. The
artifact and its versions are then stored in a local database.

---

## 🧰 Dependencies

| Library                      | Purpose                       |
|------------------------------|-------------------------------|
| `Exposed`                    | ORM layer for SQLite          |
| `sqlite-jdbc`                | Embedded SQLite support       |
| `ktor-client-cio`            | Asynchronous HTTP client      |
| `kotlinx.cli`                | CLI argument parsing          |
| `OpenCSV`                    | Parsing CSV input             |
| `io.github.z4kn4fein:semver` | SemVer parsing and comparison |
| `mockito`, `junit`           | Unit testing support          |

---

## 🛠️ Future Improvements

- Optimize indexing time, especially for a huge amount of data.
- Implement fuzzy search with mistakes (currently it is not supported).
- Support better KMP targets extraction, as now it relies on text matches, and not on gradle metadata.
- Add better error handling logic and save indexing execution data in log files.
- Introduce better user interface and IDE integrations.
- Introduce DI framework for easier instance creation and lifecycle control
