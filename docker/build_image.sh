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
    echo "  -l, --language LANG   Build specific language image (cpp, rust, java, go, csharp, python, nodejs, dart, kotlin, swift, php, ts)"
    echo "  -c, --component TYPE  Build specific component (server, client, both). Default: both"
    echo "  -a, --all             Build all language images"
    echo "  -v, --verbose         Enable verbose output"
    echo "  -h, --help            Display this help message"
    echo "  -j, --parallel        Enable parallel building (default: off)"
    echo
    echo "Examples:"
    echo "  $0 --all                        # Build all language images"
    echo "  $0 --language java              # Build Java images"
    echo "  $0 --language java --component server  # Build only Java server image"
    echo "  $0 --all --parallel             # Build all language images in parallel"
    exit 1
}

# Initialize variables
BUILD_ALL=false
LANGUAGE=""
COMPONENT="both"
VERBOSE=false
PARALLEL=false
PIDS=()

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
    -a | --all)
        BUILD_ALL=true
        shift
        ;;
    -v | --verbose)
        VERBOSE=true
        shift
        ;;
    -j | --parallel)
        PARALLEL=true
        shift
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

if [[ "$VERBOSE" == true ]]; then
    set -x
fi

# Check if Docker is running
check_docker() {
    if ! docker info >/dev/null 2>&1; then
        echo "Error: Docker does not appear to be running. Please start Docker and try again."
        exit 1
    else
        echo "Docker is running, proceeding with build..."
    fi
}

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
    server|client|both) return 0 ;;
    *)
        echo "Error: Invalid component '$1'. Must be server, client, or both."
        exit 1
        ;;
    esac
}

# Path to the project root directory (parent of docker)
PROJECT_ROOT="$(realpath ..)"

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

# Generic build function
build_language() {
    local lang="$1"
    local component="$2"
    local dockerfile="${lang}_ws.dockerfile"

    echo "==== Building $lang ($component) ===="

    check_docker

    if [[ "$component" == "server" || "$component" == "both" ]]; then
        local img=$(get_image_name "$lang" "server")
        echo "~~~ Building ws server $lang ($img) ~~~"
        docker build --no-cache -f "$dockerfile" \
            --build-arg PROJECT_ROOT="${PROJECT_ROOT}" \
            --target server -t "$img" "${PROJECT_ROOT}"
    fi

    if [[ "$component" == "client" || "$component" == "both" ]]; then
        local img=$(get_image_name "$lang" "client")
        echo "~~~ Building ws client $lang ($img) ~~~"
        docker build --no-cache -f "$dockerfile" \
            --build-arg PROJECT_ROOT="${PROJECT_ROOT}" \
            --target client -t "$img" "${PROJECT_ROOT}"
    fi

    echo "$lang build completed successfully"
}

# Wait for parallel jobs
wait_for_parallel_jobs() {
    if [ ${#PIDS[@]} -eq 0 ]; then
        return
    fi
    echo "Waiting for all build tasks to complete..."
    for pid in "${PIDS[@]}"; do
        if ! wait "$pid"; then
            echo "Warning: Process $pid build failed!"
        fi
    done
    PIDS=()
}

# Record start time
start_time=$(date +%s)

# Main logic
if [[ "$BUILD_ALL" == true ]]; then
    all_langs=(cpp rust java go csharp python nodejs dart kotlin swift php ts)
    for lang in "${all_langs[@]}"; do
        if [[ "$PARALLEL" == true ]]; then
            build_language "$lang" "$COMPONENT" &
            PIDS+=($!)
        else
            build_language "$lang" "$COMPONENT"
        fi
    done
    wait_for_parallel_jobs
elif [[ -n "$LANGUAGE" ]]; then
    validate_language "$LANGUAGE"
    validate_component "$COMPONENT"
    build_language "$LANGUAGE" "$COMPONENT"
else
    usage
fi

# Record end time and show summary
end_time=$(date +%s)
duration=$((end_time - start_time))
echo ""
echo "============================================"
echo "Build completed in ${duration}s"
echo "============================================"
