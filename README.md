# imap-mail-filter


## What is it?

imap-mail-filter (IMF) is a small daemon that bolts spam detection and routing rules onto any IMAP mailbox — built for people who run their own mail server. On Gmail or Outlook.com? You're already covered, no need for this.

Mail servers and clients often do this natively, but every time you switch software you get to reconfigure it all from scratch. IMF only needs IMAP, so it doesn't care what's behind it: point it at a new server and everything — rules included — just keeps working.

Teaching it a new rule is dead simple too: drop an example email into the right `imf-rules/` subfolder, and every future email matching the same criteria gets routed automatically. See [Learning rules by example](docs/README.md#learning-rules-by-example) for how that folder structure works.

## Requirements

* Java 17.
* An IMAP account, reachable over IMAPS (implicit TLS) — there's no plain-IMAP mode, sorry. Folder-creation rights too, since IMF manages its own `imf-rules/` folder tree; the default on pretty much any account you'd actually own.
* An internet connection — to your mail server, and for the SPF/DKIM/DMARC/FCrDNS checks, which are just DNS lookups. Nothing else ever leaves the box.

That's it.

## Features

Brings to your IMAP account:
* SPF, DKIM, DMARC and FCrDNS checks
* Rules based on **from** and **subject**
* ML-based spam detection ([`SUBJECT_CLASSIFIER_EQUALS`](docs/matchers/subject-classifier-equals.md)), trained locally on your own emails — no dataset, no third-party service, just a model built and kept from your own mailbox as it grows

Additionally:

* Logs show exactly which rule acted on each message
* No third-party service ever sees your mail — the only outbound traffic is DNS lookups for SPF/DKIM/DMARC/FCrDNS checks, which reveal sender domains/IPs but never message content.

## Concepts

A few things worth understanding before you dive in:

* **No UI, no manual rule editing** — the primary way to teach IMF a rule is to drop an example email into the right `imf-rules/` subfolder. See [Learning rules by example](docs/README.md#learning-rules-by-example).
* **First match wins** — rules from `config.json` are evaluated before learned rules, in order; the first one that matches runs its action and evaluation stops there. See [Rule evaluation order](docs/README.md#rule-evaluation-order).
* **Everything is HAM except Spam** — for [classifier corpus collection](docs/README.md#classifier-corpus-collection), every folder is treated as legitimate mail (HAM) except the configured Spam folder. `INBOX`, `imf-rules/`, and any [excluded folders](docs/README.md#excluding-a-folder-from-the-corpus) (e.g. `SpamML`) are skipped entirely rather than counted as either.
* **INBOX doesn't count** — INBOX is never scanned for the corpus, so mail you leave sitting there teaches the classifier nothing. Filing/archiving read mail into folders (an "inbox zero" habit) is what actually feeds it examples of legitimate mail.
* **Unread in Spam means "review me"** — by convention (see the [starter config](config.example.json)), strong verdicts (SPF/DKIM/DMARC `fail`) are moved to Spam pre-marked read, while weaker, corroborating-only signals are left unread — a manual-review flag, since IMF has no UI to show confidence.
* **Always verified live** — SPF/DKIM/DMARC/FCrDNS are recomputed from scratch via DNS on every check; any `Authentication-Results`/`Received-SPF` header already on the message is never trusted, since anyone could have forged it before delivery.
* **Manual reprocessing** — drop any message into `imf-rules/ToProcess` to run the current rule set against it (handy for reclassifying an old message after adding or fixing a rule); it ends up in `imf-rules/Done` once handled, whether or not a rule actually matched. See [Manually reprocessing a message](docs/README.md#manually-reprocessing-a-message).

## Roadmap

* Feed the classifier more than just the subject — sender, recipient, IP.
* Reputation based scoring for spam detection
* Simple web UI to edit more complex rules.
* More matchers:
    * Recipient-based: TO_EQUALS/CC_EQUALS/recipient-domain matching
    * Subject: CONTAINS, MATCHES (regex)
    * Generic headers: HEADER_EQUALS/HEADER_CONTAINS(name, value)
    * Body: BODY_CONTAINS, BODY_MATCHES
    * SIZE
    * Attachment: HAS_ATTACHMENT, FILENAME_ENDS_WITH, FILENAME_IS
    * Dated: MESSAGE_AGE, MESSAGE_DATE
    * IMAP_FLAGS
* More actions:
    * COPY_TO
    * DELETE
    * SET_FLAG
    * FORWARD/REDIRECT/REPLY — Later, it's a much bigger endeavor

## Documentation

See [docs/](docs/README.md) for how IMF works, the configuration reference, and a dedicated page for every matcher and action. New here? Start with the [quick start guide](docs/quickstart.md): download the jar, try it, then run it as a systemd service.

## Starter configuration

[`config.example.json`](config.example.json) is a reasonable config to start from: SPF, DKIM,
DMARC, and FCrDNS all enabled, with sane logging. Copy it to your `dataFolder` as `config.json`
and fill in `host`/`username`/`password`.

It deliberately only sends mail to Spam on the strong signals — SPF `fail`, DKIM `fail`, and
DMARC `fail` — pre-marked read. `FCRDNS_RESULT_EQUALS` is intentionally **not** used standalone:
it's the weakest of the four (see [its doc](docs/matchers/fcrdns-result-equals.md)), so it's
only combined with SPF `softfail` via `AND`, as corroboration rather than a trigger on its own —
and that combined rule leaves the message unread, for manual review rather than an outright
Spam verdict.

It also enables classifier corpus collection (`classifierCorpusRetentionDays`) and adds one
[`SUBJECT_CLASSIFIER_EQUALS`](docs/matchers/subject-classifier-equals.md) rule at a `>0.99`
threshold, pre-marked read like the protocol-verified signals above — but routed to `SpamML`,
a folder of its own, rather than straight into `Spam`. `classifierExcludedFolders` then excludes
that folder from corpus collection: without it, `SpamML` would be scanned like any other folder
and, not being named `Spam`, mislabeled `HAM` — poisoning the corpus with spam classified as
legitimate mail. It also keeps the classifier from ever training on its own past verdicts, unlike
the protocol-verified rules above, whose Spam verdicts stay perfectly fine to learn from. See
[Excluding a folder from the corpus](docs/README.md#excluding-a-folder-from-the-corpus).

This rule does nothing at all until a model has actually been trained, which requires **at
least 50 examples of each class** (`SPAM`/`HAM`) — see
[Classifier corpus collection](docs/README.md#classifier-corpus-collection). That threshold
isn't just about having *some* data: a model trained on too few examples tends to produce
artificially extreme scores (a word seen once on one side of the split can swing a score to 0.99
on its own, not because it's a genuinely reliable pattern) — 50 gives `>0.99` a fairer chance of
meaning what it says before this rule starts acting on it.

