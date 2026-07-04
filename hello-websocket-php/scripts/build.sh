#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."

echo "Installing PHP dependencies..."
composer install --no-interaction --prefer-dist

echo "Running tests..."
composer test

echo "Build complete."
