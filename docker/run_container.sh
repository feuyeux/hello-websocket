#!/bin/bash
cd "$(
    cd "$(dirname "$0")" >/dev/null 2>&1
    pwd -P
)/" || exit
set -e

# Function to display usage information
usage() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  -l, --language LANG   Run container for specific language (cpp, rust, java, go, csharp, python, nodejs, dart, kotlin, swift, php, ts)"
    echo "  -c, --component TYPE  Component to run (server, client). Required"
    echo "  -h, --help            Display this help message"
    echo
    echo "Examples:"
    echo "  $0 --language java --component server               # Run Java server"
    echo "  $0 --language go --component client                  # Run Go client"
    echo "  $0 --language python --component client              # Run Python client"
    exit 1
}

# Initialize variables
LANGUAGE=""
COMPONENT=""

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
    -l | --language)
        LANGUAGE="$2"
        shift 2
        ;;
    -c | --component)
        COMPONENT="$2"
        shift 2
        ;;
    -h | --help)
        usage
        ;;
    *)
        echo "Unknown option: $1"
        usage
        ;;
    esac
done

# Validate arguments
if [[ -z "$LANGUAGE" ]]; then
    echo "Error: --language is required"
    usage
fi
if [[ -z "$COMPONENT" ]]; then
    echo "Error: --component is required"
    usage
fi

# Function to validate language
validate_language() {
    local valid_langs=(cpp rust java go csharp python nodejs dart kotlin swift php ts)
    for lang in "${valid_langs[@]}"; do
        if [[ "$lang" == "$1" ]]; then
            return 0
        fi
    done
    echo "Error: Invalid language '$1'"
    echo "Valid languages: ${valid_langs[*]}"
    exit 1
}

# Function to validate component
validate_component() {
    case "$1" in
    server|client) return 0 ;;
    *)
        echo "Error: Invalid component '$1'. Must be server or client."
        exit 1
        ;;
    esac
}

# Function to get image name
get_image_name() {
    local lang="$1"
    local comp="$2"
    if [[ "$lang" == "nodejs" ]]; then
        echo "feuyeux/ws_${comp}_node:1.0.0"
    else
        echo "feuyeux/ws_${comp}_${lang}:1.0.0"
    fi
}

# Function to get container name
get_container_name() {
    local lang="$1"
    local comp="$2"
    if [[ "$lang" == "nodejs" ]]; then
        echo "ws_${comp}_node"
    else
        echo "ws_${comp}_${lang}"
    fi
}

# Validate inputs
validate_language "$LANGUAGE"
validate_component "$COMPONENT"

# Set variables
NAME=$(get_container_name "$LANGUAGE" "$COMPONENT")
IMG=$(get_image_name "$LANGUAGE" "$COMPONENT")

# Remove existing container if present
if docker ps -a --format '{{.Names}}' | grep -Eq "^${NAME}$"; then
    echo "Removing existing container ${NAME}..."
    docker rm -f "$NAME"
fi

if [[ "$COMPONENT" == "server" ]]; then
    echo "Running $LANGUAGE server..."
    echo "Container: $NAME, Image: $IMG"
    docker run -d --name "$NAME" -p 9898:9898 "$IMG"
    echo "Server started. View logs: docker logs -f $NAME"
    echo "Stop server: docker stop $NAME"
elif [[ "$COMPONENT" == "client" ]]; then
    echo "Running $LANGUAGE client..."
    echo "Container: $NAME, Image: $IMG"
    docker run -it --rm --name "$NAME" -e WS_SERVER=host.docker.internal -e WS_PORT=9898 "$IMG"
fi
