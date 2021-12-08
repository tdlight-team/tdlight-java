#!/bin/bash -e
# OTHER REQUIRED ENVIRONMENT VARIABLES:

cd "../../"

cd "bom"
mvn -B clean deploy
cd "../"

echo "Done."
exit 0
