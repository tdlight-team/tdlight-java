#!/bin/bash -e
# ====== Setup workspace
source ./jenkins/scripts/setup_workspace.sh

# ====== Prepare tools
cd $TD_CROSS_BIN_DIR
nice -n 5 chrt -b 0 cmake \
 -DTD_ENABLE_JNI=ON \
 -DCMAKE_BUILD_TYPE=Release \
 -DCMAKE_INSTALL_PREFIX:PATH=${TD_BIN_DIR} \
 -DCMAKE_INSTALL_BINDIR:PATH=${TD_BIN_DIR}/bin \
 $TD_SRC_DIR
nice -n 5 chrt -b 0 cmake --build . --target prepare_cross_compiling --parallel ${TRAVIS_CPU_CORES}
nice -n 5 chrt -b 0 cmake --build . --target td_generate_java_api --parallel ${TRAVIS_CPU_CORES}

# ====== Build TdApi
cd $TDNATIVES_CPP_BUILD_DIR
nice -n 5 chrt -b 0 cmake -DCMAKE_BUILD_TYPE=Release -DTD_SRC_DIR=${TD_SRC_DIR} -DTD_GENERATED_BINARIES_DIR=${TD_BIN_DIR}/td/generate \
 -DTDNATIVES_DOCS_BIN_DIR=${TDNATIVES_DOCS_BIN_DIR} -DJAVA_SRC_DIR=${JAVA_SRC_DIR} $TDNATIVES_CPP_SRC_DIR
nice -n 5 chrt -b 0 cmake --build $TDNATIVES_CPP_BUILD_DIR --target td_generate_java_api --config Release -- -j${TRAVIS_CPU_CORES}

# ====== Patch generated java code
echo "Compilation done. Patching TdApi.java"
python3 $TDLIB_SERIALIZER_DIR $JAVA_SRC_DIR/it/tdlight/tdlib/TdApi.java $JAVA_SRC_DIR/it/tdlight/tdlib/new_TdApi.java $TDLIB_SERIALIZER_DIR/headers.txt
rm $JAVA_SRC_DIR/it/tdlight/tdlib/TdApi.java
unexpand --tabs=2 $JAVA_SRC_DIR/it/tdlight/tdlib/new_TdApi.java > $JAVA_SRC_DIR/it/tdlight/tdlib/TdApi.java
rm $JAVA_SRC_DIR/it/tdlight/tdlib/new_TdApi.java
