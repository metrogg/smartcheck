cmake_minimum_required(VERSION 3.22.1)

project("face_sdk")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(face_sdk SHARED
    face_sdk_jni.cpp
)

target_include_directories(face_sdk PRIVATE
    ${CMAKE_SOURCE_DIR}/../../../../third_party/seetaface2/include
)

set(SEETA_LIB_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

add_library(SeetaNet SHARED IMPORTED)
set_target_properties(SeetaNet PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libSeetaNet.so)

add_library(SeetaFaceDetector SHARED IMPORTED)
set_target_properties(SeetaFaceDetector PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libSeetaFaceDetector.so)

add_library(SeetaFaceLandmarker SHARED IMPORTED)
set_target_properties(SeetaFaceLandmarker PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libSeetaFaceLandmarker.so)

add_library(SeetaFaceRecognizer SHARED IMPORTED)
set_target_properties(SeetaFaceRecognizer PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libSeetaFaceRecognizer.so)

add_library(SeetaFaceTracker SHARED IMPORTED)
set_target_properties(SeetaFaceTracker PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libSeetaFaceTracker.so)

add_library(SeetaQualityAssessor SHARED IMPORTED)
set_target_properties(SeetaQualityAssessor PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libSeetaQualityAssessor.so)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(face_sdk
    SeetaFaceDetector
    SeetaFaceLandmarker
    SeetaFaceRecognizer
    SeetaFaceTracker
    SeetaQualityAssessor
    SeetaNet
    ${log-lib}
    ${jnigraphics-lib}
)
