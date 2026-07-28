# SDK UI: Custom data and message metadata

[简体中文](../../zh-CN/sdk-ui/streaming-data-and-metadata.md) | English

## Custom data

Java stream writers can emit persistent or transient application data:

```java
UIMessageStream stream = UIMessageStreams.createWithOptions(options -> options
    .execute(writer -> {
        writer.writeTransientData("status", "retrieving");
        writer.writeData("sources", sources);
        writer.merge(modelStream.toUIMessageStream());
    }));
```

Persistent `data-*` chunks become `DataPart`; transient data is delivered to `onData` but not
stored in `message.parts`. Reusing the same type and ID replaces the persistent part.

Validate data on the frontend:

```ts
const chat = useChat({
    transport,
    dataPartSchemas: {
        status: {
            safeParse: (value) =>
                typeof value === "string"
                    ? { success: true, data: value }
                    : { success: false, error: { message: "Expected a string" } },
        },
    },
});
```

The schema key is the data name, so `data-status` uses `status`.

## Message metadata

`writeMessageMetadata(update)` modifies message-level metadata without creating a part. Configure
`messageMetadataSchema` on `Chat`, `useChat`, or the direct reader. Java
`UIMessageMetadataMerger` controls typed merge behavior; map metadata is overlaid by default.

Metadata is application state, not a location for provider credentials. Usage, finish reasons,
and provider metadata are not automatically promoted to message metadata.

## Sources and files

Use standard source chunks for citations. URL sources become `SourceUrlPart`; other sources become
`SourceDocumentPart`. RAG can expose display-safe sources and, only when explicitly configured,
retrieved data.

`writer.writeFile(fileId, generatedFile)` maps a generated image URL or base64 value to
`FilePart`. Attachment storage, URL lifetime, access control, and cleanup belong to the consumer
plugin.
