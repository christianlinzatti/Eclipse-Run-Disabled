package io.github.christianlinzatti.rundisabled;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.junit.launcher.JUnitLaunchShortcut;

/**
 * "Run As / Debug As &gt; JUnit Test (inkl. @Disabled)".
 * <p>
 * Creates launch configurations of the type {@value #LAUNCH_CONFIG_TYPE}. Because it is a separate
 * type, the normal "JUnit Test" shortcut never reuses these configurations and vice versa.
 */
public class RunDisabledLaunchShortcut extends JUnitLaunchShortcut {

	public static final String LAUNCH_CONFIG_TYPE = "io.github.christianlinzatti.rundisabled.launchConfigType";

	static final String NAME_SUFFIX = " (inkl. Disabled)";

	@Override
	protected String getLaunchConfigurationTypeId() {
		return LAUNCH_CONFIG_TYPE;
	}

	@Override
	protected String suggestLaunchConfigurationName(IJavaElement element, String fullTestName) {
		return super.suggestLaunchConfigurationName(element, fullTestName) + NAME_SUFFIX;
	}
}
