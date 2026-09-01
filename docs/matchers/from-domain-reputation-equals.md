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

## Requiring agreement between independent lists

Same pattern as [`IP_REPUTATION_EQUALS`](ip-reputation-equals.md#requiring-agreement-between-independent-lists):
`listIds` on one matcher already takes the worst score across the lists it names, which is fine
for redundant mirrors but means one list alone is enough to match. To require agreement between
independently-maintained sources instead, use two separate `FROM_DOMAIN_REPUTATION_EQUALS`
nodes combined with `AND`/`OR` — see the [starter config](../../config.example.json), which does
exactly this with [HaGeZi's TIF mini](https://github.com/hagezi/dns-blocklists) and the
[Blocklist Project](https://github.com/blocklistproject/Lists)'s phishing list: both agreeing
routes to Spam pre-marked read, only one matching routes to Spam left unread for review.

## Not every domain list means "malicious"

The starter config also uses this matcher standalone against two lists that aren't
malware/phishing feeds — being on either doesn't mean the sender is malicious, just unusual
enough to warrant a look, so both route to Spam left unread rather than pre-marked read:

- [disposable-email-domains](https://github.com/disposable-email-domains/disposable-email-domains)
  — throwaway-mail providers, anonymous and easily re-created.
- [HaGeZi's NRD7](https://github.com/hagezi/nrd) — domains registered in the last 7 days. A
  brand new domain emailing you out of nowhere is unusual; it's also, by definition, not proof
  of anything on its own. **Much bigger than the other lists here** (~2.5M entries, ~40MB) — see
  [Reputation lists](../README.md#reputation-lists) for the memory/refresh cost before enabling
  it.
