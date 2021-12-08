#!/bin/bash -e
set -e
# OTHER REQUIRED ENVIRONMENT VARIABLES:
#   REVISION = <revision>

cd ./scripts/core/
./deploy_release.sh

echo "Done."
exit 0
