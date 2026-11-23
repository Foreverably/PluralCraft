# Contributing to PluralCraft

Thank you for helping make PluralCraft more welcoming and useful. Contributions
are expected to respect plural people and protect the privacy of systems.

## Community expectations

- Be respectful and affirming. Do not debate or invalidate someone's identity
  or lived experience.
- Do not ask contributors or users to disclose a diagnosis, system structure,
  member information, or other personal details to participate or get support.
- Keep reports and discussions focused on the project. Harassment, identity
  policing, and sharing another person's private information are not welcome.

## Reporting issues

Include the Minecraft version, Fabric Loader and Fabric API versions, steps to
reproduce, and relevant error messages. Remove access tokens, personal
information, private system data, and unrelated chat from logs, screenshots,
and recordings before posting them.

Use the [bug report](.github/ISSUE_TEMPLATE/bug_report.yml) or
[feature request](.github/ISSUE_TEMPLATE/feature_request.yml) template when
available.

For substantial feature proposals, open an issue or discuss the idea in the
[support Discord](https://discord.gg/QvvCW7YSR7) before doing extensive work.

## Pull requests

- Keep changes focused and explain the user-visible behavior they affect.
- Preserve the mod's client and dedicated-server support. In particular, a
  server with PluralCraft must continue to require the client mod.
- Run `.\gradlew.bat build` and report the result. Add focused tests when
  introducing behavior that can be tested independently.
- Avoid adding dependencies unless they are needed and their purpose is
  explained.
- Never commit credentials, API tokens, private system data, or generated
  build output.

Use the [pull request template](.github/PULL_REQUEST_TEMPLATE.md) and follow
the [commit message convention](docs/commit-messages.md). Additionally, the code style uses
the [Allman Java style](docs/xml/README.md).

By submitting a contribution, you agree that it may be distributed under the
repository's [GPL-3.0-or-later license](LICENSE). No separate contributor
license agreement is currently required.
