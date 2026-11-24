package io.quarkus.test.junit.launcher;

import java.net.URL;

import io.quarkus.test.junit.classloading.FacadeClassLoader;

/** Minimal stand-in for Quarkus' CustomLauncherInterceptor (same static field). */
public class CustomLauncherInterceptor {
	private static FacadeClassLoader facadeLoader = null;

	public static FacadeClassLoader launcherSessionOpened(URL[] testClassesUrls) throws ClassNotFoundException {
		facadeLoader = new FacadeClassLoader(Thread.currentThread().getContextClassLoader(), testClassesUrls);
		return facadeLoader;
	}
}
