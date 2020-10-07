cmake_minimum_required(VERSION 3.12.0 FATAL_ERROR)

project(JTDLib VERSION 1.0 LANGUAGES CXX)
set(CMAKE_BUILD_TYPE Release)
set(TD_ENABLE_JNI ON)

if (POLICY CMP0054)
  # do not expand quoted arguments
  cmake_policy(SET CMP0054 NEW)
endif()

cmake_policy(SET CMP0074 NEW)

#find_package(Td REQUIRED)

if (NOT JNI_FOUND)
  find_package(JNI REQUIRED)
endif()
message(STATUS "Found JNI: ${JNI_INCLUDE_DIRS} ${JNI_LIBRARIES}")

if (NOT Java_FOUND)
  find_package(Java 1.6 REQUIRED)
endif()
message(STATUS "Found Java: ${Java_JAVAC_EXECUTABLE} ${Java_JAVADOC_EXECUTABLE}")

message(STATUS "Java Source Directory: ${JAVA_SRC_DIR}")

# Generating TdApi.java
find_program(PHP_EXECUTABLE php)

message(STATUS "PHP Executable: ${PHP_EXECUTABLE}")

set(TD_API_JAVA_PACKAGE "it/tdlight/tdlib")
set(TD_API_JAVA_PATH ${JAVA_SRC_DIR})
set(TD_API_TLO_PATH ${TD_SRC_DIR}/td/generate/scheme/td_api.tlo)
set(TD_API_TL_PATH ${TD_SRC_DIR}/td/generate/scheme/td_api.tl)
set(JAVADOC_TL_DOCUMENTATION_GENERATOR_PATH ${TD_SRC_DIR}/td/generate/JavadocTlDocumentationGenerator.php)
set(GENERATE_JAVA_API_CMD ${TD_GENERATED_BINARIES_DIR}/td_generate_java_api TdApi ${TD_API_TLO_PATH} ${TD_API_JAVA_PATH} ${TD_API_JAVA_PACKAGE})
if (PHP_EXECUTABLE)
  set(GENERATE_JAVA_API_CMD ${GENERATE_JAVA_API_CMD} && ${PHP_EXECUTABLE} ${JAVADOC_TL_DOCUMENTATION_GENERATOR_PATH} ${TD_API_TL_PATH} ${TD_API_JAVA_PATH}/${TD_API_JAVA_PACKAGE}/TdApi.java)
endif()

add_custom_target(td_generate_java_api
  COMMAND ${GENERATE_JAVA_API_CMD}
  COMMENT "Generating Java TDLib API source files"
  DEPENDS ${TD_GENERATED_BINARIES_DIR}/td_generate_java_api ${TD_API_TLO_PATH} ${TD_API_TL_PATH} ${JAVADOC_TL_DOCUMENTATION_GENERATOR_PATH}
)

add_custom_target(generate_javadoc
  COMMAND ${Java_JAVADOC_EXECUTABLE} -d ${TDNATIVES_DOCS_BIN_DIR} it.tdlight.tdlib
  WORKING_DIRECTORY ${TD_API_JAVA_PATH}
  COMMENT "Generating Javadoc documentation"
  DEPENDS td_generate_java_api
)

#add_dependencies(td_generate_java_api generate_javadoc)
