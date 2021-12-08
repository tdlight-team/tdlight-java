#!/bin/bash -e
set -e
# OTHER REQUIRED ENVIRONMENT VARIABLES:

cd ./scripts/core/
./deploy_snapshot.sh

echo "Done."
exit 0
