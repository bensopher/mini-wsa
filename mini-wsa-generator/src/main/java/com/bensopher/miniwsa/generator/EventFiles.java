package com.bensopher.miniwsa.generator;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Writes events to files of at most one batch each, instead of sending them. */
final class EventFiles {

    enum Format {
        /** One JSON array per file: each file can be POSTed to the ingest API as it is. */
        array,
        /** One event per line, for tools that stream records. */
        ndjson
    }

    private final JsonMapper json;

    EventFiles(JsonMapper json) {
        this.json = json;
    }

    /** Returns the files written, in order. */
    List<Path> write(List<List<GeneratedEvent>> batches, Path directory, Format format) throws IOException {
        Files.createDirectories(directory);
        String extension = format == Format.array ? "json" : "ndjson";
        List<Path> files = new ArrayList<>(batches.size());
        for (int i = 0; i < batches.size(); i++) {
            List<GeneratedEvent> batch = batches.get(i);
            String content = format == Format.array
                    ? json.writeValueAsString(batch)
                    : batch.stream().map(json::writeValueAsString).collect(Collectors.joining("\n", "", "\n"));
            Path file = directory.resolve("events-%05d.%s".formatted(i + 1, extension));
            Files.writeString(file, content);
            files.add(file);
        }
        return files;
    }
}
