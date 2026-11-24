import java.net.URL;
import java.nio.file.Path;

import io.quarkus.test.junit.classloading.FacadeClassLoader;
import io.quarkus.test.junit.launcher.CustomLauncherInterceptor;

/**
 * Usage: java [-javaagent:agent.jar] SelfTest <testclasses-dir> <expectAgent:true|false>
 */
public class SelfTest {
	public static void main(String[] args) throws Exception {
		URL[] urls = { Path.of(args[0]).toUri().toURL() };
		boolean expectAgent = Boolean.parseBoolean(args[1]);

		String deactivate = System.getProperty("junit.jupiter.conditions.deactivate");
		check("conditions.deactivate", expectAgent ? "org.junit.*DisabledCondition" : null, deactivate);

		// first launcher session
		FacadeClassLoader facade = CustomLauncherInterceptor.launcherSessionOpened(urls);
		check("DisabledQuarkusTest is QuarkusTest", expectAgent, facade.wouldBeLoadedAsQuarkusTest("com.example.DisabledQuarkusTest"));

		// Quarkus re-creates the facade (e.g. second session / rerun) -> must be patched again
		FacadeClassLoader facade2 = CustomLauncherInterceptor.launcherSessionOpened(urls);
		check("OtherDisabledQuarkusTest is QuarkusTest (new facade)", expectAgent, facade2.wouldBeLoadedAsQuarkusTest("com.example.OtherDisabledQuarkusTest"));
		System.out.println("SELFTEST OK (agent=" + expectAgent + ")");
	}

	private static void check(String what, Object expected, Object actual) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(what + ": expected " + expected + " but was " + actual);
		}
		System.out.println("  ok: " + what + " = " + actual);
	}
}
