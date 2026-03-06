# Makefile for building aesh-console.dll (Windows JNI library)
#
# Cross-compile from Linux:
#   make JAVA_HOME=/path/to/jdk
#
# On Windows with Visual Studio:
#   nmake -f Makefile.win JAVA_HOME=C:\path\to\jdk

CC = x86_64-w64-mingw32-gcc
CFLAGS = -shared -O2 -Wall
JNI_INCLUDE = -I$(JAVA_HOME)/include -I./win32

# Use the JDK's own win32 jni_md.h if available, otherwise fall back to local
ifneq ($(wildcard $(JAVA_HOME)/include/win32/jni_md.h),)
    JNI_INCLUDE += -I$(JAVA_HOME)/include/win32
endif

OUTPUT = aesh-console.dll
SOURCE = WinConsoleNative.c
RESOURCE_DIR = ../resources/native/windows-x86_64

.PHONY: all clean install

all: $(OUTPUT)

$(OUTPUT): $(SOURCE)
	$(CC) $(CFLAGS) $(JNI_INCLUDE) -o $@ $< -lkernel32

install: $(OUTPUT)
	mkdir -p $(RESOURCE_DIR)
	cp $(OUTPUT) $(RESOURCE_DIR)/

clean:
	rm -f $(OUTPUT)
