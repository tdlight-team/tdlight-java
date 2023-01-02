#!/bin/bash -e
# OTHER REQUIRED ENVIRONMENT VARIABLES:

cd "../../"

cd "bom"
mvn -B clean deploy -Dtdlight.build.type=standard
cd "../"

echo "Done."
exit 0
