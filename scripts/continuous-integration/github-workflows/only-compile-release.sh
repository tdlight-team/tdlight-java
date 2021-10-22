#!/bin/bash -e
set -e
# OTHER REQUIRED ENVIRONMENT VARIABLES:
#   IMPLEMENTATION_NAME = <tdlib | tdlight>

cd ./scripts/core/
./only_compile_release.sh

echo "Done."
exit 0
