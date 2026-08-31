# Action: `AND`

[← back to docs](../README.md)

Composite action: runs its children **in order**, stopping at the first one that fails.

## Config fields

| Field | Required | Description |
|---|---|---|
| `children` | yes | List of nested action configs (`type` + whatever fields that type needs), run in list order. |
| `logLevel` | no | See [Logging](../README.md#logging). Applies only to this `AND` node itself, not to its children — set `logLevel` on each child individually if you need to trace them. |

## Behavior

- With zero children, `AND` "succeeds" vacuously (does nothing).
- This is exactly how [`MOVE_TO_AND_READ`](move-to-and-read.md) is built internally
  (`READ` then `MOVE_TO`) — reach for a manual `AND` when you need a sequence `MOVE_TO_AND_READ`
  doesn't cover.
- Not learnable: `AND`/`OR` are reserved for manual `config.json` rules.

## Example

```json
{
  "matcher": { "type": "SPF_RESULT_EQUALS", "key": "fail" },
  "action": {
    "type": "AND",
    "children": [
      { "type": "READ" },
      { "type": "MOVE_TO", "key": "Spam" }
    ]
  }
}
```
