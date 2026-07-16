#!/usr/bin/env bash

# Shared OCI runtime selection for the Dockerfile-based workflows.
# Set WS_CONTAINER_RUNTIME to docker or container to override auto detection.

ws_container_runtime_init() {
    local requested="${WS_CONTAINER_RUNTIME:-auto}"
    local host_os host_arch
    host_os="$(uname -s)"
    host_arch="$(uname -m)"

    case "$requested" in
    auto)
        if [[ "$host_os" == "Darwin" && "$host_arch" == "arm64" ]] && command -v container >/dev/null 2>&1; then
            WS_CONTAINER_RUNTIME="container"
        elif command -v docker >/dev/null 2>&1; then
            WS_CONTAINER_RUNTIME="docker"
        else
            echo "Error: neither Docker nor Apple container is available." >&2
            return 1
        fi
        ;;
    docker|container)
        WS_CONTAINER_RUNTIME="$requested"
        ;;
    *)
        echo "Error: WS_CONTAINER_RUNTIME must be auto, docker, or container." >&2
        return 1
        ;;
    esac

    if [[ "$WS_CONTAINER_RUNTIME" == "container" ]]; then
        if [[ "$host_os" != "Darwin" || "$host_arch" != "arm64" ]]; then
            echo "Error: Apple container requires an Apple-silicon Mac." >&2
            return 1
        fi
        if ! command -v container >/dev/null 2>&1; then
            echo "Error: selected runtime 'container' is not installed." >&2
            return 1
        fi
        if ! container system status >/dev/null 2>&1; then
            echo "Starting Apple container services..."
            container system start
        fi
    else
        if ! command -v docker >/dev/null 2>&1; then
            echo "Error: selected runtime 'docker' is not installed." >&2
            return 1
        fi
        if ! docker info >/dev/null 2>&1; then
            echo "Error: Docker does not appear to be running. Please start Docker and try again." >&2
            return 1
        fi
    fi

    export WS_CONTAINER_RUNTIME
    echo "Using container runtime: $WS_CONTAINER_RUNTIME"
}

ws_container_build() {
    "$WS_CONTAINER_RUNTIME" build "$@"
}

ws_container_run() {
    "$WS_CONTAINER_RUNTIME" run "$@"
}

ws_container_logs() {
    "$WS_CONTAINER_RUNTIME" logs "$@"
}

ws_container_remove() {
    if [[ "$WS_CONTAINER_RUNTIME" == "container" ]]; then
        container delete --force "$@"
    else
        docker rm -f "$@"
    fi
}

ws_container_push() {
    if [[ "$WS_CONTAINER_RUNTIME" == "container" ]]; then
        container image push "$@"
    else
        docker push "$@"
    fi
}

ws_container_require_host_domain() {
    local domain="host.container.internal"

    [[ "$WS_CONTAINER_RUNTIME" == "container" ]] || return 0

    if ! container system dns list --quiet 2>/dev/null | grep -Fxq "$domain"; then
        cat >&2 <<EOF
Error: Apple container clients need a host DNS domain to reach the published server port.
Run this once, then retry:
  sudo container system dns create $domain --localhost 203.0.113.113
EOF
        return 1
    fi
}
