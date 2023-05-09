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
mvn -B -Drevision="${REVISION}" -P "java8,java17" clean deploy
cd "../"

echo "Done."
exit 0
