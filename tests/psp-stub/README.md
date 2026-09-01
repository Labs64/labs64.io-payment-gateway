# Payment Gateway PSP fixtures

This directory owns provider-specific WireMock mappings used by Payment Gateway black-box
integration scenarios. The fixtures stay beside the provider and its Robot test so an outbound
SDK contract change can update implementation, fixture, and scenario together.

WireMock lifecycle belongs to the sibling `labs64.io-tests` repository. For a local k3d stack:

```bash
cd ../labs64.io-tests
just test-up
just test-psp
just test-down
```

`just test-up` starts a blank WireMock process and temporarily applies the Stripe and PayPal
endpoint overrides to the existing Payment Gateway Helm release. Each Robot suite reads its
versioned provider mappings from this directory and registers them through WireMock's Admin API;
there is no dependency on Docker seeing devcontainer workspace mounts. No WireMock container or
Kubernetes resource is part of the Payment Gateway application itself.
