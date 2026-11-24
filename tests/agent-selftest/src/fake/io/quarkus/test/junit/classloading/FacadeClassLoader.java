package io.quarkus.test.junit.classloading;

import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Minimal stand-in for Quarkus' FacadeClassLoader (3.22 - 3.27+). Same field names and the same
 * decision logic: a test class annotated with @Disabled is NOT treated as a QuarkusTest.
 */
public final class FacadeClassLoader extends ClassLoader {
	private final URLClassLoader peekingClassLoader;
	private Class<? extends Annotation> disabledAnnotation;

	@SuppressWarnings("unchecked")
	public FacadeClassLoader(ClassLoader parent, URL[] testClassesUrls) throws ClassNotFoundException {
		super(parent);
		peekingClassLoader = new URLClassLoader(testClassesUrls, parent);
		disabledAnnotation = (Class<? extends Annotation>) peekingClassLoader.loadClass("org.junit.jupiter.api.Disabled");
	}

	/** Mirrors FacadeClassLoader.loadClass: load an inspection copy, then check for @Disabled. */
	public boolean wouldBeLoadedAsQuarkusTest(String name) throws ClassNotFoundException {
		Class<?> inspectionClass = peekingClassLoader.loadClass(name);
		boolean isDisabled = inspectionClass.isAnnotationPresent(disabledAnnotation);
		return !isDisabled;
	}
}
