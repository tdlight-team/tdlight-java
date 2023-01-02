#!/bin/bash -e
# OTHER REQUIRED ENVIRONMENT VARIABLES:

cd "../../"

cd "bom"
mvn -B clean package
cd "../"

echo "Done."
exit 0
