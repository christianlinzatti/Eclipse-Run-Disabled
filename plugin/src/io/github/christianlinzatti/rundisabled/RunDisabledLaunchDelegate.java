package io.github.christianlinzatti.rundisabled;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.junit.launcher.AdvancedJUnitLaunchConfigurationDelegate;

/**
 * The normal Eclipse JUnit launch delegate, plus the "run @Disabled" Java agent.
 */
public class RunDisabledLaunchDelegate extends AdvancedJUnitLaunchConfigurationDelegate {

	@Override
	protected void collectExecutionArguments(ILaunchConfiguration configuration, List<String> vmArguments,
			List<String> programArguments) throws CoreException {
		super.collectExecutionArguments(configuration, vmArguments, programArguments);
		String agent = AgentJar.vmArgument();
		if (!vmArguments.contains(agent)) {
			vmArguments.add(agent);
		}
	}
}
