#!/usr/bin/env bash
set -euo pipefail

host_project_dir=${HOST_PROJECT_DIR:-}

if [[ -z "${host_project_dir}" ]]; then
  host_project_dir=${PWD}

  # Docker-outside-of-Docker: paths passed to the daemon must be host paths,
  # not the /workspaces paths visible only inside the devcontainer.
  if [[ -f /.dockerenv && "${PWD}" == /workspaces/* ]]; then
    container_id=${HOSTNAME:-$(hostname)}
    workspace_source=$(docker inspect "${container_id}" \
      --format '{{range .Mounts}}{{if eq .Destination "/workspaces"}}{{.Source}}{{end}}{{end}}' \
      2>/dev/null || true)

    if [[ -n "${workspace_source}" ]]; then
      relative_project_dir=${PWD#/workspaces/}
      host_project_dir="${workspace_source%/}/${relative_project_dir}"
    fi
  fi
fi

export HOST_PROJECT_DIR="${host_project_dir}"
exec docker compose "$@"
