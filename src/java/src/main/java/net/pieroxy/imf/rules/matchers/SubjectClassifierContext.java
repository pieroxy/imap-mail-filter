package net.pieroxy.imf.rules.matchers;

import java.io.File;

/**
 * Lets {@link net.pieroxy.imf.rules.matchers.implementations.SubjectClassifierMatcher} know the
 * model file for the account it's running for. A matcher is built by
 * {@code MatcherType.getImplementation()} with no context at all (just an empty constructor),
 * so there's no other way to tell it "which account" it belongs to.
 * <p>
 * A plain ThreadLocal works here because each account runs on its own dedicated thread for the
 * whole lifetime of the process (see {@code MailAccount}): this isn't ephemeral request-scoped
 * context, it's a genuine 1:1 thread/account mapping, set once by {@code MailAccount.run()}
 * before any processing happens on that thread.
 */
public final class SubjectClassifierContext {
  private static final ThreadLocal<File> MODEL_FILE = new ThreadLocal<>();

  private SubjectClassifierContext() {}

  public static void set(File modelFile) {
    MODEL_FILE.set(modelFile);
  }

  public static File get() {
    return MODEL_FILE.get();
  }
}
