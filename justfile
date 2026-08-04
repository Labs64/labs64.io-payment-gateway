# Labs64.IO :: Payment Gateway — product-level commands

default:
    @just --list

# Check core development tools and print their versions
doctor:
    @java -version
    @mvn -version
    @just --version
    @git --version

# Run E2E tests via the sibling labs64.io-tests repository
test-e2e:
    @just -f ../labs64.io-tests/justfile test-module payment-gateway
