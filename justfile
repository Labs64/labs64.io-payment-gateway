# Labs64.IO :: Payment Gateway — development commands

default:
    @just --list

# Show Maven reactor module aliases accepted by the commands below
modules:
    @echo "all (default), be, providers, spi, noop, paypal"

# Build a module and its dependencies; without MODULE, build the complete reactor
build MODULE="all":
    bash ./scripts/maven-module.sh "{{MODULE}}" clean package -DskipTests

# Install a module and its dependencies into the local Maven repository
install MODULE="all":
    bash ./scripts/maven-module.sh "{{MODULE}}" clean install -DskipTests

# Run tests for a module and its dependencies
test MODULE="all":
    bash ./scripts/maven-module.sh "{{MODULE}}" clean verify

# Run unit tests for a module and its dependencies
unit-test MODULE="all":
    bash ./scripts/maven-module.sh "{{MODULE}}" test

# Remove generated build artifacts for a module and its dependencies
clean MODULE="all":
    bash ./scripts/maven-module.sh "{{MODULE}}" clean

# Build backend dependencies and run it with the local profile
run:
    just install be
    cd payment-gateway-be && mvn spring-boot:run -Dspring-boot.run.profiles=local

# Check core development tools and print their versions
doctor:
    @java -version
    @mvn -version
    @just --version
    @git --version

# Run E2E tests via the sibling labs64.io-tests repository
test-e2e:
    @just -f ../labs64.io-tests/justfile test-module payment-gateway
