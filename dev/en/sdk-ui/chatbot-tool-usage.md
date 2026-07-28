# SDK UI: Tool interaction

[简体中文](../../zh-CN/sdk-ui/chatbot-tool-usage.md) | English

A tool is stored as a dynamic `tool-${toolName}` part. Render its state:

- `input-streaming`
- `input-available`
- `approval-requested`
- `approval-responded`
- `output-available`
- `output-denied`
- `output-error`

## Client tools

```ts
const chat = useChat({
    transport,
    async onToolCall(part) {
        if (part.toolName !== "get_location") return;
        try {
            const output = await getLocation(part.input);
            await chat.addToolOutput({
                toolCallId: part.toolCallId,
                output,
            });
        } catch (error) {
            await chat.addToolOutput({
                toolCallId: part.toolCallId,
                state: "output-error",
                errorText: String(error),
            });
        }
    },
});
```

`onToolCall` fires once when a tool first reaches `input-available`. Its return value is not
automatically used as tool output.

## Approval

```ts
await chat.addToolApprovalResponse({
    approvalId: part.approval?.id,
    approved: true,
});

await chat.rejectToolCall({
    id: part.approval?.id,
    reason: "The user declined this action",
});
```

A denial is `approval-responded`, not an execution error. Preserve the same tool call and approval
IDs.

## Automatic continuation

Continuation is opt-in:

```ts
const chat = useChat({
    transport,
    sendAutomaticallyWhen({ messages }) {
        return lastAssistantMessageHasCompletedToolContinuations({ messages });
    },
    maxAutomaticSteps: 4,
});
```

Other predicates cover complete tool calls and responded approvals. Automatic continuation waits
for the active stream to finish, deduplicates the same terminal tool state, and stops at the
configured step limit.

Providers may stream `tool-input-start` / delta / available, or emit only the final available
input. Support both. When several tools appear, complete every required result or approval before
continuing.
