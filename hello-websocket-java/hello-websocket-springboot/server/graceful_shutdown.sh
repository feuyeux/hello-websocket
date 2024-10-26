#!/bin/bash
# shellcheck disable=SC2046
kill -15 $(jps | grep hello | awk '{print $1}')
