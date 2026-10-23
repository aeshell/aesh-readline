/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <windows.h>
#else
#include <unistd.h>
#endif

/*
 * Test-only sh/sh.exe for ProcessBuilder("sh", "-c", "test -t N").
 * Record actual execution before keeping both output pipes open. Unlike a
 * shell script named sh, this executable is launchable by the Windows JVM.
 * Compile only in the fixture job; never package it with the library.
 */
int main(int argc, char **argv) {
    const char *directory = getenv("AESH_TTY_PROBE_MARKER_DIR");
    char path[4096];
    FILE *marker;
    int written;
    unsigned long pid;

    if (argc != 3 || strcmp(argv[1], "-c") != 0
            || strlen(argv[2]) != 9 || strncmp(argv[2], "test -t ", 8) != 0
            || argv[2][8] < '0' || argv[2][8] > '2') {
        fputs("unexpected stalled-tty-probe invocation\n", stderr);
        return 64;
    }
    if (directory == NULL || directory[0] == '\0') {
        fputs("AESH_TTY_PROBE_MARKER_DIR is required\n", stderr);
        return 65;
    }
#ifdef _WIN32
    pid = (unsigned long) GetCurrentProcessId();
#else
    pid = (unsigned long) getpid();
#endif
    written = snprintf(path, sizeof(path), "%s/%lu-%c.started", directory, pid, argv[2][8]);
    if (written < 0 || (size_t) written >= sizeof(path)) {
        return 66;
    }
    marker = fopen(path, "wb");
    if (marker == NULL) {
        perror("cannot create probe marker");
        return 67;
    }
    written = fprintf(marker, "fd=%c\n", argv[2][8]);
    if (fclose(marker) != 0 || written < 0) {
        return 68;
    }
#ifdef _WIN32
    Sleep(300000);
#else
    sleep(300);
#endif
    /* A healthy parent kills us at its probe deadline, long before here. */
    return 69;
}
