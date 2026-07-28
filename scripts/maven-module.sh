#!/usr/bin/env bash
set -euo pipefail

module="${1:-all}"
shift || true

case "${module}" in
  all)       project_args=() ;;
  be)        project_args=(-pl payment-gateway-be -am) ;;
  providers) project_args=(-pl :payment-provider-spi,:payment-provider-noop,:payment-provider-paypal -am) ;;
  spi)       project_args=(-pl :payment-provider-spi -am) ;;
  noop)      project_args=(-pl :payment-provider-noop -am) ;;
  paypal)    project_args=(-pl :payment-provider-paypal -am) ;;
  *)
    echo "Unknown module '${module}'." >&2
    echo "Available modules: all, be, providers, spi, noop, paypal" >&2
    exit 2
    ;;
esac

exec mvn -f pom.xml "${project_args[@]}" "$@"
