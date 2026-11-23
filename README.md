<p align="center">
  <img src="https://cdn.forwynn.net/pluralcraft.png" alt="PluralCraft" width="420" />
</p>

<h1 align="center">PluralCraft</h1>

<p align="center">
  <strong>A Minecraft mod made with systems in mind</strong>
   <br>
   <a href="https://pluralcraft.forwynn.net/">Website</a> • <a href="https://discord.gg/QvvCW7YSR7">Support Discord</a>
</p>

PluralCraft connects Minecraft: Java Edition to PluralBuddy so any DID/OSDD system can be
represented by the alter who is fronting while playing.

## Project status

> This repository currently contains the base Fabric mod for Minecraft **26.3**.
It loads on both clients and dedicated servers. When a server has PluralCraft,
it requires connecting clients to have the mod too and disconnects clients that
don't have PluralCraft's networking channel.

The PluralBuddy API integration and in-game fronting features are _not
implemented yet_. There are no commands, configuration options, or API
credentials to set up at this stage.

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
mod when joining a server that does not have PluralCraft; servers with
PluralCraft require the client mod.

## Building from source

Use Java 25 or newer. From the repository root, run:

```powershell
.\gradlew.bat build
```

On macOS or Linux, run `sh ./gradlew build`. The distributable JAR is written to
`build/libs/pluralcraft-0.1.0.jar`.

Fabric Loom also provides development launch tasks:

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
```

## Privacy and support

PluralCraft is intended to connect to PluralBuddy's API in a future version.
API access, permissions, and handling of any credentials will be documented
before that integration is released. Do not post private system information,
access tokens, or unredacted logs in public issues or support channels.

For help, visit the [support Discord](https://discord.gg/QvvCW7YSR7). Please use
the repository's issue tracker for reproducible bugs and feature requests.

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening an issue or pull
request.

## License

The source code in this repository is licensed under the
[GNU General Public License, version 3 or later](LICENSE). This does not grant
rights to third-party materials or trademarks. The Gradle wrapper JAR is
provided under the [Apache License 2.0](LICENSE-APACHE-2.0).
