package net.pieroxy.imf.rules.matchers;

import java.io.File;

/**
 * Same role as {@link SubjectClassifierContext}, for the header classifier's own model file
 * instead — a separate {@link ThreadLocal}, not a shared/generalized one, since the two models
 * are independent and set at the same call site anyway (see {@code MailAccount.run()}).
 */
public final class HeaderClassifierContext {
  private static final ThreadLocal<File> MODEL_FILE = new ThreadLocal<>();

  private HeaderClassifierContext() {}

  public static void set(File modelFile) {
    MODEL_FILE.set(modelFile);
  }

  public static File get() {
    return MODEL_FILE.get();
  }
}
