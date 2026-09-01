# Matcher: `DMARC_POLICY_EQUALS`

[← back to docs](../README.md)

Matches the **effective DMARC policy** published by the sender's domain — not whether this
particular message passed or failed. **Case-insensitive.**

## Config fields

| Field | Required | Description |
|---|---|---|
| `key` | one of `key`/`keys` | Policy to match: `none`, `quarantine`, `reject`, `unpublished`, `permerror`, or `temperror`. |
| `keys` | one of `key`/`keys` | Set of values, any of which matches. Takes priority over `key` if both are set. |
| `logLevel` | no | See [Logging](../README.md#logging). |

## Why this is separate from `DMARC_RESULT_EQUALS`

[`DMARC_RESULT_EQUALS`](dmarc-result-equals.md) tells you whether *this message* passed or
failed. It doesn't tell you how much the domain itself vouches for that verdict — and that
matters:

- A domain publishing `p=reject` has explicitly said "if a message claiming to be from us
  doesn't align, it isn't us — reject it." Banks, payment processors, and most large SaaS
  providers publish this. A `fail` from one of them is about as close to "certainly spoofed" as
  a filter can get without opening the message.
- A domain publishing `p=none` is only monitoring — often because they haven't finished
  rolling out SPF/DKIM everywhere yet. A `fail` from them can just mean "this particular
  sending path isn't covered yet," not "this message is forged."

Combine the two matchers with `AND` to act on `fail` only when the domain has actually
committed to a strict policy, rather than treating every `fail` the same:

```json
{
  "matcher": {
    "type": "AND",
    "children": [
      { "type": "DMARC_RESULT_EQUALS", "key": "fail" },
      { "type": "DMARC_POLICY_EQUALS", "key": "reject" }
    ]
  },
  "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
}
```

## How the policy is resolved

Same record lookup as [`DMARC_RESULT_EQUALS`](dmarc-result-equals.md#how-the-record-is-found):
the exact `From:` domain's `_dmarc.<domain>` record is used if it exists; otherwise the
organizational domain's record is used as a fallback (RFC 7489 §6.6.3) — in which case its
`sp=` tag applies (falling back to `p=` if `sp=` is absent), not `p=` directly, since `sp=` is
specifically the policy for subdomains that don't publish their own record.

## Result values

| Value | Meaning |
|---|---|
| `none` | The domain publishes DMARC but its policy is `p=none` (monitor only) — an explicit, active choice. |
| `quarantine` | The domain asks that misaligned mail be quarantined (e.g. spam-folder it). |
| `reject` | The domain asks that misaligned mail be rejected outright. |
| `unpublished` | The domain (and its organizational domain) has **no** DMARC record at all. |
| `permerror` | The published record is malformed, or ambiguous (more than one TXT record found), or `p=`/`sp=` has a value other than `none`/`quarantine`/`reject`. |
| `temperror` | A DNS lookup failed temporarily. |

`unpublished` is deliberately **not** the same value as `none`: `p=none` is a domain actively
telling you "monitor only," while `unpublished` means the domain has no DMARC policy at all —
the norm for most small domains and individuals, and not by itself a sign of anything wrong.
Don't write a rule that conflates the two.

## Example

```json
{
  "matcher": { "type": "DMARC_POLICY_EQUALS", "key": "unpublished" },
  "action": { "type": "READ" }
}
```
