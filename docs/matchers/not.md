# Matcher: `NOT`

[← back to docs](../README.md)

Composite matcher: matches if its single child matcher does **not**.

## Config fields

| Field | Required | Description |
|---|---|---|
| `children` | yes | Exactly one nested matcher config (`type` + whatever fields that type needs). |
| `logLevel` | no | See [Logging](../README.md#logging). Applies only to this `NOT` node itself, not to its child. |

## Behavior

- Requires **exactly one** child. Zero children (negating nothing) or more than one (negating
  which one?) both fail at startup rather than being silently misread.
- Not learnable: reserved for manual `config.json` rules, same reasoning as `AND`/`OR`.
- Many leaf matchers already support negation more directly — e.g. `SPF_RESULT_EQUALS` against
  `"pass"` combined with `NOT` says "anything but a pass", which might read more naturally as
  whichever specific failure statuses you actually care about. Reach for `NOT` when the condition
  you want to exclude is itself a composite (`AND`/`OR`), or otherwise awkward to phrase directly.

## Example

Move to Spam anything that isn't from a trusted domain and fails DKIM:

```json
{
  "matcher": {
    "type": "AND",
    "children": [
      { "type": "NOT", "children": [{ "type": "FROM_DOMAIN_EQUALS", "key": "trusted.example.com" }] },
      { "type": "DKIM_RESULT_EQUALS", "key": "fail" }
    ]
  },
  "action": { "type": "MOVE_TO", "key": "Spam" }
}
```
