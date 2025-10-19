# Inqwise Async - Blocking to Vert.x Non-Blocking Bridge

[![CI](https://github.com/inqwise/inqwise-async/actions/workflows/ci.yml/badge.svg)](https://github.com/inqwise/inqwise-async/actions/workflows/ci.yml)
[![Release](https://github.com/inqwise/inqwise-async/actions/workflows/release.yml/badge.svg)](https://github.com/inqwise/inqwise-async/actions/workflows/release.yml)
[![CodeQL](https://github.com/inqwise/inqwise-async/actions/workflows/codeql.yml/badge.svg)](https://github.com/inqwise/inqwise-async/actions/workflows/codeql.yml)
[![Codecov](https://codecov.io/gh/inqwise/inqwise-async/branch/master/graph/badge.svg)](https://codecov.io/gh/inqwise/inqwise-async)
[![Snyk Security](https://github.com/inqwise/inqwise-async/actions/workflows/snyk.yml/badge.svg)](https://github.com/inqwise/inqwise-async/actions/workflows/snyk.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.inqwise/inqwise-async.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.inqwise%22%20AND%20a:%22inqwise-async%22)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://openjdk.java.net/projects/jdk/21/)
[![Vert.x](https://img.shields.io/badge/Vert.x-5.0.4%2B-purple.svg)](https://vertx.io/)
Inqwise Async bridges standard Java blocking I/O structures with Vert.x's asynchronous, non-blocking APIs. By providing robust wrappers for traditional Java classes such as [`InputStream`](https://docs.oracle.com/javase/8/docs/api/java/io/InputStream.html), [`OutputStream`](https://docs.oracle.com/javase/8/docs/api/java/io/OutputStream.html), [`Reader`](https://docs.oracle.com/javase/8/docs/api/java/io/Reader.html), [`Writer`](https://docs.oracle.com/javase/8/docs/api/java/io/Writer.html), and more, Inqwise Async enables developers to integrate existing Java codebases into Vert.x-based reactive applications without disrupting the event loop. This bridge ensures efficient and scalable I/O operations by managing back-pressure and flow control, facilitating the modernization of legacy systems for high-performance, event-driven environments.

## Table of Contents

- [Introduction](#introduction)
- [Features](#features)
- [Installation](#installation)
  - [Maven Dependency](#maven-dependency)
- [Usage](#usage)
  - [Stream Package](#stream-package)
- [Examples](#examples)
  - [Wrapping an InputStream as a ReadStream](#wrapping-an-inputstream-as-a-readstream)
  - [Wrapping an OutputStream as a WriteStream](#wrapping-an-outputstream-as-a-writestream)
- [Contributing](#contributing)
- [License](#license)

## Introduction

Inqwise Async is a Java library designed to seamlessly integrate standard blocking I/O operations with Vert.x's non-blocking, asynchronous framework. It provides wrappers for Java's traditional I/O classes, allowing developers to incorporate existing blocking code into reactive applications without compromising the performance and scalability that Vert.x offers. This integration is crucial for modernizing legacy systems and ensuring efficient resource management in high-throughput environments.

## Features

- **Seamless Integration**: Wraps standard Java I/O classes (`InputStream`, `OutputStream`, `Reader`, `Writer`) as Vert.x `ReadStream` and `WriteStream`.
- **Non-Blocking Operations**: Ensures that blocking I/O does not hinder the Vert.x event loop, maintaining application responsiveness.
- **Back-Pressure Management**: Handles flow control to prevent overwhelming the system with data.
- **Thread-Safe Execution**: Utilizes `ExecutorService` to manage blocking operations in separate threads.
- **Easy to Use API**: Provides intuitive methods for wrapping and interacting with I/O streams.
- **Extensible Design**: Can be extended to support additional I/O classes and functionalities as needed.

## Installation

### Maven Dependency

To include the Inqwise Async library in your Maven project, add the following dependency to your `pom.xml` file:

```xml
<dependency>
    <groupId>com.inqwise</groupId>
    <artifactId>inqwise-async</artifactId>
    <version>${latest.version}</version>
</dependency>
```

Ensure that your project's repositories are correctly configured. If the library is available on [Maven Central](https://search.maven.org/), no additional repository configuration is required.

**Note**: Replace the version number with the latest version if a newer one is available.

## Usage

Inqwise Async provides straightforward methods to wrap Java's blocking I/O streams into Vert.x's non-blocking streams. Below is a guide on how to utilize these wrappers effectively.

### Stream Package

The `stream` package within Inqwise Async offers classes that encapsulate Java's I/O streams and expose them as Vert.x `ReadStream` and `WriteStream`. This allows for asynchronous data processing and integration with Vert.x's reactive pipelines.

#### Key Classes:

- `AsyncReadStream`: Wraps a standard `InputStream` and exposes it as a Vert.x `ReadStream<Buffer>`.
- `AsyncWriteStream`: Wraps a standard `OutputStream` and exposes it as a Vert.x `WriteStream<Buffer>`.

These classes manage the execution of blocking operations using a dedicated `ExecutorService`, ensuring that the Vert.x event loop remains unblocked.

## Examples

### Wrapping an InputStream as a ReadStream

```java
import com.inqwise.async.stream.AsyncReadStream;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;

import java.io.FileInputStream;
import java.io.InputStream;

public class ReadStreamExample {
    public static void main(String[] args) throws Exception {
        Vertx vertx = Vertx.vertx();
        InputStream inputStream = new FileInputStream("example.txt");
        ReadStream<Buffer> readStream = new AsyncReadStream(vertx, inputStream);

        readStream.handler(buffer -> {
            System.out.println("Received data: " + buffer.toString());
        }).exceptionHandler(err -> {
            System.err.println("Error: " + err.getMessage());
        }).endHandler(v -> {
            System.out.println("Stream has ended.");
            vertx.close();
        });
    }
}
```

**Explanation:**

1. **Initialization**: Create a Vert.x instance and an `InputStream` pointing to your source file.
2. **Wrapping**: Instantiate `AsyncReadStream` with Vert.x and the `InputStream`.
3. **Handlers**:
   - **Data Handler**: Processes incoming data asynchronously.
   - **Exception Handler**: Handles any errors during the read operation.
   - **End Handler**: Notifies when the stream has been fully read or closed.

### Wrapping an OutputStream as a WriteStream

```java
import com.inqwise.async.stream.AsyncWriteStream;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;

import java.io.FileOutputStream;
import java.io.OutputStream;

public class WriteStreamExample {
    public static void main(String[] args) throws Exception {
        Vertx vertx = Vertx.vertx();
        OutputStream outputStream = new FileOutputStream("output.txt");
        WriteStream<Buffer> writeStream = new AsyncWriteStream(vertx, outputStream);

        writeStream.exceptionHandler(err -> {
            System.err.println("Error: " + err.getMessage());
        });

        Buffer data = Buffer.buffer("Hello, Inqwise Async!");
        writeStream.write(data).onComplete(ar -> {
            if (ar.succeeded()) {
                System.out.println("Data written successfully.");
            } else {
                System.err.println("Failed to write data: " + ar.cause().getMessage());
            }
        });

        writeStream.end(ar -> {
            if (ar.succeeded()) {
                System.out.println("Stream closed successfully.");
            } else {
                System.err.println("Failed to close stream: " + ar.cause().getMessage());
            }
            vertx.close();
        });
    }
}
```

**Explanation:**

1. **Initialization**: Create a Vert.x instance and an `OutputStream` pointing to your destination file.
2. **Wrapping**: Instantiate `AsyncWriteStream` with Vert.x and the `OutputStream`.
3. **Handlers**:
   - **Exception Handler**: Handles any errors during the write operation.
   - **Write Operation**: Writes data asynchronously and notifies upon completion.
   - **End Handler**: Closes the stream and notifies upon successful closure or failure.

## Contributing

Contributions are welcome! If you'd like to contribute to the project, please follow these steps:

1. Fork the repository.
2. Create a new branch for your feature or bug fix.
3. Commit your changes with clear commit messages.
4. Submit a pull request describing your changes.

Please ensure that your code adheres to the project's coding standards and includes appropriate tests.

## License

This project is licensed under the [MIT License](LICENSE).

---

If you have any questions or need further assistance, feel free to open an issue or contact the maintainers.
