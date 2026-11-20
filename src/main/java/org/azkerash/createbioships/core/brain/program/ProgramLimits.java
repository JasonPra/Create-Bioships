package org.azkerash.createbioships.core.brain.program;

/** Shared hard limits for the private C# compiler and cooperative machine. */
public final class ProgramLimits {
  public static final int DIAGNOSTIC_CHARS = 512;
  public static final int FINGERPRINT_HEX_CHARS = 64;
  public static final int WARNINGS = 64;

  public static final int SOURCE = 8192;
  public static final int TOKENS = 2048;
  public static final int INSTRUCTIONS = 4096;
  public static final int LOCALS = 128;
  public static final int STACK = 128;
  public static final int NESTING = 64;
  public static final int STEPS_PER_UPDATE = 1024;
  public static final int STRING = 4096;
  public static final int OUTPUT_LINES = 64;
  public static final int OUTPUT_LINE = 256;
  public static final int MAX_WAIT = 72000;

  private ProgramLimits() {}
}
