FROM maven:3.9.11-eclipse-temurin-25-noble

ARG USER_ID=1064
ARG GROUP_ID=1064
ARG USER_NAME=l64user
ARG GROUP_NAME=l64group
ARG JUST_VERSION=1.42.4
ARG TARGETARCH

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates curl git postgresql-client redis-tools unzip \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid "${GROUP_ID}" "${GROUP_NAME}" \
    && useradd --uid "${USER_ID}" --gid "${GROUP_ID}" --create-home --shell /bin/bash "${USER_NAME}" \
    && mkdir -p "/home/${USER_NAME}/.m2" /workspaces \
    && chown -R "${USER_ID}:${GROUP_ID}" "/home/${USER_NAME}" /workspaces \
    && case "${TARGETARCH}" in \
         amd64) just_arch=x86_64 ;; \
         arm64) just_arch=aarch64 ;; \
         *) echo "Unsupported architecture: ${TARGETARCH}" >&2; exit 1 ;; \
       esac \
    && curl -fsSL \
        "https://github.com/casey/just/releases/download/${JUST_VERSION}/just-${JUST_VERSION}-${just_arch}-unknown-linux-musl.tar.gz" \
        | tar -xz -C /usr/local/bin just

WORKDIR /workspaces/labs64.io-payment-gateway

USER ${USER_NAME}
