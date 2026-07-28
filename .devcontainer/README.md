# Payment Gateway development container

This directory follows the open Dev Container Specification. It is intended for
any compatible client, including VS Code, JetBrains IDEs, and the Dev Container
CLI.

The complete repository is mounted because the backend depends on provider
modules in the same Maven reactor. Maven artifacts and service data are kept in
named Docker volumes, so rebuilding the container preserves them.

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

Cerbos policies are generated under `payment-gateway-be/target/cerbos` during a
Maven build. Cerbos is therefore allowed to start independently during the first
container creation and automatically restarts while the generated policies are
not available yet.
