# Commit message convention

PluralCraft uses a lightweight version of
[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/). This
is a contributor guideline, not an automated release or versioning system.

## Format

```text
type(optional-scope): short imperative summary
```

Use a lowercase type and summary. Keep the summary concise (ideally 72
characters or fewer), start it with an imperative verb, and omit the ending
period.

Common types:

- `feat`: add user-facing behavior
- `fix`: correct a bug
- `docs`: change documentation
- `refactor`: restructure code without changing behavior
- `test`: add or update tests
- `build`: change build configuration or dependencies
- `chore`: other maintenance

Scopes are optional; use them when they make the change clearer, for example
`client`, `server`, or `docs`.

## Examples

```text
feat(client): show the active front in the HUD
fix(server): reject clients without the required channel
docs: explain Fabric setup
build: update the Fabric API version
```

For a breaking change, add `!` after the type or scope and explain it in the
commit body or a `BREAKING CHANGE:` footer. Reference issues in a footer when
useful, for example `Fixes #42`.

Keep commits focused. Do not include generated build output, credentials,
access tokens, or private system information.
