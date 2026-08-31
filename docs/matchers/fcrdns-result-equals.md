# Matcher: `FCRDNS_RESULT_EQUALS`

[← back to docs](../README.md)

Matches when a **live-evaluated** FCrDNS (Forward-Confirmed reverse DNS) result equals the
configured value. **Case-insensitive.**

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | Result to match: `pass`, `fail`, `none`, or `temperror`. |
| `keys` | one of `key`/`keys` | Set of results, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## What this actually checks

Unlike [`SPF_RESULT_EQUALS`](spf-result-equals.md), [`DKIM_RESULT_EQUALS`](dkim-result-equals.md),
and [`DMARC_RESULT_EQUALS`](dmarc-result-equals.md), **this is not a domain authentication
standard** — there's no RFC defining a pass/fail vocabulary for it, and it says nothing about
the sender's claimed domain (`From:`/`Return-Path`). It only checks whether the IP that
connected to deliver the message has a properly configured reverse DNS entry:

1. **PTR lookup**: reverse-resolve the connecting IP (the same IP `SPF_RESULT_EQUALS` reads
   from the topmost `Received:` header) to get its hostname(s).
2. **Forward confirmation**: for each hostname found, resolve it forward (A or AAAA) and check
   whether any of the addresses returned match the original IP. A PTR record alone proves
   nothing — anyone who controls an IP block's reverse DNS zone can put any hostname there.
   Confirming it forward (which requires controlling that hostname's own DNS zone) is what
   makes the pairing meaningful.

A real, properly administered mail server almost always has a forward-confirmed PTR. Spam
sources — compromised home PCs, cheap dynamic-IP VPS ranges — often don't, or have a
generic ISP-assigned reverse name. But a forward-confirmed PTR only speaks to the
**infrastructure's** legitimacy, not to who the mail claims to be from: a spammer with a
properly configured VPS can pass FCrDNS while forging any `From:` address they like.

## Result values

| Value | Meaning |
|---|---|
| `pass` | The IP has a PTR record, and at least one of its hostnames resolves back to the same IP. |
| `fail` | The IP has one or more PTR records, but none of them resolve back to it. |
| `none` | The IP has no PTR record at all. |
| `temperror` | A DNS lookup (PTR or the forward confirmation) failed temporarily. |

## Example

```json
{
  "matcher": { "type": "FCRDNS_RESULT_EQUALS", "key": "none", "logLevel": "DEBUG" },
  "action": { "type": "MOVE_TO", "key": "Spam" }
}
```
