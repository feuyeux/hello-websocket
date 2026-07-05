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
    echo "  -l, --language LANG   Push specific language image (cpp, rust, java, go, csharp, python, nodejs, dart, kotlin, swift, php, ts)"
    echo "  -c, --component TYPE  Push specific component (server, client, both). Default: both"
    echo "  -a, --all             Push all language images"
    echo "  -h, --help            Display this help message"
    echo
    echo "Examples:"
    echo "  $0 --all                        # Push all language images"
    echo "  $0 --language java              # Push Java images"
    echo "  $0 --language java --component server  # Push only Java server image"
    exit 1
}

# Initialize variables
PUSH_ALL=false
LANGUAGE=""
COMPONENT="both"

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
        PUSH_ALL=true
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

# Function to push language images
push_language() {
    local lang="$1"
    local component="$2"

    echo "==== Pushing $lang ($component) ===="

    if [[ "$component" == "server" || "$component" == "both" ]]; then
        local img=$(get_image_name "$lang" "server")
        echo "~~~ Pushing ws server $lang ($img) ~~~"
        docker push "$img"
    fi

    if [[ "$component" == "client" || "$component" == "both" ]]; then
        local img=$(get_image_name "$lang" "client")
        echo "~~~ Pushing ws client $lang ($img) ~~~"
        docker push "$img"
    fi

    echo "$lang push completed successfully"
}

# Record start time
start_time=$(date +%s)

# Main logic
if [[ "$PUSH_ALL" == true ]]; then
    all_langs=(cpp rust java go csharp python nodejs dart kotlin swift php ts)
    for lang in "${all_langs[@]}"; do
        push_language "$lang" "$COMPONENT"
    done
elif [[ -n "$LANGUAGE" ]]; then
    validate_language "$LANGUAGE"
    validate_component "$COMPONENT"
    push_language "$LANGUAGE" "$COMPONENT"
else
    usage
fi

# Record end time and show summary
end_time=$(date +%s)
duration=$((end_time - start_time))
echo ""
echo "============================================"
echo "Push completed in ${duration}s"
echo "============================================"
