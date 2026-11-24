package io.github.christianlinzatti.rundisabled;

import java.lang.reflect.Method;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.junit.model.ITestCaseElement;
import org.eclipse.jdt.junit.model.ITestElement;
import org.eclipse.jdt.junit.model.ITestRunSession;
import org.eclipse.jdt.junit.model.ITestSuiteElement;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Context menu entries "Run / Debug (inkl. @Disabled)" in the JUnit view.
 * <p>
 * Works like the built-in "Run"/"Debug" entries of the JUnit view: the launch configuration of the
 * test run is copied (VM arguments, environment, class path, ...), restricted to the selected test
 * and launched with the "JUnit (inkl. @Disabled)" launch configuration type. If the original launch
 * configuration is not available (e.g. imported test run), the selected class or method is launched
 * like "Run As &gt; JUnit Test (inkl. @Disabled)".
 */
public abstract class JUnitViewLaunchHandler extends AbstractHandler {

	// attribute names of org.eclipse.jdt.internal.junit.launcher.JUnitLaunchConfigurationConstants
	private static final String JUNIT_LAUNCH_CONFIG_TYPE = "org.eclipse.jdt.junit.launchconfig";
	private static final String ATTR_TEST_NAME = "org.eclipse.jdt.junit.TESTNAME";
	private static final String ATTR_TEST_CONTAINER = "org.eclipse.jdt.junit.CONTAINER";
	private static final String ATTR_TEST_UNIQUE_ID = "org.eclipse.jdt.junit.TEST_UNIQUE_ID";
	private static final String ATTR_FAILURES_NAMES = "org.eclipse.jdt.junit.FAILURENAMES";
	private static final String ATTR_KEEPRUNNING = "org.eclipse.jdt.junit.KEEPRUNNING_ATTR";

	private final String mode;

	protected JUnitViewLaunchHandler(String mode) {
		this.mode = mode;
	}

	/** "Run (inkl. @Disabled)" */
	public static class Run extends JUnitViewLaunchHandler {
		public Run() {
			super(ILaunchManager.RUN_MODE);
		}
	}

	/** "Debug (inkl. @Disabled)" */
	public static class Debug extends JUnitViewLaunchHandler {
		public Debug() {
			super(ILaunchManager.DEBUG_MODE);
		}
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ISelection selection = HandlerUtil.getActiveMenuSelection(event);
		if (selection == null || selection.isEmpty()) {
			selection = HandlerUtil.getCurrentSelection(event);
		}
		if (!(selection instanceof IStructuredSelection)
				|| !(((IStructuredSelection) selection).getFirstElement() instanceof ITestElement)) {
			return null;
		}
		ITestElement element = (ITestElement) ((IStructuredSelection) selection).getFirstElement();
		try {
			Target target = Target.of(element);
			if (target == null) {
				MessageDialog.openInformation(HandlerUtil.getActiveShell(event), "JUnit (inkl. @Disabled)",
						"Für dieses Element kann keine Testklasse bestimmt werden.");
				return null;
			}
			ILaunchConfiguration base = originalConfiguration(element.getTestRunSession());
			if (base != null) {
				DebugUITools.launch(createConfiguration(base, target), mode);
			} else {
				launchWithShortcut(element.getTestRunSession(), target);
			}
		} catch (CoreException e) {
			throw new ExecutionException("Start inkl. @Disabled fehlgeschlagen: " + e.getMessage(), e);
		}
		return null;
	}

	private ILaunchConfigurationWorkingCopy createConfiguration(ILaunchConfiguration base, Target target)
			throws CoreException {
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType type = manager.getLaunchConfigurationType(RunDisabledLaunchShortcut.LAUNCH_CONFIG_TYPE);
		String name = manager.generateLaunchConfigurationName(
				target.displayName.replace('@', ' ').trim() + RunDisabledLaunchShortcut.NAME_SUFFIX);
		ILaunchConfigurationWorkingCopy wc = type.newInstance(null, name);
		Map<String, Object> attributes = base.getAttributes();
		wc.setAttributes(attributes);
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, target.className);
		wc.setAttribute(ATTR_TEST_CONTAINER, "");
		wc.setAttribute(ATTR_TEST_NAME, target.testName);
		wc.setAttribute(ATTR_TEST_UNIQUE_ID, target.uniqueId);
		wc.setAttribute(ATTR_FAILURES_NAMES, (String) null);
		wc.setAttribute(ATTR_KEEPRUNNING, false);
		// not saved: a temporary configuration, like the JUnit view's own "Run"/"Debug"
		return wc;
	}

	private void launchWithShortcut(ITestRunSession session, Target target) throws CoreException {
		IJavaProject project = session.getLaunchedProject();
		IJavaElement element = null;
		if (project != null) {
			IType type = project.findType(target.className.replace('$', '.'));
			if (type != null) {
				element = type;
				if (target.methodName != null) {
					for (IMethod method : type.getMethods()) {
						if (method.getElementName().equals(target.methodName)) {
							element = method;
							break;
						}
					}
				}
			}
		}
		if (element == null) {
			throw new CoreException(Status.error(
					"Testklasse " + target.className + " wurde im Projekt nicht gefunden."));
		}
		new RunDisabledLaunchShortcut().launch(new StructuredSelection(element), mode);
	}

	/**
	 * @return the launch configuration of the test run, if it is a (normal or "inkl. @Disabled")
	 *         JUnit launch configuration
	 */
	private static ILaunchConfiguration originalConfiguration(ITestRunSession session) {
		Object config = invoke(session, "getRerunLaunchConfiguration");
		if (!(config instanceof ILaunchConfiguration)) {
			Object launch = invoke(session, "getLaunch");
			config = launch instanceof ILaunch ? ((ILaunch) launch).getLaunchConfiguration() : null;
		}
		if (!(config instanceof ILaunchConfiguration)) {
			return null;
		}
		ILaunchConfiguration configuration = (ILaunchConfiguration) config;
		try {
			if (!configuration.exists() && !configuration.isWorkingCopy()) {
				return null;
			}
			String typeId = configuration.getType().getIdentifier();
			if (JUNIT_LAUNCH_CONFIG_TYPE.equals(typeId) || RunDisabledLaunchShortcut.LAUNCH_CONFIG_TYPE.equals(typeId)) {
				return configuration;
			}
		} catch (CoreException e) {
			// fall back to the launch shortcut
		}
		return null;
	}

	/** Calls a public no-arg method of the (internal) JUnit model, or returns null. */
	static Object invoke(Object target, String methodName) {
		if (target == null) {
			return null;
		}
		try {
			Method method = target.getClass().getMethod(methodName);
			return method.invoke(target);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}

	/** What to launch, derived like the JUnit view's own "Run" action. */
	static final class Target {
		String className;
		String methodName;
		String testName;
		String uniqueId;
		String displayName;

		static Target of(ITestElement element) {
			Target t = new Target();
			Object uniqueId = invoke(element, "getUniqueId");
			t.uniqueId = uniqueId instanceof String ? (String) uniqueId : null;
			Object displayName = invoke(element, "getDisplayName");

			if (element instanceof ITestCaseElement) {
				ITestCaseElement testCase = (ITestCaseElement) element;
				t.className = testCase.getTestClassName();
				t.methodName = testCase.getTestMethodName();
				t.testName = t.methodName + parameterTypes(element);
				t.displayName = displayName instanceof String ? (String) displayName : t.className + "." + t.methodName;
			} else if (element instanceof ITestSuiteElement) {
				t.className = ((ITestSuiteElement) element).getSuiteTypeName();
				Object testName = invoke(element, "getTestName");
				// method level container, e.g. @ParameterizedTest or @TestFactory: "method(params)"
				if (testName instanceof String && !testName.equals(t.className)) {
					int index = ((String) testName).indexOf('(');
					if (index > 0) {
						t.methodName = ((String) testName).substring(0, index);
						t.testName = t.methodName + parameterTypes(element);
					}
				}
				t.displayName = displayName instanceof String ? (String) displayName : t.className;
			} else {
				return null;
			}
			if (t.className == null || t.className.isEmpty()) {
				return null;
			}
			return t;
		}

		private static String parameterTypes(ITestElement element) {
			Object types = invoke(element, "getParameterTypes");
			if (types instanceof String[]) {
				return "(" + String.join(",", (String[]) types) + ")";
			}
			return "";
		}
	}
}
