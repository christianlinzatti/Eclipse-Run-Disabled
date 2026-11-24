/**
 * Usage: java -javaagent:agent.jar ExitTest hang|normal
 * "hang": a shutdown hook blocks forever (like a Quarkus app/dev service that does not stop).
 */
public class ExitTest {
	public static void main(String[] args) throws Exception {
		if ("hang".equals(args[0])) {
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					Thread.sleep(Long.MAX_VALUE);
				} catch (InterruptedException e) {
					// ignore
				}
			}, "blocking-shutdown-hook"));
		}
		System.exit(0); // like Eclipse's RemoteTestRunner at the end of a test run
	}
}
