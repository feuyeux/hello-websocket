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
    echo "  -b, --batch-size N    Languages per concurrent group; groups run serially. Default: 6."
    echo "                         N=0 puts every language in a single group (fully parallel). N=1 is fully serial."
    echo "  -k, --continue        Keep going past per-language failures and print a failure summary at the end."
    echo
    echo "Examples:"
    echo "  $0 --all                        # Build all language images, 6 per group (groups serial, group internals parallel)"
    echo "  $0 --language java              # Build Java images"
    echo "  $0 --language java --component server  # Build only Java server image"
    echo "  $0 --all --batch-size 1         # Build all languages fully serially"
    echo "  $0 --all --batch-size 0         # Build all languages in one fully-parallel group (highest concurrency, most memory pressure)"
    echo "  $0 --all --continue            # Build all, skip past failures, report which failed at the end"
    exit 1
}

# Initialize variables
BUILD_ALL=false
LANGUAGE=""
COMPONENT="both"
VERBOSE=false
BATCH_SIZE=6
CONTINUE=false
PIDS=()
FAILED_LANGS=()

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
    -b | --batch-size)
        BATCH_SIZE="$2"
        shift 2
        ;;
    -k | --continue)
        CONTINUE=true
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

# Validate BATCH_SIZE: must be a non-negative integer
if ! [[ "$BATCH_SIZE" =~ ^[0-9]+$ ]]; then
    echo "Error: --batch-size must be a non-negative integer (got '$BATCH_SIZE')."
    exit 1
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
    local dockerfile_lang="$lang"
    if [[ "$lang" == "nodejs" ]]; then
        dockerfile_lang="node"
    fi
    local dockerfile="${dockerfile_lang}_ws.dockerfile"

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

# Wait for parallel jobs and report any failures.
# Each PIDS entry is paired with the language name in PIDS_LANGS (parallel arrays).
# On CONTINUE=true, failed languages are appended to FAILED_LANGS and the group continues.
# On CONTINUE=false, the first failure triggers exit 1 (preserves the original strict behaviour).
wait_for_parallel_jobs() {
    if [ ${#PIDS[@]} -eq 0 ]; then
        return 0
    fi
    local failed=0
    for i in "${!PIDS[@]}"; do
        local pid="${PIDS[$i]}"
        local lang="${PIDS_LANGS[$i]}"
        if ! wait "$pid"; then
            if [[ "$CONTINUE" == true ]]; then
                echo "!! $lang build failed (continuing)"
                FAILED_LANGS+=("$lang")
            else
                echo "Warning: Process $pid ($lang) build failed!"
                failed=1
            fi
        fi
    done
    PIDS=()
    PIDS_LANGS=()
    return $failed
}

# Record start time
start_time=$(date +%s)

# Main logic
if [[ "$BUILD_ALL" == true ]]; then
    all_langs=(cpp rust java go csharp python nodejs dart kotlin swift php ts)
    total=${#all_langs[@]}

    # Determine effective group size. BATCH_SIZE=0 means "one group with all languages" (fully parallel).
    # BATCH_SIZE>=1 means up to BATCH_SIZE languages per group; groups run serially, languages within a group run in parallel.
    eff_group_size=$BATCH_SIZE
    if [[ "$eff_group_size" -eq 0 ]]; then
        eff_group_size=$total
    fi
    [[ "$eff_group_size" -gt $total ]] && eff_group_size=$total

    num_groups=$(( (total + eff_group_size - 1) / eff_group_size ))
    for ((g = 0; g < num_groups; g++)); do
        start=$((g * eff_group_size))
        end=$((start + eff_group_size))
        [[ $end -gt $total ]] && end=$total
        chunk=("${all_langs[@]:start:end-start}")
        chunk_str="${chunk[*]}"
        echo ""
        echo "==== Batch $((g + 1))/$num_groups [$chunk_str] ===="
        if [[ "$eff_group_size" -eq 1 ]]; then
            # Single language per group: run it directly. With CONTINUE, swallow failures and record.
            if [[ "$CONTINUE" == true ]]; then
                if ! build_language "${chunk[0]}" "$COMPONENT"; then
                    echo "!! ${chunk[0]} build failed (continuing)"
                    FAILED_LANGS+=("${chunk[0]}")
                fi
            else
                build_language "${chunk[0]}" "$COMPONENT"
            fi
        else
            for lang in "${chunk[@]}"; do
                build_language "$lang" "$COMPONENT" &
                PIDS+=($!)
                PIDS_LANGS+=("$lang")
            done
            if ! wait_for_parallel_jobs; then
                exit 1
            fi
        fi
    done
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
if [[ "$BUILD_ALL" == true ]]; then
    if [[ ${#FAILED_LANGS[@]} -eq 0 ]]; then
        echo "All languages built successfully."
    else
        echo "FAILED (${#FAILED_LANGS[@]}): ${FAILED_LANGS[*]}"
    fi
fi
echo "============================================"
