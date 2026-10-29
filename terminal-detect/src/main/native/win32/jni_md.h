/*
 * Minimal jni_md.h for cross-compiling JNI code targeting Windows
 * from a non-Windows host (e.g., Linux with MinGW-w64).
 */

#ifndef _JAVASOFT_JNI_MD_H_
#define _JAVASOFT_JNI_MD_H_

#define JNIEXPORT __declspec(dllexport)
#define JNIIMPORT __declspec(dllimport)
#ifndef JNICALL
/* Empty on x86_64 (single calling convention); __stdcall only matters for x86 */
#define JNICALL
#endif

typedef long jint;
typedef long long jlong;
typedef signed char jbyte;

#endif /* _JAVASOFT_JNI_MD_H_ */
