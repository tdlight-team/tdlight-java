#!/bin/bash -e
# OTHER REQUIRED ENVIRONMENT VARIABLES:

cd "../../"

cd "bom"
mvn -B package
cd "../"

echo "Done."
exit 0
