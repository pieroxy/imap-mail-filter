# Matcher: `FROM_DOMAIN_REPUTATION_EQUALS`

[← back to docs](../README.md)

Like [`IP_REPUTATION_EQUALS`](ip-reputation-equals.md), but on the domain of the `From:` header
instead of the connecting IP, against `DOMAIN` [reputation lists](../README.md#reputation-lists).

## Config fields

| Field | Required | Description |
|---|---|---|
| `listIds` | yes | Set of `id`s from `reputationLists` to check against. Must all be of type `DOMAIN`. |
| `key` | yes | A comparison: an operator (`>`, `>=`, `<`, or `<=`) followed by a number between 0 and 1, e.g. `">0.5"`. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## How the check works

1. The message must have exactly one `From:` address — like [`FROM_DOMAIN_EQUALS`](from-domain-equals.md),
   a message with zero or multiple `From:` addresses never matches.
2. The domain is compared **exactly** (case-insensitively) against each referenced list's
   entries — no parent-domain fallback. A list entry for `example.com` does not cover
   `mail.example.com`.
3. If found in more than one referenced list, the highest (worst) `score` among the matches is
   used, then compared to the threshold in `key`.

Same trust model as [`IP_REPUTATION_EQUALS`](ip-reputation-equals.md#never-a-live-query): the
lists are downloaded and refreshed once for the whole process, never queried live per message.
Not learnable by example, for the same reason.

## Example

```json
{
  "matcher": { "type": "FROM_DOMAIN_REPUTATION_EQUALS", "listIds": ["known-spam-domains"], "key": ">0.5" },
  "action": { "type": "MOVE_TO", "key": "Spam" }
}
```
