# Matcher: `AND`

[← back to docs](../README.md)

Composite matcher: matches only if **every** child matcher matches. Evaluation short-circuits
at the first child that doesn't match.

## Config fields

| Field | Required | Description |
|---|---|---|
| `children` | yes | List of nested matcher configs (`type` + whatever fields that type needs). |
| `logLevel` | no | See [Logging](../README.md#logging). Applies only to this `AND` node itself, not to its children — set `logLevel` on each child individually if you need to trace them. |

## Behavior

- With zero children, `AND` matches vacuously (always true) — not a useful configuration in
  practice, but not an error either.
- Not learnable: `AND`/`OR` are reserved for manual `config.json` rules, since "teach me an AND
  of several conditions from one example message" isn't a well-defined operation.

## Example

Only match a domain when its DKIM signature actually checks out:

```json
{
  "matcher": {
    "type": "AND",
    "children": [
      { "type": "FROM_DOMAIN_EQUALS", "key": "newsletter.example.com" },
      { "type": "DKIM_RESULT_EQUALS", "key": "pass" }
    ]
  },
  "action": { "type": "MOVE_TO", "key": "Newsletters" }
}
```
