package io.github.christianlinzatti.rundisabled;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.variables.IDynamicVariable;
import org.eclipse.core.variables.IDynamicVariableResolver;

/**
 * Resolves {@code ${run_disabled_agent}} to the path of the agent jar. This allows to use the agent
 * in any launch configuration, e.g. VM arguments {@code -javaagent:${run_disabled_agent}}.
 */
public class AgentVariableResolver implements IDynamicVariableResolver {

	@Override
	public String resolveValue(IDynamicVariable variable, String argument) throws CoreException {
		return AgentJar.get().toString();
	}
}
