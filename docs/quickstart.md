# Quick start

Two steps: try IMF locally in a throwaway directory, then turn it into a proper Linux service
once you're happy with the config.

## 1. Try it out

You need Java 17 and network access to your IMAP server (and to the public DNS for SPF/DKIM/
DMARC/FCrDNS checks — see [What is it?](../README.md#what-is-it)).

```sh
mkdir imf && cd imf
curl -LO https://github.com/pieroxy/imap-mail-filter/releases/latest/download/imf-core-1.0.0.jar
curl -LO https://raw.githubusercontent.com/pieroxy/imap-mail-filter/main/config.example.json
mv config.example.json config.json
```

Edit `config.json`:

- Fill in `host`, `username`, `password` (and `port`/`displayName` if needed) under
  `configurations`.
- For this first try, point `dataFolder` and `logFile` at plain local paths instead of the
  `/var/lib`/`/var/log` ones in the example — you don't have write access there yet, and you
  don't want to run as root just to test:

  ```json
  "dataFolder": "./data",
  "logFile": "./imf.log",
  ```

See the [configuration reference](README.md#configuration-file) for what every field does —
the example file is a reasonable starting point (see
[Starter configuration](../README.md#starter-configuration)), not something to use as-is.

Then run it, passing the **directory containing `config.json`** (here, the current directory):

```sh
java -jar imf-core-1.0.0.jar .
```

IMF starts one thread per account, connects, creates the `imf-rules/` folder skeleton used for
[learning rules by example](README.md#learning-rules-by-example), and begins polling every
`runEvery` seconds. Watch `./imf.log` (or the console) to confirm it's picking up mail and
matching rules. Ctrl-C stops it.

Once you're satisfied it's working, move on to running it as a service.

## 2. Run it as a systemd service on Linux

This sets IMF up under a dedicated, unprivileged system user, with the FHS-style paths the
example config already assumes (`/var/lib/imf`, `/var/log/imf`).

Create the user and directories:

```sh
sudo useradd --system --home /opt/imf --shell /usr/sbin/nologin imf
sudo mkdir -p /opt/imf /var/lib/imf /var/log/imf
```

Put the jar and your finished `config.json` (from step 1, with `dataFolder`/`logFile` switched
back to `/var/lib/imf`/`/var/log/imf/imf.log` as in the example) into `/opt/imf`:

```sh
sudo cp imf-core-1.0.0.jar config.json /opt/imf/
sudo chown -R imf:imf /opt/imf /var/lib/imf /var/log/imf
```

Find your `java` binary (`which java`), then create `/etc/systemd/system/imf.service`:

```ini
[Unit]
Description=IMF - IMAP mail filter daemon
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=imf
Group=imf
ExecStart=/usr/bin/java -jar /opt/imf/imf-core-1.0.0.jar /opt/imf
Restart=on-failure
RestartSec=30

NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/imf /var/log/imf

[Install]
WantedBy=multi-user.target
```

(Adjust the `ExecStart` java path to match `which java`. The last argument, `/opt/imf`, is the
directory IMF reads `config.json` from — it happens to be the same directory as the jar here,
but doesn't have to be.)

Enable and start it:

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now imf
sudo systemctl status imf
```

Two places to look for logs: `/var/log/imf/imf.log` (IMF's own rotating log, per `logFile`/
`keepLogFiles` in the config — see [Logging](README.md#logging)) for the day-to-day activity,
and `journalctl -u imf -f` for service-level output (startup, crashes, anything printed before
the log file is set up).

To pick up a config change, restart the service. The same applies if you hand-edit
`learned-rules.json` — see [`SUBJECT_STARTS_WITH`](matchers/subject-starts-with.md) for why:

```sh
sudo systemctl restart imf
```
