# Makefile for building aesh-console.dll (Windows JNI library)
#
# Cross-compile from Linux:
#   make JAVA_HOME=/path/to/jdk
#
# Native compile on Windows with MinGW-w64:
#   make CC=gcc JAVA_HOME=C:/path/to/jdk
#
# Required: JAVA_HOME must point to a JDK with include/jni.h

CC ?= x86_64-w64-mingw32-gcc
NOEXECSTACK ?= -Wl,--noexecstack
CFLAGS = -shared -O2 -Wall -Wextra -Werror $(NOEXECSTACK)

# JDK includes: prefer the JDK's own win32 jni_md.h over the local shim.
# The local ./win32/jni_md.h is a minimal fallback for cross-compilation
# environments where the JDK's win32 include is not available.
JNI_INCLUDE = -I$(JAVA_HOME)/include
ifneq ($(wildcard $(JAVA_HOME)/include/win32/jni_md.h),)
    JNI_INCLUDE += -I$(JAVA_HOME)/include/win32
else
    JNI_INCLUDE += -I./win32
endif

OUTPUT = aesh-console.dll
SOURCE = WinConsoleNative.c

# Architecture detection for resource directory
ARCH ?= x86_64
RESOURCE_DIR = ../resources/native/windows-$(ARCH)

.PHONY: all clean install

all: $(OUTPUT)

$(OUTPUT): $(SOURCE)
	$(CC) $(CFLAGS) $(JNI_INCLUDE) -o $@ $< -lkernel32

install: $(OUTPUT)
	mkdir -p $(RESOURCE_DIR)
	cp $(OUTPUT) $(RESOURCE_DIR)/

clean:
	rm -f $(OUTPUT)
