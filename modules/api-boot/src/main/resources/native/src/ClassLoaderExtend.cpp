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

        // Получаем байт-код
        jsize bytecodeLength = env->GetArrayLength(bytecode);
        jbyte* bytes = env->GetByteArrayElements(bytecode, nullptr);
        if (bytes == nullptr) {
            env->ReleaseStringUTFChars(className, name);
            return;
        }

        // Получаем метод defineClass из ClassLoader
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

        // Вызываем defineClass
        jclass definedClass = (jclass)env->CallObjectMethod(
            classLoader,
            defineClassMethod,
            className,
            bytecode,
            offset,
            length,
            protectionDomain
        );

        // Освобождаем ресурсы
        env->ReleaseStringUTFChars(className, name);
        env->ReleaseByteArrayElements(bytecode, bytes, JNI_ABORT);

        // Проверяем ошибки
        if (env->ExceptionOccurred()) {
            env->ExceptionDescribe();
        }
    }
}
