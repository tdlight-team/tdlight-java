#!/bin/bash -e
# OTHER REQUIRED ENVIRONMENT VARIABLES:
#   IMPLEMENTATION_NAME = <tdlib | tdlight>

# Check variables correctness
if [ -z "${IMPLEMENTATION_NAME}" ]; then
	echo "Missing parameter: IMPLEMENTATION_NAME"
	exit 1
fi

cd ../../${IMPLEMENTATION_NAME}

mvn -B clean package --file pom.xml

echo "Done."
exit 0
