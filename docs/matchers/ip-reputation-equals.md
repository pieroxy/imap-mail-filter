# Matcher: `IP_REPUTATION_EQUALS`

[← back to docs](../README.md)

Matches when the connecting IP's reputation score — the worst (highest) score among the
referenced `IP_CIDR` [reputation lists](../README.md#reputation-lists) that contain it — crosses
a threshold.

## Config fields

| Field | Required | Description |
|---|---|---|
| `listIds` | yes | Set of `id`s from `reputationLists` to check against. Must all be of type `IP_CIDR`. |
| `key` | yes | A comparison, not a value to match: an operator (`>`, `>=`, `<`, or `<=`) immediately followed by a number between 0 and 1, e.g. `">0.5"` or `"<=0.1"`. |
| `logLevel` | no | See [Logging](../README.md#logging). |

There is no `keys` for this matcher — a threshold is a single comparison, not a set of values.

## Never a live query

Like [`SPF_RESULT_EQUALS`](spf-result-equals.md) and the other protocol matchers, this never
contacts anything per message. The lists referenced by `listIds` are downloaded and kept
refreshed once for the whole process (see [Reputation lists](../README.md#reputation-lists)) —
checking a message against them is a pure in-memory lookup. No IP, domain, or message content is
ever sent to a third party while a message is being evaluated.

## How the check works

1. The connecting IP is read the same way as [`SPF_RESULT_EQUALS`](spf-result-equals.md#how-the-check-works)
   does: the topmost (most recent) `Received:` header, added by your own IMAP server.
2. Each list in `listIds` is checked for that IP (IPv4 only — IPv6 entries in a list, or an IPv6
   connecting address, never match). If found in more than one, the **highest** (worst) `score`
   among the matches is used.
3. That score is compared to the threshold in `key`.

If the connecting IP can't be determined, or it isn't present in any referenced list, the
matcher simply doesn't match — it never throws.

## Not learnable by example

Like the other protocol/reputation matchers, this isn't in the learnable list: reputation comes
from external lists, not from a value pulled out of a dropped example message.

## Example

```json
{
  "matcher": { "type": "IP_REPUTATION_EQUALS", "listIds": ["spamhaus-drop"], "key": ">0.5" },
  "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
}
```

## Requiring agreement between independent lists

`listIds` on a single matcher already picks the **worst** score across the lists it names — fine
when the lists are redundant (e.g. mirrors of the same feed), but it means one list alone is
enough to match. To instead require **agreement between independent sources** (a stronger
signal — see the [starter config](../../config.example.json)), use two separate
`IP_REPUTATION_EQUALS` nodes, one per list, combined with `AND`/`OR` like any other matcher:

```json
[
  {
    "matcher": {
      "type": "AND",
      "children": [
        { "type": "IP_REPUTATION_EQUALS", "listIds": ["spamhaus-drop"], "key": ">0.5" },
        { "type": "IP_REPUTATION_EQUALS", "listIds": ["blocklist-de-mail"], "key": ">0.5" }
      ]
    },
    "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
  },
  {
    "matcher": {
      "type": "OR",
      "children": [
        { "type": "IP_REPUTATION_EQUALS", "listIds": ["spamhaus-drop"], "key": ">0.5" },
        { "type": "IP_REPUTATION_EQUALS", "listIds": ["blocklist-de-mail"], "key": ">0.5" }
      ]
    },
    "action": { "type": "MOVE_TO", "key": "Spam" }
  }
]
```

The `AND` rule (both agree) must come first: since rule evaluation stops at the first match (see
[Rule evaluation order](../README.md#rule-evaluation-order)), an IP flagged by both lists hits it
and stops there pre-marked read; one flagged by only one list falls through to the `OR` rule and
is left unread for review.
