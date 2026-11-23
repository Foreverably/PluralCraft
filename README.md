<p align="center">
  <img src="https://cdn.forwynn.net/pluralcraft.png" alt="PluralCraft" width="420" />
</p>
<p align="center">
  <strong>A Minecraft mod made with systems in mind</strong>
   <br>
   <a href="https://pluralcraft.forwynn.net/">Website</a> • <a href="https://discord.gg/QvvCW7YSR7">Support Discord</a>
</p>

PluralCraft connects Minecraft: Java Edition to PluralBuddy so any DID/OSDD system can be
represented by the alter who is fronting while playing.

## System Linking & Chat Proxying

In game, run
`/pluralcraft link` to open the PluralBuddy authorization page and allow PluralCraft to fetch the linked system's
auto-proxy
settings and alter list, including per-alter proxy tags.

The client applies the linked system's alter, latch, and off auto-proxy modes as well as per-alter prefix or suffix
proxy
tags (which are stripped upon sending) and
shares active presence with servers and connected modded clients. When sending messages on a client / server with
PluralCraft, chat is
decorated as
`<Username> [Alter Name] <message>`.

Use `/pluralcraft status` to check fronting, `/pluralcraft alters` to list all
alters and proxy tags, `/pluralcraft refresh` to update fetched data immediately, and
`/pluralcraft ap` to inspect the resolved auto-proxy mode, and `/pluralcraft unlink` to clear the link.

> [!WARNING]
> The client stores OAuth tokens and a linked-system snapshot in
> `config/pluralcraft/link.json` so it can restore the link after a client or world
> restart. This includes the access token and refresh token in plaintext, along
> with alter and auto-proxy data. Protect your Minecraft profile directory and
> use `/pluralcraft unlink` to remove it.

## Requirements

- Minecraft Java Edition 26.3
- Fabric Loader 0.20.1 or newer
- Fabric API 0.166.2+26.3
- Java 25 or newer

## Installing

1. Install Fabric Loader for Minecraft 26.3 on the server and each client.
2. Put the PluralCraft JAR and matching Fabric API JAR in the `mods` folder of
   the server and every client that joins it.
3. Start the server and connect using a Fabric 26.3 client.

The PluralCraft mod JAR is the same on client and server. Clients can use the
mod when joining a server that does not have PluralCraft. Servers with
PluralCraft require the client mod; therefore, joining a server that runs PluralCraft requires the client mod to be
present.

## Building from source

Use Java 25 or newer. From the repository root, run:

```powershell
.\gradlew.bat build
```

On macOS or Linux, run `sh ./gradlew build`. The distributable JAR is written to
`build/libs/pluralcraft-1.0.0-alpha-r1.jar`.

Fabric Loom also provides development launch tasks:

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
```

## Privacy and support

PluralCraft connects to PluralBuddy's API and sends active alter presence to
Minecraft servers running the mod. Alter names and IDs may also be shared with
connected clients running PluralCraft.

The mod stores OAuth tokens and system, alter, proxy-tag, and auto-proxy data
in `config/pluralcraft/link.json` so the link survives game restarts. Information
handled by PluralBuddy is governed by
[PluralBuddy's Privacy Policy](https://pluralbuddy.app/docs/policies/privacy).

For help regarding PluralCraft, visit the [support Discord](https://discord.gg/QvvCW7YSR7). Please use
the repository's issue tracker for reproducible bugs and feature requests.

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening an issue or pull
request.

## License

The source code in this repository is licensed under the
[GNU General Public License, version 3 or later](LICENSE). This does not grant
rights to third-party materials or trademarks. The Gradle wrapper JAR is
provided under the [Apache License 2.0](LICENSE-APACHE-2.0).
