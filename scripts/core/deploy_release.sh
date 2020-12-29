#!/bin/bash -e
# OTHER REQUIRED ENVIRONMENT VARIABLES:
#   REVISION = <revision>
#   IMPLEMENTATION_NAME = <tdlib | tdlight>

# Check variables correctness
if [ -z "${REVISION}" ]; then
	echo "Missing parameter: REVISION"
	exit 1
fi
if [ -z "${IMPLEMENTATION_NAME}" ]; then
	echo "Missing parameter: IMPLEMENTATION_NAME"
	exit 1
fi

cd ../../generated

mvn -B -Drevision=${REVISION} clean deploy --file ${IMPLEMENTATION_NAME}/pom.xml

echo "Done."
exit 0
