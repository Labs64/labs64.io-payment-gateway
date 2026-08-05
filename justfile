# Labs64.IO :: Payment Gateway — product-level commands

APP := "payment-gateway"
NAMESPACE := "labs64io"
DEFAULT_FORWARD_PORT := "8020"
BACKEND_JUSTFILE := "payment-gateway-be/justfile"
HELM_JUSTFILE := "../labs64.io-helm-charts/justfile"

default:
    @just --list

# Bootstrap the local cluster and shared tools, then deploy Payment Gateway
up:
    just --justfile {{HELM_JUSTFILE}} cluster-up
    just --justfile {{HELM_JUSTFILE}} repo-update
    just --justfile {{HELM_JUSTFILE}} generate-secrets
    just --justfile {{HELM_JUSTFILE}} install-tools
    just deploy

# Build and push :latest, install or upgrade the chart, then roll out the new image
deploy:
    just --justfile {{BACKEND_JUSTFILE}} docker-push
    just --justfile {{HELM_JUSTFILE}} install-app {{APP}}
    just restart

# Restart Payment Gateway and wait until the deployment is ready
restart:
    just --justfile {{HELM_JUSTFILE}} restart {{APP}}
    kubectl rollout status deployment labs64io-{{APP}} --namespace {{NAMESPACE}} --timeout=180s

# Uninstall only Payment Gateway; keep the cluster and shared tools running
down:
    just --justfile {{HELM_JUSTFILE}} uninstall-app {{APP}}

# Show Payment Gateway Kubernetes resources
status:
    kubectl get deployment,pod,service --namespace {{NAMESPACE}} --selector app.kubernetes.io/name={{APP}} --output wide

# Follow Payment Gateway logs
logs:
    just --justfile {{HELM_JUSTFILE}} logs {{APP}}

# Forward Payment Gateway from Kubernetes, prompting for a local port when omitted
port-forward port="":
    #!/usr/bin/env bash
    set -euo pipefail
    local_port="{{port}}"
    if [[ -z "$local_port" && -t 0 ]]; then
      read -r -p "Local port [{{DEFAULT_FORWARD_PORT}}]: " local_port || true
    fi
    local_port="${local_port:-{{DEFAULT_FORWARD_PORT}}}"
    if [[ ! "$local_port" =~ ^[0-9]+$ ]] || (( local_port < 1 || local_port > 65535 )); then
      echo "Invalid port: $local_port" >&2
      exit 2
    fi
    echo "Forwarding labs64io-{{APP}} to http://localhost:$local_port (Ctrl+C to stop)"
    kubectl port-forward --namespace {{NAMESPACE}} service/labs64io-{{APP}} "$local_port:8080"

# Check core development tools and print their versions
doctor:
    @java -version
    @mvn -version
    @just --version
    @git --version

# Run E2E tests via the sibling labs64.io-tests repository
test-e2e:
    @just -f ../labs64.io-tests/justfile test-module payment-gateway
