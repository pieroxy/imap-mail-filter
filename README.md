# imap-mail-filter


## What is it?

imap-mail-filter is a program (a "daemon") that has the purpose of adding some automation to your email server. It is targeted at people hosting their own mail servers. Implementing those things at the mail server level or at the mail client level is more or less easy depending on the software used, but in all cases, you have to do the job all over again if/when you change said software.

IMF stands alone and just needs IMAP access to your account to add spam detection and basic routing rules.

IMF also implements a dead simple way to report spam or create a simple rule: just move an example email into the right `imf-rules/` subfolder, and every future email matching the same criteria gets routed automatically — see [Learning rules by example](docs/README.md#learning-rules-by-example) for how that folder structure works.

## Requirements

Just Java 17 and an Internet connexion.

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

Nothing here has a per-rule `logLevel` set (default `INFO`): add `"logLevel": "DEBUG"`
temporarily on a specific rule if you need to see its full verification trace — see
[Logging](docs/README.md#logging).
