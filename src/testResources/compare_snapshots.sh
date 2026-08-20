#!/usr/bin/env bash

# Strict bash
set -euo pipefail
IFS=$'\n\t'

# Standard paths
SNAPSHOT_BASE='build/resources/test/'
RESOURCE_BASE='src/testResources/'

findSnapshots() {
    find -E . -type f -regex ".*${SNAPSHOT_BASE}.*/failed/.*\$"
}

findResource() {
    # Remove base path string
    relativePath="${1##*"${SNAPSHOT_BASE}"}"
    
    # Split at `failed` dir
    snapshotParts=($(echo ${relativePath} | sed 's/\/failed\//\n\t/g'))
    
    # Find matching resource
    find -E . -type f -regex ".*${RESOURCE_BASE}${snapshotParts[0]}.*${snapshotParts[1]}\$"
}

# YES or NO prompt
function yes_or_no {
    while true; do
        read -p "$* [y/n]: " yn
        case $yn in
            [Yy]*) return 0 ;;
            [Nn]*) return 1 ;;
        esac
    done
}

# Compare all snapshots
compareSnapshots() {
    snapshots=($(findSnapshots)) 
    for snapshot in "${snapshots[@]}"
        do
        resource=$(findResource "${snapshot}")
        echo "Comparing SNAPSHOT: '${snapshot}' vs. RESOURCE: '${resource}'"

        yes_or_no "Compare files?" && eval "${DIFFCMD} ${snapshot} ${resource}"

        yes_or_no "Do you want to delete the snapshot?" && rm "${snapshot}"
        done
}

echo "Use custom command to compare differences?"
read -e -i 'code --wait --diff' DIFFCMD

compareSnapshots