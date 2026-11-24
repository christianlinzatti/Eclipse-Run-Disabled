package io.github.christianlinzatti.rundisabled.agent;

import java.lang.instrument.Instrumentation;

/**
 * Java agent that is attached to the test JVM by the "JUnit Test (inkl. Disabled)" launch.
 * <p>
 * It does two things:
 * <ol>
 * <li>It deactivates JUnit Jupiter's {@code DisabledCondition} through the documented
 * {@code junit.jupiter.conditions.deactivate} configuration parameter, so that {@code @Disabled}
 * classes and methods are executed.</li>
 * <li>It registers a {@link QuarkusFacadePatcher}. Since Quarkus 3.22 the Quarkus test framework
 * ({@code FacadeClassLoader}) checks for {@code @Disabled} on test classes itself and does
 * <em>not</em> load such classes with a {@code QuarkusClassLoader}. If JUnit then runs the class
 * anyway, {@code QuarkusTestExtension} fails with "... should have been loaded with a
 * QuarkusClassLoader, but instead it was loaded with AppClassLoader". The patcher makes Quarkus
 * ignore {@code @Disabled} as well.</li>
 * </ol>
 * <p>
 * This class must only depend on {@code java.base} and {@code java.instrument}, because the agent
 * jar is assembled from the plugin's class files and runs inside the test JVM.
 */
public final class RunDisabledAgent {

	public static final String DEACTIVATE_PROPERTY = "junit.jupiter.conditions.deactivate";
	public static final String DISABLED_CONDITION_PATTERN = "org.junit.*DisabledCondition";
	private RunDisabledAgent() {
	}

	public static void premain(String args, Instrumentation instrumentation) {
		install(instrumentation);
	}

	public static void agentmain(String args, Instrumentation instrumentation) {
		install(instrumentation);
	}

	static void install(Instrumentation instrumentation) {
		deactivateDisabledCondition();
		instrumentation.addTransformer(new QuarkusFacadePatcher(), false);
		ExitWatchdog.install();
		log("aktiv - @Disabled wird ignoriert (" + DEACTIVATE_PROPERTY + "="
				+ System.getProperty(DEACTIVATE_PROPERTY) + ")");
	}

	static void deactivateDisabledCondition() {
		String current = System.getProperty(DEACTIVATE_PROPERTY);
		if (current == null || current.isBlank()) {
			System.setProperty(DEACTIVATE_PROPERTY, DISABLED_CONDITION_PATTERN);
		} else if (!current.contains(DISABLED_CONDITION_PATTERN) && !current.trim().equals("*")) {
			System.setProperty(DEACTIVATE_PROPERTY, current + "," + DISABLED_CONDITION_PATTERN);
		}
	}

	static void log(String message) {
		System.err.println("[Eclipse-Run-Disabled] " + message);
	}
}
