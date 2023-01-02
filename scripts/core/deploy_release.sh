#!/bin/bash -e
# OTHER REQUIRED ENVIRONMENT VARIABLES:
#   REVISION = <revision>

# Check variables correctness
if [ -z "${REVISION}" ]; then
	echo "Missing parameter: REVISION"
	exit 1
fi

cd "../../"

cd "bom"
mvn -B -Drevision="${REVISION}" -Dtdlight.build.type=legacy clean deploy
mvn -B -Drevision="${REVISION}" -Dtdlight.build.type=standard clean deploy
cd "../"

echo "Done."
exit 0
