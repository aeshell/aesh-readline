/*
 * JNI implementation for org.aesh.terminal.tty.impl.WinConsoleNative.
 * Wraps Windows console API (Kernel32) functions.
 *
 * Cross-compile from Linux with MinGW-w64:
 *   x86_64-w64-mingw32-gcc -shared -O2 -o aesh-console.dll \
 *       -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/win32 \
 *       WinConsoleNative.c -lkernel32
 */

#ifdef _WIN32
#include <windows.h>
#else
/* Cross-compilation stubs for syntax checking */
#include <stdint.h>
typedef void* HANDLE;
typedef unsigned long DWORD;
typedef int BOOL;
#define INVALID_HANDLE_VALUE ((HANDLE)(intptr_t)-1)
#define TRUE 1
#define FALSE 0
#endif

#include <jni.h>

/*
 * Class:     org_aesh_terminal_tty_impl_WinConsoleNative
 * Method:    getStdHandle
 */
JNIEXPORT jlong JNICALL Java_org_aesh_terminal_tty_impl_WinConsoleNative_getStdHandle
  (JNIEnv *env, jclass cls, jint nStdHandle)
{
#ifdef _WIN32
    HANDLE h = GetStdHandle((DWORD)nStdHandle);
    if (h == INVALID_HANDLE_VALUE || h == NULL)
        return -1L;
    return (jlong)(intptr_t)h;
#else
    return -1L;
#endif
}

/*
 * Class:     org_aesh_terminal_tty_impl_WinConsoleNative
 * Method:    getConsoleMode
 */
JNIEXPORT jint JNICALL Java_org_aesh_terminal_tty_impl_WinConsoleNative_getConsoleMode
  (JNIEnv *env, jclass cls, jlong handle)
{
#ifdef _WIN32
    DWORD mode;
    if (!GetConsoleMode((HANDLE)(intptr_t)handle, &mode))
        return -1;
    return (jint)mode;
#else
    return -1;
#endif
}

/*
 * Class:     org_aesh_terminal_tty_impl_WinConsoleNative
 * Method:    setConsoleMode
 */
JNIEXPORT jboolean JNICALL Java_org_aesh_terminal_tty_impl_WinConsoleNative_setConsoleMode
  (JNIEnv *env, jclass cls, jlong handle, jint mode)
{
#ifdef _WIN32
    return SetConsoleMode((HANDLE)(intptr_t)handle, (DWORD)mode) ? JNI_TRUE : JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}

/*
 * Class:     org_aesh_terminal_tty_impl_WinConsoleNative
 * Method:    getConsoleOutputCP
 */
JNIEXPORT jint JNICALL Java_org_aesh_terminal_tty_impl_WinConsoleNative_getConsoleOutputCP
  (JNIEnv *env, jclass cls)
{
#ifdef _WIN32
    return (jint)GetConsoleOutputCP();
#else
    return 65001; /* UTF-8 fallback */
#endif
}

/*
 * Class:     org_aesh_terminal_tty_impl_WinConsoleNative
 * Method:    getConsoleSize
 * Returns:   int[]{width, height} or NULL on error
 */
JNIEXPORT jintArray JNICALL Java_org_aesh_terminal_tty_impl_WinConsoleNative_getConsoleSize
  (JNIEnv *env, jclass cls, jlong handle)
{
#ifdef _WIN32
    CONSOLE_SCREEN_BUFFER_INFO info;
    if (!GetConsoleScreenBufferInfo((HANDLE)(intptr_t)handle, &info))
        return NULL;
    jint size[2];
    size[0] = info.srWindow.Right - info.srWindow.Left + 1;
    size[1] = info.srWindow.Bottom - info.srWindow.Top + 1;
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result == NULL) return NULL;
    (*env)->SetIntArrayRegion(env, result, 0, 2, size);
    return result;
#else
    return NULL;
#endif
}

/*
 * Class:     org_aesh_terminal_tty_impl_WinConsoleNative
 * Method:    readConsoleKeyEvent
 * Returns:   int[]{keyDown, repeatCount, vKeyCode, unicodeChar, controlKeyState}
 *            or NULL if no key event was read
 */
JNIEXPORT jintArray JNICALL Java_org_aesh_terminal_tty_impl_WinConsoleNative_readConsoleKeyEvent
  (JNIEnv *env, jclass cls, jlong handle)
{
#ifdef _WIN32
    INPUT_RECORD record;
    DWORD eventsRead;
    if (!ReadConsoleInputW((HANDLE)(intptr_t)handle, &record, 1, &eventsRead))
        return NULL;
    if (eventsRead == 0 || record.EventType != KEY_EVENT)
        return NULL;
    KEY_EVENT_RECORD *ke = &record.Event.KeyEvent;
    jint fields[5];
    fields[0] = ke->bKeyDown ? 1 : 0;
    fields[1] = (jint)ke->wRepeatCount;
    fields[2] = (jint)ke->wVirtualKeyCode;
    fields[3] = (jint)ke->uChar.UnicodeChar;
    fields[4] = (jint)ke->dwControlKeyState;
    jintArray result = (*env)->NewIntArray(env, 5);
    if (result == NULL) return NULL;
    (*env)->SetIntArrayRegion(env, result, 0, 5, fields);
    return result;
#else
    return NULL;
#endif
}

/*
 * Class:     org_aesh_terminal_tty_impl_WinConsoleNative
 * Method:    writeConsole
 */
JNIEXPORT jboolean JNICALL Java_org_aesh_terminal_tty_impl_WinConsoleNative_writeConsole
  (JNIEnv *env, jclass cls, jlong handle, jcharArray buffer, jint length)
{
#ifdef _WIN32
    jchar *chars = (*env)->GetCharArrayElements(env, buffer, NULL);
    if (chars == NULL) return JNI_FALSE;
    DWORD written;
    BOOL ok = WriteConsoleW((HANDLE)(intptr_t)handle, chars, (DWORD)length, &written, NULL);
    (*env)->ReleaseCharArrayElements(env, buffer, chars, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}
