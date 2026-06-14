# Markdown Reader

A fast, beautiful, and distraction-free Markdown reader and editor for Windows, built natively with **Java 21** and **JavaFX**.

Markdown Reader was designed to provide a focused environment with deep operating system integration and a modern UI that adapts to your workflow.

## Features

* **Real-Time Background Rendering**: Smooth, non-blocking real-time rendering. When "Real Time" mode is enabled, the editor automatically updates the preview after 4 seconds of typing idle time—with zero screen flashing.
* **Native Windows Integration**: Built-in support for command-line file loading. The application natively supports opening `.md` files via command-line arguments.
* **Elegant Themes**: Full support for both beautiful Light and sleek Dark modes.
* **Smart Navigation**: A dedicated sidebar keeps track of your recently opened files and dynamically generates a Table of Contents for rapid navigation across large documents.
* **Format Conversion**: Easily import existing HTML documents for editing, or export your rendered Markdown directly to an HTML file for web distribution.

## Prerequisites

To build and run this project from source, you will need:
* **JDK 21** or higher.
* **Apache Maven** (3.8+ recommended).

## Building and Running

Clone the repository to your local machine:

```bash
git clone https://github.com/your-username/j-md-reader.git
cd j-md-reader
```

### Run Locally
You can run the application directly via the JavaFX Maven Plugin without packaging it:
```bash
mvn clean compile
mvn javafx:run
```

## Packaging

You can package the application into a standalone "fat JAR" with all dependencies included using the Maven Shade Plugin:

```bash
mvn clean package
```

This will generate an executable JAR file in the `target/` directory: `j-md-reader-1.0-SNAPSHOT-shaded.jar`. 

You can then run the packaged JAR natively:
```bash
java -jar target/j-md-reader-1.0-SNAPSHOT-shaded.jar
```

If you wish to create a native Windows executable or installer, you can use standard tools like the JDK's built-in `jpackage` utility.

## Open Source & Third-Party Licenses

This application is built on the shoulders of giants and relies on several incredible open-source libraries, including **JavaFX**, **CommonMark**, **Flexmark**, **SLF4J**, and **Logback**. 

For a complete list of third-party licenses, copyright notices, and terms, please see the `THIRD-PARTY-LICENSES.txt` file included in this repository.
