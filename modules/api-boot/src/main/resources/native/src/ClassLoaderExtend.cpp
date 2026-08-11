#include "io_byteops_internal_util_ClassLoaderExtend.h"
#include <jni.h>
#include <cstdio>

extern "C" {
    JNIEXPORT void JNICALL Java_io_byteops_internal_util_ClassLoaderExtend_defineClass(
        JNIEnv* env,
        jclass clazz,
        jobject classLoader,
        jstring className,
        jbyteArray bytecode,
        jint offset,
        jint length,
        jobject protectionDomain
    ) {
        if (classLoader == nullptr || className == nullptr || bytecode == nullptr) {
            env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Null argument");
            return;
        }

        const char* name = env->GetStringUTFChars(className, nullptr);
        if (name == nullptr) return;

        jsize bytecodeLength = env->GetArrayLength(bytecode);
        jbyte* bytes = env->GetByteArrayElements(bytecode, nullptr);
        if (bytes == nullptr) {
            env->ReleaseStringUTFChars(className, name);
            return;
        }

        jclass clLoaderClass = env->GetObjectClass(classLoader);
        jmethodID defineClassMethod = env->GetMethodID(
            clLoaderClass,
            "defineClass",
            "(Ljava/lang/String;[BIILjava/security/ProtectionDomain;)Ljava/lang/Class;"
        );

        if (defineClassMethod == nullptr) {
            env->ReleaseStringUTFChars(className, name);
            env->ReleaseByteArrayElements(bytecode, bytes, JNI_ABORT);
            return;
        }

        jclass definedClass = (jclass)env->CallObjectMethod(
            classLoader,
            defineClassMethod,
            className,
            bytecode,
            offset,
            length,
            protectionDomain
        );

        env->ReleaseStringUTFChars(className, name);
        env->ReleaseByteArrayElements(bytecode, bytes, JNI_ABORT);

        jthrowable exception = env->ExceptionOccurred();
        if (exception != nullptr) {
            jclass linkageErrorClass = env->FindClass("java/lang/LinkageError");
            if (linkageErrorClass != nullptr && env->IsInstanceOf(exception, linkageErrorClass)) {
                env->ExceptionClear();
                env->DeleteLocalRef(linkageErrorClass);
                return;
            }

            env->ExceptionDescribe();
        }
    }
}