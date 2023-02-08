#!/bin/bash -e
# OTHER REQUIRED ENVIRONMENT VARIABLES:
#   REVISION = <revision>
#   SSL_TYPE = <ssl1|ssl3>

# Check variables correctness
if [ -z "${REVISION}" ]; then
	echo "Missing parameter: REVISION"
	exit 1
fi
# Check variables correctness
if [ -z "${SSL_TYPE}" ]; then
	echo "Missing parameter: SSL_TYPE"
	exit 1
fi

SSL_SUFFIX=""
if [[ "$SSL_TYPE" == "ssl3" ]]; then
	SSL_SUFFIX="-ssl3"
fi

cd "../../"

cd "bom"
mvn -B -Drevision="${REVISION}" -DrevisionSuffix="" -DnativesSsl3Suffix="${SSL_SUFFIX}" clean deploy
cd "../"

echo "Done."
exit 0
