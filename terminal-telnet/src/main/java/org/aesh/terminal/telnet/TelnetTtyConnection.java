/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
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

package org.aesh.terminal.telnet;

import org.aesh.io.Decoder;
import org.aesh.io.Encoder;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Device;
import org.aesh.terminal.EventDecoder;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.TtyOutputMode;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A telnet handler that implements {@link org.aesh.terminal.Connection}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public final class TelnetTtyConnection extends TelnetHandler implements Connection {

  private final boolean inBinary;
  private final boolean outBinary;
  private boolean receivingBinary;
  private boolean sendingBinary;
  private boolean accepted;
  private Size size;
  private String terminalType;
  private Consumer<Size> sizeHandler;
  private Consumer<Void> closeHandler;
  protected TelnetConnection conn;
  private final Charset charset;
  private final EventDecoder eventDecoder = new EventDecoder(3, 4, 26);
  private final ReadBuffer readBuffer = new ReadBuffer(this::execute);
  private final Decoder decoder = new Decoder(512, TelnetCharset.INSTANCE, readBuffer);
  private final Encoder encoder = new Encoder(StandardCharsets.US_ASCII, data -> conn.write(data));
  private final Consumer<int[]> stdout = new TtyOutputMode(encoder);
  private final Consumer<Connection> handler;
  private long lastAccessedTime = System.currentTimeMillis();
  private Device device;
  private Attributes attributes;

  public TelnetTtyConnection(boolean inBinary, boolean outBinary, Charset charset, Consumer<Connection> handler) {
    this.charset = charset;
    this.inBinary = inBinary;
    this.outBinary = outBinary;
    this.handler = handler;
  }

  public long lastAccessedTime() {
    return lastAccessedTime;
  }

  public String terminalType() {
    return terminalType;
  }

  public void execute(Runnable task) {
    conn.execute(task);
  }

  public void schedule(Runnable task, long delay, TimeUnit unit) {
    conn.schedule(task, delay, unit);
  }

  @Override
  public Charset inputEncoding() {
    return inBinary ? charset : StandardCharsets.US_ASCII;
  }

  @Override
  public Charset outputEncoding() {
    return outBinary ? charset : StandardCharsets.US_ASCII;
  }

  @Override
  public boolean supportsAnsi() {
    return true;
  }

  @Override
  protected void onSendBinary(boolean binary) {
    sendingBinary = binary;
    if (binary) {
      encoder.setCharset(charset);
    }
    checkAccept();
  }

  @Override
  protected void onReceiveBinary(boolean binary) {
    receivingBinary = binary;
    if (binary) {
      decoder.setCharset(charset);
    }
    checkAccept();
  }

  @Override
  protected void onData(byte[] data) {
    lastAccessedTime = System.currentTimeMillis();
    decoder.write(data);
  }

  @Override
  protected void onOpen(TelnetConnection conn) {
    this.conn = conn;

    //set default size for now
      size = new Size(80, 24);

    // Kludge mode
    conn.writeWillOption(Option.ECHO);
    conn.writeWillOption(Option.SGA);

    //
    if (inBinary) {
      conn.writeDoOption(Option.BINARY);
    }
    if (outBinary) {
      conn.writeWillOption(Option.BINARY);
    }

    // Window size
    conn.writeDoOption(Option.NAWS);

    // Get some info about user
    conn.writeDoOption(Option.TERMINAL_TYPE);

    attributes = new Attributes();

    //
    checkAccept();
  }

  private void checkAccept() {
    if (!accepted) {
      if (!outBinary | (outBinary && sendingBinary)) {
        if (!inBinary | (inBinary && receivingBinary)) {
          accepted = true;
          readBuffer.setReadHandler(eventDecoder);
          handler.accept(this);
        }
      }
    }
  }

  @Override
  protected void onTerminalType(String terminalType) {
    this.terminalType = terminalType;

    device = new TelnetDevice(terminalType);

  }

  @Override
  public Size size() {
    return size;
  }

  @Override
  protected void onSize(int width, int height) {
    this.size = new Size(width, height);
    if (sizeHandler != null) {
      sizeHandler.accept(size);
    }
  }

  @Override
  public Device device() {
      //create a default device for now
      if(device == null)
          device = new TelnetDevice("vt100");
    return device;
  }

  @Override
  public Consumer<Size> getSizeHandler() {
    return sizeHandler;
  }

  @Override
  public void setSizeHandler(Consumer<Size> handler) {
    this.sizeHandler = handler;
  }

  @Override
  public Consumer<Signal> getSignalHandler() {
    return eventDecoder.getSignalHandler();
  }

  @Override
  public void setSignalHandler(Consumer<Signal> handler) {
    eventDecoder.setSignalHandler(handler);
  }

  @Override
  public Consumer<int[]> getStdinHandler() {
    return eventDecoder.getInputHandler();
  }

  @Override
  public void setStdinHandler(Consumer<int[]> handler) {
    eventDecoder.setInputHandler(handler);
  }

  @Override
  public Consumer<int[]> stdoutHandler() {
    return stdout;
  }

  @Override
  public void setCloseHandler(Consumer<Void> closeHandler) {
    this.closeHandler = closeHandler;
  }

  @Override
  public Consumer<Void> getCloseHandler() {
    return closeHandler;
  }

  @Override
  protected void onClose() {
    if (closeHandler != null) {
      closeHandler.accept(null);
    }
  }

  @Override
  public void close() {
    conn.close();
  }

  @Override
  public void openBlocking() {
  }

  @Override
  public void openNonBlocking() {
  }

  @Override
  public boolean put(Capability capability, Object... params) {
    return false;
  }

  @Override
  public Attributes getAttributes() {
    return attributes;
  }

  @Override
  public void setAttributes(Attributes attr) {

  }
}
