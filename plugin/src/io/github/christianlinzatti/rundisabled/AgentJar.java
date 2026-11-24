package io.github.christianlinzatti.rundisabled;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import io.github.christianlinzatti.rundisabled.agent.NeverPresent;
import io.github.christianlinzatti.rundisabled.agent.QuarkusFacadePatcher;
import io.github.christianlinzatti.rundisabled.agent.RunDisabledAgent;

/**
 * Provides the Java agent jar that is passed to the test JVM with {@code -javaagent:...}.
 * <p>
 * The jar is assembled from the agent class files contained in this bundle and written to the
 * bundle's state location. That works no matter whether the plugin is installed as a jar or as a
 * directory, and the path is always valid for the installed plugin version.
 */
public final class AgentJar {

	public static final String PLUGIN_ID = "io.github.christianlinzatti.rundisabled";

	/** All classes of the agent. The agent must not use nested or anonymous classes. */
	private static final Class<?>[] AGENT_CLASSES = { RunDisabledAgent.class, QuarkusFacadePatcher.class,
			NeverPresent.class };

	private static Path cached;

	private AgentJar() {
	}

	/**
	 * @return the absolute path of the agent jar, created on first use
	 */
	public static synchronized Path get() throws CoreException {
		if (cached != null && Files.isRegularFile(cached)) {
			return cached;
		}
		Bundle bundle = FrameworkUtil.getBundle(AgentJar.class);
		String version = bundle != null ? bundle.getVersion().toString() : "dev";
		try {
			Path dir = bundle != null ? Platform.getStateLocation(bundle).toFile().toPath()
					: Path.of(System.getProperty("java.io.tmpdir"), PLUGIN_ID);
			Files.createDirectories(dir);
			Path jar = dir.resolve("run-disabled-agent-" + version + ".jar");
			// Always rewrite qualifier-less "dev" builds; released versions are immutable.
			if (!Files.isRegularFile(jar) || version.endsWith("qualifier") || "dev".equals(version)) {
				write(jar);
			}
			cached = jar.toAbsolutePath();
			return cached;
		} catch (IOException | RuntimeException e) {
			throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID,
					"Der Java-Agent für @Disabled-Tests konnte nicht erstellt werden: " + e.getMessage(), e));
		}
	}

	/**
	 * @return the {@code -javaagent:...} VM argument
	 */
	public static String vmArgument() throws CoreException {
		return "-javaagent:" + get();
	}

	private static void write(Path jar) throws IOException {
		Manifest manifest = new Manifest();
		Attributes main = manifest.getMainAttributes();
		main.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		main.putValue("Premain-Class", RunDisabledAgent.class.getName());
		main.putValue("Agent-Class", RunDisabledAgent.class.getName());
		main.putValue("Implementation-Title", "Eclipse-Run-Disabled agent");

		Path tmp = Files.createTempFile(jar.getParent(), "agent", ".tmp");
		try {
			try (OutputStream out = Files.newOutputStream(tmp); JarOutputStream jarOut = new JarOutputStream(out, manifest)) {
				for (Class<?> type : AGENT_CLASSES) {
					String entry = type.getName().replace('.', '/') + ".class";
					try (InputStream in = AgentJar.class.getClassLoader().getResourceAsStream(entry)) {
						if (in == null) {
							throw new IOException("Klassendatei nicht gefunden: " + entry);
						}
						jarOut.putNextEntry(new JarEntry(entry));
						in.transferTo(jarOut);
						jarOut.closeEntry();
					}
				}
			}
			try {
				Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				// e.g. Windows: jar is in use by a running test JVM - keep using the existing one
				if (!Files.isRegularFile(jar)) {
					throw e;
				}
			}
		} finally {
			Files.deleteIfExists(tmp);
		}
	}
}
