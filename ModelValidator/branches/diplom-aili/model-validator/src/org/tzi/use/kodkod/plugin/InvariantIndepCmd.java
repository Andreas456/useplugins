package org.tzi.use.kodkod.plugin;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.commons.configuration.ConfigurationException;
import org.tzi.kodkod.InvariantIndepChecker;
import org.tzi.kodkod.helper.LogMessages;
import org.tzi.use.main.shell.Shell;
import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.runtime.shell.IPluginShellCmdDelegate;

/**
 * Cmd-Class for the invariant independence check.
 * 
 * @author Hendrik Reitmann
 * @author Frank Hilken
 */
public class InvariantIndepCmd extends ConfigurablePlugin implements IPluginShellCmdDelegate {
	//TODO adapt command to allow configuration section selection

	@Override
	public void performCommand(IPluginShellCmd pluginCommand) {
		if(!pluginCommand.getSession().hasSystem()){
			LOG.error("No model loaded.");
			return;
		}
		
		initialize(pluginCommand.getSession());
		enrichModel();

		InvariantIndepChecker indepChecker = new InvariantIndepChecker(session);

		String[] arguments = pluginCommand.getCmdArgumentList();

		// kodkod -invIndep <propertyFile> (all|<className>::<invName>)
		if(arguments.length != 2){
			LOG.error("Invalid parameters.");
			LOG.error("Syntax of command is: " + pluginCommand.getCmd() + " <propertyFile> (all|<className>::<invName>)");
			return;
		}
		
		String filenameToOpen = Shell.getInstance().getFilenameToOpen(arguments[0], false);
		try {
			StringWriter errorBuffer = new StringWriter();
			configureModel(new File(filenameToOpen), new PrintWriter(errorBuffer, true));
			LOG.warn(errorBuffer.toString());
		} catch (ConfigurationException e) {
			LOG.error(LogMessages.propertiesConfigurationReadError + ". " + (e.getMessage() != null ? e.getMessage() : ""));
			return;
		}
		
		if(arguments[1].equalsIgnoreCase("all")){
			indepChecker.validate(model());
		}
		else {
			String[] split = arguments[1].split("-|:{2}");
			if (split.length != 2) {
				LOG.error(LogMessages.invIndepSyntaxError(arguments[1]));
			} else {
				indepChecker.validate(model(), split[0], split[1]);
			}
		}
	}
		
}
