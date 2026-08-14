#include <jni.h>

#ifndef _Included_io_byteops_internal_util_ClassLoaderExtend
#define _Included_io_byteops_internal_util_ClassLoaderExtend
#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT void JNICALL Java_io_byteops_internal_util_ClassLoaderExtend_defineClass
  (JNIEnv *, jclass, jobject, jstring, jbyteArray, jint, jint, jobject);

#ifdef __cplusplus
}
#endif
#endif
