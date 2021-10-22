#!/bin/bash -e
set -e
# OTHER REQUIRED ENVIRONMENT VARIABLES:
#   IMPLEMENTATION_NAME = <tdlib | tdlight>

cd ./scripts/core/
./deploy_snapshot.sh

echo "Done."
exit 0
