package io.github.christianlinzatti.rundisabled.agent;

import java.util.Map;

/**
 * Ends the test JVM if it hangs during shutdown.
 * <p>
 * Eclipse's JUnit runner always calls {@code System.exit(0)} when the test run is finished. If a
 * shutdown hook (e.g. of a Quarkus application or dev services started for a former
 * {@code @Disabled} test class) or a non-terminating thread blocks the exit, the launch stays
 * alive in Eclipse and has to be terminated manually.
 * <p>
 * This shutdown hook only starts a daemon thread and returns immediately, so it never delays a
 * normal exit. If the JVM is still alive after the timeout, the daemon thread prints the remaining
 * threads (for diagnosis) and halts the JVM.
 * <p>
 * Timeout in seconds: system property {@value #TIMEOUT_PROPERTY} (default
 * {@value #DEFAULT_TIMEOUT_SECONDS}, {@code 0} disables the watchdog).
 */
public final class ExitWatchdog implements Runnable {

	public static final String TIMEOUT_PROPERTY = "rundisabled.exitTimeout";
	public static final int DEFAULT_TIMEOUT_SECONDS = 15;

	private final long timeoutMillis;
	private final boolean waiting;

	private ExitWatchdog(long timeoutMillis, boolean waiting) {
		this.timeoutMillis = timeoutMillis;
		this.waiting = waiting;
	}

	static void install() {
		long seconds;
		try {
			seconds = Long.parseLong(System.getProperty(TIMEOUT_PROPERTY, String.valueOf(DEFAULT_TIMEOUT_SECONDS)).trim());
		} catch (NumberFormatException e) {
			seconds = DEFAULT_TIMEOUT_SECONDS;
		}
		if (seconds <= 0) {
			return;
		}
		Runtime.getRuntime().addShutdownHook(
				new Thread(new ExitWatchdog(seconds * 1000L, false), "Eclipse-Run-Disabled exit hook"));
	}

	@Override
	public void run() {
		if (!waiting) {
			// shutdown hook: start the timer and return immediately
			Thread timer = new Thread(new ExitWatchdog(timeoutMillis, true), "Eclipse-Run-Disabled exit watchdog");
			timer.setDaemon(true);
			timer.start();
			return;
		}
		try {
			Thread.sleep(timeoutMillis);
		} catch (InterruptedException e) {
			return;
		}
		StringBuilder dump = new StringBuilder();
		dump.append("[Eclipse-Run-Disabled] Test-JVM endet nach ").append(timeoutMillis / 1000)
				.append(" s nicht (blockierter Shutdown-Hook/Thread) - wird beendet. Threads:\n");
		for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
			Thread thread = entry.getKey();
			if (thread == Thread.currentThread()) {
				continue;
			}
			dump.append('"').append(thread.getName()).append('"').append(thread.isDaemon() ? " daemon" : "")
					.append(' ').append(thread.getState()).append('\n');
			for (StackTraceElement frame : entry.getValue()) {
				dump.append("    at ").append(frame).append('\n');
			}
		}
		System.err.print(dump);
		System.err.flush();
		Runtime.getRuntime().halt(0);
	}
}
