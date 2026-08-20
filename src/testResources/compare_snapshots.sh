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
  relativePath="${1##*"${SNAPSHOT_BASE}"}"
  snapshotParts=($(echo ${relativePath} | sed 's/\/failed\//\n\t/g'))  
  find -E . -type f -regex ".*${RESOURCE_BASE}${snapshotParts[0]}.*${snapshotParts[1]}\$"
}

checkFiles() {
    snapshots=($(findSnapshots)) 
    for snapshot in "${snapshots[@]}"
      do
        resource=$(findResource "${snapshot}")
        echo "Comparing SNAPSHOT: '${snapshot}' vs. RESOURCE: '${resource}'"
        read -p "Press Enter to continue" </dev/tty
        code --wait --diff "${snapshot}" "${resource}"
      done
}

checkFiles