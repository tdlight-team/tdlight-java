#!/bin/bash -e
# OTHER REQUIRED ENVIRONMENT VARIABLES:

cd "../../"

cd "bom"
mvn -B -Dtdlight.build.type=legacy clean package
mvn -B -Dtdlight.build.type=standard clean package
cd "../"

echo "Done."
exit 0
