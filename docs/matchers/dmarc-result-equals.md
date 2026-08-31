# Matcher: `DMARC_RESULT_EQUALS`

[← back to docs](../README.md)

Matches when a **live-evaluated** DMARC (RFC 7489) result equals the configured value.
**Case-insensitive.**

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | DMARC result to match: `pass`, `fail`, `none`, `permerror`, or `temperror`. |
| `keys` | one of `key`/`keys` | Set of results, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## What DMARC actually checks

DMARC doesn't verify anything new by itself — it **ties SPF and DKIM together**. A message can
pass SPF and pass DKIM individually while still failing DMARC, if neither of the domains those
checks actually verified matches the domain the recipient *sees* in `From:`. That's the gap
DMARC closes: it recomputes [SPF](spf-result-equals.md) and [DKIM](dkim-result-equals.md) live
(same policy as those matchers — never trusting a pre-existing header) and checks whether either
one is **aligned** with the `From:` domain:

- **SPF alignment**: does the domain SPF actually verified (`Return-Path`, or `From` as a
  fallback — see [`SPF_RESULT_EQUALS`](spf-result-equals.md)) match the `From:` domain?
- **DKIM alignment**: does the signing domain (`d=`) of any *passing* DKIM signature match the
  `From:` domain?

DMARC passes if **either** SPF passed-and-aligned, **or** DKIM passed-and-aligned — only one is
required.

"Match" itself has two modes, set per-domain by the `adkim=`/`aspf=` tags in the domain's own
DMARC record (default: relaxed for both):

- **relaxed** (default): the two domains just need to share the same *organizational domain* —
  computed properly via the [Public Suffix List](https://publicsuffix.org/) (e.g.
  `mail.example.co.uk` and `example.co.uk` are aligned; a naive "last two labels" comparison
  would get this wrong and wrongly treat unrelated domains sharing a public suffix like
  `*.github.io` as aligned).
- **strict**: the two domains must match exactly.

## How the record is found

The `_dmarc.<domain>` TXT record is looked up for the exact `From:` domain first; if absent,
the organizational domain's `_dmarc.<org-domain>` record is tried as a fallback (RFC 7489
§6.6.3) — so a subdomain without its own DMARC record still inherits its organization's policy.

## Result values

| Value | Meaning |
|---|---|
| `pass` | SPF or DKIM passed **and** was aligned with `From:`. |
| `fail` | A DMARC record exists, but neither SPF nor DKIM passed-and-aligned. |
| `none` | No DMARC record is published for the domain (or its organizational domain). |
| `permerror` | The published record is malformed (missing the required `p=` tag), or ambiguous (more than one TXT record found). |
| `temperror` | A DNS lookup failed temporarily. |

Unlike SPF/DKIM, this matcher doesn't currently expose the domain's published *policy*
(`p=none/quarantine/reject`) — only whether the mail itself passed or failed DMARC. Whether to
act on a `fail` is entirely up to your own rule, same as for SPF/DKIM.

## Example

```json
{
  "matcher": { "type": "DMARC_RESULT_EQUALS", "key": "fail", "logLevel": "DEBUG" },
  "action": { "type": "MOVE_TO", "key": "Spam" }
}
```
