# IMF documentation

IMF (imap-mail-filter) is a small daemon that polls one or more IMAP accounts and applies
configurable rules to new mail: move it, mark it read, or both. Rules can be written by hand in
`config.json`, or taught by example by dropping sample messages into special IMAP folders.

- [Running IMF](#running-imf)
- [Configuration file](#configuration-file)
  - [Top-level fields](#top-level-fields)
  - [Account fields](#account-fields)
  - [Example `config.json`](#example-configjson)
- [Matchers and actions](#matchers-and-actions)
- [Rule evaluation order](#rule-evaluation-order)
- [Learning rules by example](#learning-rules-by-example)
- [Manually reprocessing a message](#manually-reprocessing-a-message)
- [Logging](#logging)
- [Data files](#data-files)
- [Classifier corpus collection](#classifier-corpus-collection)

## Running IMF

IMF requires Java 17. Build a runnable jar with Maven, then run it with the path to a
**directory containing `config.json`**:

```sh
mvn clean package
java -jar target/imf-core-1.0-SNAPSHOT.jar /path/to/config-dir
```

One thread is started per account declared in `configurations`. Each account is processed on
its own schedule (`runEvery`, see below), backing off automatically (up to 30 minutes) if a
cycle keeps failing (e.g. the IMAP server is unreachable).

## Configuration file

`config.json` (found in the directory passed on the command line) is a plain JSON file, parsed
field-for-field into Java objects — the JSON keys match the Java field names exactly (camelCase).

### Top-level fields

| Field | Required | Description |
|---|---|---|
| `configurations` | yes | List of accounts to monitor (see below). |
| `dataFolder` | yes | Directory where IMF persists its own state: per-account UID cursors, learned rules, classifier corpus. Created if missing. |
| `logFile` | no | Path to a log file. If absent, IMF only logs to the console. |
| `keepLogFiles` | no | Number of rotated, lz4-compressed daily log files to keep. Only relevant if `logFile` is set; `0` or absent disables rotation. |
| `classifierCorpusRetentionDays` | no | Enables classifier corpus collection when `> 0` (see [Classifier corpus collection](#classifier-corpus-collection)). `0` or absent disables it. |

### Account fields

Each entry in `configurations` is one IMAP account:

| Field | Required | Description |
|---|---|---|
| `displayName` | yes | Free-form name, used for logging, thread naming, and as the key for this account's state files on disk. Must be filesystem-safe and unique across accounts. |
| `host` | yes | IMAP server hostname. |
| `port` | yes | IMAP server port. |
| `username` | yes | IMAP login. |
| `password` | yes | IMAP password. |
| `runEvery` | yes | Seconds between processing cycles. |
| `classifierSpamFolderName` | no | Folder treated as "Spam" for classifier corpus labeling. Defaults to `"Spam"`. |
| `rules` | no | List of manually-configured rules for this account (see [Matchers and actions](#matchers-and-actions)). Absent/empty means only learned rules (if any) apply. |

Connections are always made over IMAPS (implicit TLS) — there is no plain-IMAP option.

### Example `config.json`

```json
{
  "dataFolder": "/var/lib/imf",
  "logFile": "/var/log/imf/imf.log",
  "keepLogFiles": 14,
  "classifierCorpusRetentionDays": 90,
  "configurations": [
    {
      "displayName": "personal",
      "host": "imap.example.com",
      "port": 993,
      "username": "me@example.com",
      "password": "secret",
      "runEvery": 300,
      "classifierSpamFolderName": "Spam",
      "rules": [
        {
          "matcher": { "type": "SPF_RESULT_EQUALS", "key": "fail", "logLevel": "DEBUG" },
          "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
        },
        {
          "matcher": { "type": "SPF_RESULT_EQUALS", "key": "softfail" },
          "action": { "type": "MOVE_TO", "key": "Spam" }
        },
        {
          "matcher": { "type": "DMARC_RESULT_EQUALS", "key": "fail" },
          "action": { "type": "MOVE_TO_AND_READ", "key": "Spam" }
        },
        {
          "matcher": { "type": "FCRDNS_RESULT_EQUALS", "key": "none" },
          "action": { "type": "MOVE_TO", "key": "Spam" }
        },
        {
          "matcher": {
            "type": "AND",
            "children": [
              { "type": "FROM_DOMAIN_EQUALS", "key": "newsletter.example.com" },
              { "type": "DKIM_RESULT_EQUALS", "key": "pass" }
            ]
          },
          "action": { "type": "MOVE_TO", "key": "Newsletters" }
        },
        {
          "matcher": { "type": "FROM_ADDRESS_EQUALS", "keys": ["boss@example.com", "hr@example.com"] },
          "action": { "type": "READ" }
        }
      ]
    }
  ]
}
```

This example: sends SPF hard-failures and DMARC failures to Spam pre-marked read, sends SPF
softfails to Spam unread (for manual review), routes a newsletter domain to a `Newsletters`
folder only when it also carries a valid DKIM signature for that domain, and simply marks mail
from two trusted addresses as read (using `keys` to match either one) without moving it.

The `FCRDNS_RESULT_EQUALS: none` rule is here mainly to show the syntax — see
[its own doc](matchers/fcrdns-result-equals.md) before using it standalone like this in
production: it's a much weaker signal than SPF/DKIM/DMARC (plenty of legitimate small mail
servers have no forward-confirmed reverse DNS), so it's usually better combined with another
weak signal via `AND` than acted on alone.

## Matchers and actions

A rule is a `matcher` + an `action`. When a matcher matches a message, its action runs.

**Matchers** (`net.pieroxy.imf.rules.matchers`):

| Type | Purpose |
|---|---|
| [`FROM_EQUALS`](matchers/from-equals.md) | Exact match of the full `From:` header. |
| [`FROM_ADDRESS_EQUALS`](matchers/from-address-equals.md) | Match the sender's email address only. |
| [`FROM_DOMAIN_EQUALS`](matchers/from-domain-equals.md) | Match the sender's domain only. |
| [`SPF_RESULT_EQUALS`](matchers/spf-result-equals.md) | Match a live-verified SPF result. |
| [`DKIM_RESULT_EQUALS`](matchers/dkim-result-equals.md) | Match a live-verified DKIM result. |
| [`DMARC_RESULT_EQUALS`](matchers/dmarc-result-equals.md) | Match a live-evaluated DMARC result (SPF/DKIM domain alignment). |
| [`FCRDNS_RESULT_EQUALS`](matchers/fcrdns-result-equals.md) | Match a live-evaluated reverse DNS (FCrDNS) result on the connecting IP. |
| [`AND`](matchers/and.md) | Composite: all children must match. |
| [`OR`](matchers/or.md) | Composite: any child matching is enough. |

**Actions** (`net.pieroxy.imf.rules.actions`):

| Type | Purpose |
|---|---|
| [`MOVE_TO`](actions/move-to.md) | Move the message to another folder. |
| [`READ`](actions/read.md) | Mark the message as read. |
| [`MOVE_TO_AND_READ`](actions/move-to-and-read.md) | Mark as read, then move. |
| [`AND`](actions/and.md) | Composite: run children in order, stop at first failure. |
| [`OR`](actions/or.md) | Composite: run children in order, stop at first success. |

Every matcher and action config accepts an optional `logLevel` field (`"DEBUG"`, `"INFO"`,
`"WARNING"`, or `"ERROR"`; default `"INFO"`) controlling **only that node's own** log verbosity
— it never affects sibling or child nodes. `"DEBUG"` maps to `java.util.logging`'s `FINE` level
(there is no native `DEBUG` level in `java.util.logging`). See [Logging](#logging).

Composite matchers/actions (`AND`/`OR`) take a `children` array of nested matcher/action
configs instead of `key`/`keys`.

## Rule evaluation order

For each new message, IMF walks the full rule list — **manually-configured rules first, in the
order they appear in `config.json`, then learned rules** — and applies the **first one whose
matcher matches**. A rule "applies" (and stops the search) as soon as its matcher matches, even
if the action itself later fails; a failed action is logged but doesn't make IMF try the next
rule instead.

## Learning rules by example

Instead of writing a rule by hand, you can teach it by dropping example messages into a
specific IMAP folder path:

```
imf-rules/<MATCHER_TYPE>/<ACTION_TYPE>/<key>
```

For example, `imf-rules/FROM_DOMAIN_EQUALS/MOVE_TO/Spam`: every message dropped in that folder
teaches the rule "if the sender's domain equals this message's sender domain, move to Spam".
IMF automatically creates the `<MATCHER_TYPE>/<ACTION_TYPE>` skeleton for every learnable
matcher/action combination each cycle — you only need to create the final `<key>` folder
yourself (its name becomes the action's key, e.g. the target folder for `MOVE_TO`).

Only "learnable" matcher/action types support this (everything except the composite `AND`/`OR`
types, which are reserved for manual config). Each cycle, for every example message found:

1. The matcher's key is extracted from the example (e.g. its sender's domain).
2. The rule is persisted to `<dataFolder>/<displayName>-learned-rules.json` — a separate file
   from `config.json`, which IMF never modifies. If a rule with the same matcher type and
   action already exists, the new key is merged into it (`keys`) rather than creating a
   duplicate rule.
3. The action actually runs on the example message itself.
4. If the example is still present afterward (the action didn't move or delete it), it's moved
   to `imf-rules/Done` — so it isn't re-learned/re-run every cycle.

Learned rules always come **after** manual rules in evaluation order, so a manual rule always
wins if both would match.

## Manually reprocessing a message

To re-run the full rule catalog against a message that's already in your mailbox (for example,
after adding a rule that should have caught it), drop it into `imf-rules/ToProcess`. Every
cycle, IMF treats every message found there exactly as if it had just arrived: the first
matching rule applies normally. If the message is still present afterward — no rule matched, or
the matching rule's action didn't relocate it — it's moved to `imf-rules/Done` (the same folder
used by the learning system above) for manual review.

## Logging

Console logging is always on. Setting `logFile` also writes to that file, rotating daily into
lz4-compressed archives (kept for `keepLogFiles` days) if `keepLogFiles > 0`.

Each matcher/action node in a rule has its own logger, independently leveled via that node's
`logLevel` config field (default `INFO`). Setting `"logLevel": "DEBUG"` on an
[`SPF_RESULT_EQUALS`](matchers/spf-result-equals.md), [`DKIM_RESULT_EQUALS`](matchers/dkim-result-equals.md),
or [`DMARC_RESULT_EQUALS`](matchers/dmarc-result-equals.md) matcher is particularly useful: it
surfaces that rule's full live verification trace (DNS records fetched, mechanisms/signatures
evaluated, alignment computed) without touching any global logging configuration.

## Data files

Everything IMF persists lives under `dataFolder`, one file/folder per account (keyed by
`displayName`):

| Path | Contents |
|---|---|
| `<displayName>.json` | INBOX UID cursor (which messages have already been processed). |
| `<displayName>-learned-rules.json` | Rules learned via `imf-rules/` (see above). Hand-editable. |
| `classifier-corpus/<displayName>-scan-state.json` | Per-folder UID cursor for corpus scanning. |
| `classifier-corpus/<displayName>/classifier-YYYY-MM-DD.json.lz4` | One compressed file per day of collected corpus data. |

## Classifier corpus collection

When `classifierCorpusRetentionDays > 0`, IMF spends a little time once a day (per account)
walking every folder except `INBOX` and `imf-rules/`, labeling messages `SPAM` (if in the
`classifierSpamFolderName` folder) or `HAM` (everything else), and writing them out as a
labeled dataset under `dataFolder`. Files older than `classifierCorpusRetentionDays` days are
pruned.

**This currently only collects a labeled dataset — nothing in IMF yet classifies incoming mail
using it.** There is no classifier-based matcher today; this is groundwork for a future
capability, not something that affects filtering decisions now.
