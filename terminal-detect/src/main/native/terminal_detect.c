/*
 * JNI bridge for terminal-detect: minimal POSIX terminal access.
 *
 * Provides tcgetattr/tcsetattr/open/close for direct terminal attribute
 * manipulation without spawning stty subprocesses.
 *
 * The termios struct is passed as an opaque byte array — all flag
 * interpretation happens in Java.
 *
 * Licensed under the Apache License, Version 2.0.
 */

#include <jni.h>
#include <termios.h>
#include <unistd.h>
#include <fcntl.h>
#include <poll.h>
#include <errno.h>

JNIEXPORT jint JNICALL
Java_org_aesh_terminal_detect_TerminalNative_openTty(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return open("/dev/tty", O_RDWR);
}

JNIEXPORT jint JNICALL
Java_org_aesh_terminal_detect_TerminalNative_closeFd(JNIEnv *env, jclass cls, jint fd)
{
    (void)env;
    (void)cls;
    return close(fd);
}

JNIEXPORT jbyteArray JNICALL
Java_org_aesh_terminal_detect_TerminalNative_tcgetattr(JNIEnv *env, jclass cls, jint fd)
{
    (void)cls;
    struct termios t;
    if (tcgetattr(fd, &t) != 0) {
        return NULL;
    }
    jbyteArray result = (*env)->NewByteArray(env, sizeof(struct termios));
    if (result == NULL) {
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, result, 0, sizeof(struct termios), (const jbyte *)&t);
    return result;
}

JNIEXPORT jint JNICALL
Java_org_aesh_terminal_detect_TerminalNative_tcsetattr(JNIEnv *env, jclass cls,
                                                        jint fd, jint action, jbyteArray termios)
{
    (void)cls;
    if (termios == NULL) {
        return -1;
    }
    jsize len = (*env)->GetArrayLength(env, termios);
    if (len != sizeof(struct termios)) {
        return -1;
    }
    struct termios t;
    (*env)->GetByteArrayRegion(env, termios, 0, sizeof(struct termios), (jbyte *)&t);
    return tcsetattr(fd, action, &t);
}

JNIEXPORT jint JNICALL
Java_org_aesh_terminal_detect_TerminalNative_termiosSize(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return (jint)sizeof(struct termios);
}

JNIEXPORT jint JNICALL
Java_org_aesh_terminal_detect_TerminalNative_writeAll(JNIEnv *env, jclass cls,
                                                       jint fd, jbyteArray data)
{
    (void)cls;
    if (data == NULL) return -1;
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *buf = (*env)->GetByteArrayElements(env, data, NULL);
    if (buf == NULL) return -1;

    jint total = 0;
    while (total < len) {
        ssize_t n = write(fd, buf + total, len - total);
        if (n < 0) {
            if (errno == EINTR) continue;
            (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
            return -1;
        }
        total += (jint)n;
    }
    (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
    return total;
}

/*
 * Count response terminators in the buffer:
 * - BEL (0x07) for OSC responses
 * - ESC \ (ST) for OSC responses
 * - 'c' after ESC [ for DA1 responses
 */
static int count_terminators(const char *buf, int len)
{
    int count = 0;
    int in_csi = 0;
    int i;
    for (i = 0; i < len; i++) {
        char c = buf[i];
        if (c == '\007') {
            count++;
            in_csi = 0;
        } else if (c == '\033' && i + 1 < len) {
            if (buf[i + 1] == '\\') {
                count++;
                i++;
            } else if (buf[i + 1] == '[') {
                in_csi = 1;
                i++;
            }
        } else if (in_csi && c == 'c') {
            count++;
            in_csi = 0;
        } else if (in_csi && !((c >= '0' && c <= '9') || c == ';' || c == '?')) {
            in_csi = 0;
        }
    }
    return count;
}

JNIEXPORT jbyteArray JNICALL
Java_org_aesh_terminal_detect_TerminalNative_readWithTimeout(JNIEnv *env, jclass cls,
                                                              jint fd, jint timeoutMs,
                                                              jint expectedResponses)
{
    (void)cls;
    /* Read terminal responses using poll() with precise timeout.
     * Exits early when all expectedResponses terminators are found.
     * Uses a 4096-byte buffer — enough for all terminal responses. */
    char buf[4096];
    int total = 0;
    int remaining = timeoutMs;

    int gotFirstData = 0;

    while (total < (int)sizeof(buf) - 1 && remaining > 0) {
        struct pollfd pfd;
        pfd.fd = fd;
        pfd.events = POLLIN;
        pfd.revents = 0;

        /* Use full remaining timeout until first data arrives,
         * then switch to short follow-up for trailing responses. */
        int pollTime = gotFirstData ? (remaining < 20 ? remaining : 20) : remaining;
        int rc = poll(&pfd, 1, pollTime);

        if (rc < 0) {
            if (errno == EINTR) continue;
            break; /* real error */
        }
        if (rc == 0) {
            remaining -= pollTime;
            continue;
        }
        if (pfd.revents & (POLLHUP | POLLERR)) {
            break;
        }
        if (pfd.revents & POLLIN) {
            ssize_t n = read(fd, buf + total, sizeof(buf) - 1 - total);
            if (n <= 0) break;
            total += (int)n;
            gotFirstData = 1;
            /* Check if we have all expected responses */
            if (count_terminators(buf, total) >= expectedResponses)
                break;
            /* Short follow-up timeout for remaining data */
            remaining = 20;
        }
    }

    if (total <= 0) return NULL;

    jbyteArray result = (*env)->NewByteArray(env, total);
    if (result == NULL) return NULL;
    (*env)->SetByteArrayRegion(env, result, 0, total, (const jbyte *)buf);
    return result;
}
