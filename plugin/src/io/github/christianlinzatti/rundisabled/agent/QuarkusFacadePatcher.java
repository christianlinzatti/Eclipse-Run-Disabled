package io.github.christianlinzatti.rundisabled.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;

/**
 * Makes Quarkus' {@code FacadeClassLoader} ignore {@code @Disabled} on test classes.
 * <p>
 * The transformer never changes any bytecode (it always returns {@code null}). It is only used as a
 * hook that is called whenever a class is about to be defined. Quarkus' {@code FacadeClassLoader}
 * loads every test class into an "inspection" class loader <em>before</em> it checks for
 * {@code @Disabled}, so this hook runs right before that check. At that moment the current
 * {@code FacadeClassLoader} instance is available in the static field
 * {@code CustomLauncherInterceptor.facadeLoader}, and its field {@code disabledAnnotation} is
 * replaced by {@link NeverPresent}.
 * <p>
 * If Quarkus is not on the class path (or uses another internal structure) nothing happens.
 */
public final class QuarkusFacadePatcher implements ClassFileTransformer {

	static final String INTERCEPTOR_CLASS = "io.quarkus.test.junit.launcher.CustomLauncherInterceptor";
	static final String INTERCEPTOR_INTERNAL_NAME = INTERCEPTOR_CLASS.replace('.', '/');
	static final String FACADE_FIELD = "facadeLoader";
	static final String DISABLED_ANNOTATION_FIELD = "disabledAnnotation";

	private final ThreadLocal<Boolean> busy = new ThreadLocal<>();

	private volatile ClassLoader interceptorLoader;
	private volatile Field facadeField;
	private volatile boolean givenUp;
	private volatile WeakReference<Object> lastPatched = new WeakReference<>(null);

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, byte[] classfileBuffer) {
		if (givenUp || className == null || busy.get() != null) {
			return null;
		}
		busy.set(Boolean.TRUE);
		try {
			if (interceptorLoader == null) {
				if (INTERCEPTOR_INTERNAL_NAME.equals(className)) {
					// The class is being defined right now; it can be resolved on a later event.
					interceptorLoader = loader != null ? loader : ClassLoader.getSystemClassLoader();
				}
				return null;
			}
			if (loader == null || isInfrastructureClass(className)) {
				// Reflection below resolves Quarkus/JDK field types. Doing that while one of these
				// classes is being defined would define it twice (LinkageError). Test classes never
				// live in these packages, and the check we have to precede happens for test classes.
				return null;
			}
			patchCurrentFacade();
		} catch (Throwable t) {
			givenUp = true;
			RunDisabledAgent.log("Quarkus FacadeClassLoader konnte nicht angepasst werden: " + t);
		} finally {
			busy.remove();
		}
		return null;
	}

	/**
	 * Patches the current {@code FacadeClassLoader}, if there is a new one.
	 *
	 * @return {@code true} if a facade class loader was patched by this call
	 */
	boolean patchCurrentFacade() throws ReflectiveOperationException {
		Field field = facadeField;
		if (field == null) {
			Class<?> interceptor;
			try {
				interceptor = Class.forName(INTERCEPTOR_CLASS, false, interceptorLoader);
			} catch (ClassNotFoundException | LinkageError e) {
				// not yet completely defined - try again on the next event
				return false;
			}
			field = interceptor.getDeclaredField(FACADE_FIELD);
			if (!Modifier.isStatic(field.getModifiers())) {
				throw new NoSuchFieldException("static " + FACADE_FIELD);
			}
			field.setAccessible(true);
			facadeField = field;
		}

		Object facade = field.get(null);
		if (facade == null || facade == lastPatched.get()) {
			return false;
		}
		Field disabled = findField(facade.getClass(), DISABLED_ANNOTATION_FIELD);
		disabled.setAccessible(true);
		disabled.set(facade, NeverPresent.class);
		lastPatched = new WeakReference<>(facade);
		RunDisabledAgent.log("Quarkus FacadeClassLoader angepasst: @Disabled-Testklassen werden mit dem QuarkusClassLoader geladen");
		return true;
	}

	static boolean isInfrastructureClass(String internalName) {
		return internalName.startsWith("java/") || internalName.startsWith("javax/")
				|| internalName.startsWith("jdk/") || internalName.startsWith("sun/")
				|| internalName.startsWith("com/sun/") || internalName.startsWith("io/quarkus/")
				|| internalName.startsWith("org/jboss/") || internalName.startsWith("org/junit/")
				|| internalName.startsWith("io/github/christianlinzatti/rundisabled/");
	}

	private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			try {
				return c.getDeclaredField(name);
			} catch (NoSuchFieldException e) {
				// continue with super class
			}
		}
		throw new NoSuchFieldException(type.getName() + "." + name);
	}
}
