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

/* Validate a console handle passed from Java. Returns 0 (invalid) or 1 (valid). */
static int isValidHandle(jlong handle) {
    return handle != -1L && handle != 0L;
}

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
 * Returns:   the console mode flags, or -1 on error/invalid handle
 */
JNIEXPORT jint JNICALL Java_org_aesh_terminal_tty_impl_WinConsoleNative_getConsoleMode
  (JNIEnv *env, jclass cls, jlong handle)
{
#ifdef _WIN32
    if (!isValidHandle(handle))
        return -1;
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
 * Returns:   true if successful, false on error/invalid handle
 */
JNIEXPORT jboolean JNICALL Java_org_aesh_terminal_tty_impl_WinConsoleNative_setConsoleMode
  (JNIEnv *env, jclass cls, jlong handle, jint mode)
{
#ifdef _WIN32
    if (!isValidHandle(handle))
        return JNI_FALSE;
    return SetConsoleMode((HANDLE)(intptr_t)handle, (DWORD)mode) ? JNI_TRUE : JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}

/*
 * Class:     org_aesh_terminal_tty_impl_WinConsoleNative
 * Method:    getConsoleOutputCP
 * Returns:   the output code page, or -1 on non-Windows
 */
JNIEXPORT jint JNICALL Java_org_aesh_terminal_tty_impl_WinConsoleNative_getConsoleOutputCP
  (JNIEnv *env, jclass cls)
{
#ifdef _WIN32
    UINT cp = GetConsoleOutputCP();
    return cp != 0 ? (jint)cp : -1;
#else
    return -1;
#endif
}

/*
 * Class:     org_aesh_terminal_tty_impl_WinConsoleNative
 * Method:    getConsoleSize
 * Returns:   int[]{width, height} or NULL on error/invalid handle
 */
JNIEXPORT jintArray JNICALL Java_org_aesh_terminal_tty_impl_WinConsoleNative_getConsoleSize
  (JNIEnv *env, jclass cls, jlong handle)
{
#ifdef _WIN32
    if (!isValidHandle(handle))
        return NULL;
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
 * Method:    readConsoleInputEvent
 * Returns:   int[] where first element is the event type:
 *            KEY_EVENT (1):                {1, keyDown, repeatCount, vKeyCode, unicodeChar, controlKeyState}
 *            MOUSE_EVENT (2):              {2, x, y, buttonState, controlKeyState, eventFlags}
 *            WINDOW_BUFFER_SIZE_EVENT (4): {4, width, height}
 *            Returns NULL for other event types (focus, menu) or on error/invalid handle.
 */
JNIEXPORT jintArray JNICALL Java_org_aesh_terminal_tty_impl_WinConsoleNative_readConsoleInputEvent
  (JNIEnv *env, jclass cls, jlong handle)
{
#ifdef _WIN32
    if (!isValidHandle(handle))
        return NULL;
    INPUT_RECORD record;
    DWORD eventsRead;
    if (!ReadConsoleInputW((HANDLE)(intptr_t)handle, &record, 1, &eventsRead))
        return NULL;
    if (eventsRead == 0)
        return NULL;

    if (record.EventType == KEY_EVENT) {
        KEY_EVENT_RECORD *ke = &record.Event.KeyEvent;
        jint fields[6];
        fields[0] = 1; /* KEY_EVENT */
        fields[1] = ke->bKeyDown ? 1 : 0;
        fields[2] = (jint)ke->wRepeatCount;
        fields[3] = (jint)ke->wVirtualKeyCode;
        fields[4] = (jint)ke->uChar.UnicodeChar;
        fields[5] = (jint)ke->dwControlKeyState;
        jintArray result = (*env)->NewIntArray(env, 6);
        if (result == NULL) return NULL;
        (*env)->SetIntArrayRegion(env, result, 0, 6, fields);
        return result;
    }

    if (record.EventType == WINDOW_BUFFER_SIZE_EVENT) {
        COORD size = record.Event.WindowBufferSizeEvent.dwSize;
        jint fields[3];
        fields[0] = 4; /* WINDOW_BUFFER_SIZE_EVENT */
        fields[1] = (jint)size.X;
        fields[2] = (jint)size.Y;
        jintArray result = (*env)->NewIntArray(env, 3);
        if (result == NULL) return NULL;
        (*env)->SetIntArrayRegion(env, result, 0, 3, fields);
        return result;
    }

    if (record.EventType == MOUSE_EVENT) {
        MOUSE_EVENT_RECORD *me = &record.Event.MouseEvent;
        jint fields[6];
        fields[0] = 2; /* MOUSE_EVENT */
        fields[1] = (jint)me->dwMousePosition.X;
        fields[2] = (jint)me->dwMousePosition.Y;
        fields[3] = (jint)me->dwButtonState;
        fields[4] = (jint)me->dwControlKeyState;
        fields[5] = (jint)me->dwEventFlags;
        jintArray result = (*env)->NewIntArray(env, 6);
        if (result == NULL) return NULL;
        (*env)->SetIntArrayRegion(env, result, 0, 6, fields);
        return result;
    }

    /* Other event types (menu, focus) - ignore */
    return NULL;
#else
    return NULL;
#endif
}

/*
 * Class:     org_aesh_terminal_tty_impl_WinConsoleNative
 * Method:    writeConsole
 * Returns:   true if all characters were written, false on error/partial write/invalid args
 */
JNIEXPORT jboolean JNICALL Java_org_aesh_terminal_tty_impl_WinConsoleNative_writeConsole
  (JNIEnv *env, jclass cls, jlong handle, jcharArray buffer, jint length)
{
#ifdef _WIN32
    if (!isValidHandle(handle))
        return JNI_FALSE;
    if (buffer == NULL || length < 0)
        return JNI_FALSE;
    jsize arrayLen = (*env)->GetArrayLength(env, buffer);
    if (length > arrayLen)
        return JNI_FALSE;
    jchar *chars = (*env)->GetCharArrayElements(env, buffer, NULL);
    if (chars == NULL) return JNI_FALSE;
    DWORD written;
    BOOL ok = WriteConsoleW((HANDLE)(intptr_t)handle, chars, (DWORD)length, &written, NULL);
    (*env)->ReleaseCharArrayElements(env, buffer, chars, JNI_ABORT);
    return (ok && written == (DWORD)length) ? JNI_TRUE : JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}
