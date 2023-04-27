#!/bin/bash -e
# OTHER REQUIRED ENVIRONMENT VARIABLES:

cd "../../"

cd "bom"
mvn -B -P "java8,java17" clean deploy
cd "../"

echo "Done."
exit 0
