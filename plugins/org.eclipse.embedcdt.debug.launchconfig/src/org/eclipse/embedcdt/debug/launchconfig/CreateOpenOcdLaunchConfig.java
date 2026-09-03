/*******************************************************************************
 * Copyright (c) 2024 OEM.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.embedcdt.debug.launchconfig;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.core.templateengine.TemplateCore;
import org.eclipse.cdt.core.templateengine.process.ProcessArgument;
import org.eclipse.cdt.core.templateengine.process.ProcessFailureException;
import org.eclipse.cdt.core.templateengine.process.ProcessRunner;
import org.eclipse.cdt.managedbuilder.core.IManagedBuildInfo;
import org.eclipse.cdt.managedbuilder.core.IManagedProject;
import org.eclipse.cdt.managedbuilder.core.ManagedBuilderManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;

/**
 * Process to automatically create an OpenOCD+GDB launch configuration
 * when a new RISC-V project is created.
 */
public class CreateOpenOcdLaunchConfig extends ProcessRunner {

    private static final String OPENOCD_LAUNCH_TYPE = "ilg.gnumcueclipse.debug.gdbjtag.openocd.launchConfigurationType";
    private static final String GDB_SERVER_OTHER = "ilg.gnumcueclipse.debug.gdbjtag.openocd.gdbServer.other";
    private static final String GDB_CLIENT_OTHER = "ilg.gnumcueclipse.debug.gdbjtag.openocd.gdbClient.other";
    private static final String FIRST_INSTRUCTIONS = "ilg.gnumcueclipse.debug.gdbjtag.openocd.firstInstructions";
    private static final String DO_FIRST_INSTRUCTIONS = "ilg.gnumcueclipse.debug.gdbjtag.openocd.doFirstInstructions";
    private static final String LOAD_IMAGE = "org.eclipse.cdt.launch.ATTR_LOAD_IMAGE";
    private static final String PROJECT_ATTR = "org.eclipse.cdt.launch.PROJECT_ATTR";

    @Override
    public void process(TemplateCore template, ProcessArgument[] args, String processId, IProgressMonitor monitor)
            throws ProcessFailureException {
        try {
            String projectName = args[0].getSimpleValue();
            if (projectName == null || projectName.trim().isEmpty()) {
                return;
            }

            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
            if (project == null || !project.exists()) {
                return;
            }

            ICProject cProject = CCorePlugin.getDefault().getCModel().findElement(project);
            if (cProject == null) {
                return;
            }

            // Check if this is a RISC-V project
            IManagedBuildInfo buildInfo = ManagedBuilderManager.getManagedBuildInfo(project);
            if (buildInfo == null) {
                return;
            }

            IManagedProject managedProject = buildInfo.getManagedProject();
            if (managedProject == null) {
                return;
            }

            String toolChainId = managedProject.getToolChain().getId();
            if (!toolChainId.contains("riscv")) {
                // Only create OpenOCD config for RISC-V projects
                return;
            }

            createOpenOcdLaunchConfiguration(project, cProject);
        } catch (CoreException e) {
            throw new ProcessFailureException("Failed to create OpenOCD launch configuration", e);
        }
    }

    private void createOpenOcdLaunchConfiguration(IProject project, ICProject cProject) throws CoreException {
        ILaunchConfigurationType launchType = DebugPlugin.getDefault().getLaunchManager()
                .getLaunchConfigurationType(OPENOCD_LAUNCH_TYPE);

        if (launchType == null) {
            // OpenOCD launch type not available, skip
            return;
        }

        String configName = project.getName() + " OpenOCD Debug";
        
        // Check if configuration already exists
        ILaunchConfiguration[] existingConfigs = DebugPlugin.getDefault().getLaunchManager()
                .getLaunchConfigurations(launchType);
        for (ILaunchConfiguration config : existingConfigs) {
            if (config.getName().equals(configName)) {
                return; // Already exists
            }
        }

        ILaunchConfigurationWorkingCopy workingCopy = launchType.newInstance(null, configName);
        
        // Set project attribute
        workingCopy.setAttribute(PROJECT_ATTR, project.getName());
        
        // Configure OpenOCD server options (default for RISC-V)
        workingCopy.setAttribute(GDB_SERVER_OTHER, "-f interface/cmsis-dap.cfg -f target/riscv.cfg");
        
        // Configure GDB client options
        workingCopy.setAttribute(GDB_CLIENT_OTHER, "");
        
        // Set first instructions for RISC-V debugging
        workingCopy.setAttribute(FIRST_INSTRUCTIONS, "set mem inaccessible-by-default off\nmonitor reset halt");
        workingCopy.setAttribute(DO_FIRST_INSTRUCTIONS, true);
        
        // Don't load image automatically (OpenOCD handles it)
        workingCopy.setAttribute(LOAD_IMAGE, false);

        workingCopy.doSave();
    }
}
