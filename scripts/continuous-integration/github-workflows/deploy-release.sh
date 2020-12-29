#!/bin/bash -e
set -e
# OTHER REQUIRED ENVIRONMENT VARIABLES:
#   REVISION = <revision>
#   IMPLEMENTATION_NAME = <tdlib | tdlight>

cd ./scripts/core/
./deploy_release.sh

echo "Done."
exit 0
