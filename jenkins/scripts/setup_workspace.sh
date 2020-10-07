#!/bin/bash -e
# ====== Setup environment variables
source ./jenkins/scripts/setup_variables.sh

# ====== Environment setup
[ -d $TD_BUILD_DIR ] || mkdir -p $TD_BUILD_DIR
[ -d $TD_BIN_DIR ] || mkdir -p $TD_BIN_DIR
[ -d $TD_NATIVE_BIN_DIR ] || mkdir -p $TD_NATIVE_BIN_DIR
[ -d $TD_CROSS_BIN_DIR ] || mkdir -p $TD_CROSS_BIN_DIR
[ -d $TDNATIVES_CPP_BUILD_DIR ] || mkdir -p $TDNATIVES_CPP_BUILD_DIR

echo "CCACHE statistics:"
ccache -s
