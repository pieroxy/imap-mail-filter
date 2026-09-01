# imap-mail-filter


## What is it?

imap-mail-filter is a program (a "daemon") that has the purpose of adding some automation to your email server. It is targeted at people hosting their own mail servers. Implementing those things at the mail server level or at the mail client level is more or less easy depending on the software used, but in all cases, you have to do the job all over again if/when you change said software.

IMF stands alone and just needs IMAP access to your account to add spam detection and basic routing rules.

IMF also implements a dead simple way to report spam or create a simple rule: just move an example email into the right `imf-rules/` subfolder, and every future email matching the same criteria gets routed automatically — see [Learning rules by example](docs/README.md#learning-rules-by-example) for how that folder structure works.

## Requirements

Just Java 17 and an Internet connection.

## Features

Brings to your IMAP account:
* SPF, DKIM, DMARC and FCrDNS checks
* Rules based on **from** and **subject**
* ML-based spam detection ([`SUBJECT_CLASSIFIER_EQUALS`](docs/matchers/subject-classifier-equals.md)), trained locally on your own emails — no dataset, no third-party service, just a model built and kept from your own mailbox as it grows

Additionally:

* Logs show exactly which rule acted on each message
* No third-party service ever sees your mail — the only outbound traffic is DNS lookups for SPF/DKIM/DMARC/FCrDNS checks, which reveal sender domains/IPs but never message content.

## Roadmap

* Feed the classifier more than just the subject — sender, recipient, IP.
* Simple web UI to edit more complex rules.

## Documentation

See [docs/](docs/README.md) for how IMF works, the configuration reference, and a dedicated page for every matcher and action.

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

