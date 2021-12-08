#!/bin/bash -e
set -e
# OTHER REQUIRED ENVIRONMENT VARIABLES:

cd ./scripts/core/
./only_compile_release.sh

echo "Done."
exit 0
