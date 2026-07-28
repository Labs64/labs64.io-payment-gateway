# Payment Gateway development container

This directory follows the open Dev Container Specification. It is intended for
any compatible client, including VS Code, JetBrains IDEs, and the Dev Container
CLI.

The complete repository is mounted because the backend depends on provider
modules in the same Maven reactor. Maven artifacts and service data are kept in
named Docker volumes, so rebuilding the container preserves them.

The Maven local repository uses the ecosystem-wide
`labs64io-maven-repository` volume. Other Labs64 dev containers can mount it at
`/home/l64user/.m2/repository`, making locally installed artifacts available
across repositories.

## Common commands

```shell
just build              # complete Maven reactor
just build be           # backend and required dependencies
just build providers    # every provider module
just test paypal        # PayPal provider and dependencies
just install noop       # install NoOp provider and dependencies
just run                # build dependencies and run the backend
just modules            # list accepted module aliases
```

The credentials in `compose.yml` are local-only infrastructure defaults.
Provider credentials and other secrets must be supplied through the environment
and must not be committed.

VS Code extensions are declared under `customizations.vscode` and cached in the
ecosystem-wide `labs64io-vscode-extensions` volume. VS Code user data and extension
global storage are persisted separately in the project-scoped
`vscode-server-data` volume; the VS Code server binary remains managed by VS
Code. Other Dev Container clients ignore these optional editor customizations.

Codex, Claude Code, and GitHub Copilot CLI keep their local configuration and
session history in the `codex-data`, `claude-data`, and `copilot-data` volumes.
`CLAUDE_CONFIG_DIR` keeps all Claude Code state under its mounted directory.
These volumes contain personal history and may contain authentication data, so
they must not be exported or shared with other users.

Cerbos policies are generated under `payment-gateway-be/target/cerbos` during a
Maven build. Cerbos is therefore allowed to start independently during the first
container creation and automatically restarts while the generated policies are
not available yet.
