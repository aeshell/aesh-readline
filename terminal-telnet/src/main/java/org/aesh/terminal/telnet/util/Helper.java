/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.aesh.terminal.telnet.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class Helper {

  public static Consumer<Throwable> startedHandler(CompletableFuture<?> fut) {
    return err -> {
      if (err == null) {
        fut.complete(null);
      } else {
        fut.completeExceptionally(err);
      }
    };
  }

  public static Consumer<Throwable> stoppedHandler(CompletableFuture<?> fut) {
    return err -> {
      fut.complete(null);
    };
  }

}
