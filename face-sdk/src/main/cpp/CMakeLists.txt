cmake_minimum_required(VERSION 3.22.1)

project("face_sdk")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(face_sdk SHARED
    face_sdk_jni.cpp
)

target_include_directories(face_sdk PRIVATE
    ${CMAKE_SOURCE_DIR}/../../../../sf6.0_android/include
)

set(SEETA_LIB_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

add_library(TenniS SHARED IMPORTED)
set_target_properties(TenniS PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libTenniS.so)

add_library(SeetaFaceDetector SHARED IMPORTED)
set_target_properties(SeetaFaceDetector PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libSeetaFaceDetector600.so)

add_library(SeetaFaceLandmarker SHARED IMPORTED)
set_target_properties(SeetaFaceLandmarker PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libSeetaFaceLandmarker600.so)

add_library(SeetaFaceRecognizer SHARED IMPORTED)
set_target_properties(SeetaFaceRecognizer PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libSeetaFaceRecognizer600.so)

add_library(SeetaFaceTracking SHARED IMPORTED)
set_target_properties(SeetaFaceTracking PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libSeetaFaceTracking600.so)

add_library(SeetaFaceAntiSpoofing SHARED IMPORTED)
set_target_properties(SeetaFaceAntiSpoofing PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libSeetaFaceAntiSpoofingX600.so)

add_library(SeetaQualityAssessor SHARED IMPORTED)
set_target_properties(SeetaQualityAssessor PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libQualityAssessor300.so)

add_library(SeetaAgePredictor SHARED IMPORTED)
set_target_properties(SeetaAgePredictor PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libSeetaAgePredictor600.so)

add_library(SeetaGenderPredictor SHARED IMPORTED)
set_target_properties(SeetaGenderPredictor PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libSeetaGenderPredictor600.so)

add_library(SeetaMaskDetector SHARED IMPORTED)
set_target_properties(SeetaMaskDetector PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libSeetaMaskDetector200.so)

add_library(SeetaEyeStateDetector SHARED IMPORTED)
set_target_properties(SeetaEyeStateDetector PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libSeetaEyeStateDetector600.so)

add_library(SeetaPoseEstimation SHARED IMPORTED)
set_target_properties(SeetaPoseEstimation PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libPoseEstimation600.so)

# Optional / legacy stubs absent in arm64 package: guard in case linker looks for them.
# add_library(SeetaFaceDetectorJava SHARED IMPORTED)
# set_target_properties(SeetaFaceDetectorJava PROPERTIES IMPORTED_LOCATION ${SEETA_LIB_DIR}/libSeetaFaceDetector600_java.so)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(face_sdk
    SeetaFaceDetector
    SeetaFaceLandmarker
    SeetaFaceRecognizer
    SeetaFaceAntiSpoofing
    SeetaFaceTracking
    SeetaQualityAssessor
    TenniS
    ${log-lib}
    ${jnigraphics-lib}
)
