package org.tzi.use.runtime.impl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.tzi.use.main.runtime.IExtensionPoint;
import org.tzi.use.runtime.IPluginDescriptor;
import org.tzi.use.runtime.IPluginRuntime;
import org.tzi.use.runtime.gui.impl.ActionExtensionPoint;
import org.tzi.use.runtime.gui.impl.MModelExtensionPoint;
import org.tzi.use.runtime.model.PluginServiceModel;
import org.tzi.use.runtime.service.IPluginService;
import org.tzi.use.runtime.service.IPluginServiceDescriptor;
import org.tzi.use.runtime.service.impl.PluginServiceDescriptor;
import org.tzi.use.runtime.shell.impl.ShellExtensionPoint;
import org.tzi.use.runtime.util.PluginRegistry;
import org.tzi.use.runtime.util.ServiceRegistry;
import org.tzi.use.util.Log;

/**
 * @author Roman Asendorf
 */
public class PluginRuntime implements IPluginRuntime {

	private static IPluginRuntime instance = new PluginRuntime();

	/**
	 * Method returning the Singleton instance of the PluginRuntime
	 * 
	 * @return The PluginRuntime instance.
	 */
	public static IPluginRuntime getInstance() {
		return instance;
	}

	private Map<String, IPluginDescriptor> registeredPlugins;

	private Map<String, IPluginServiceDescriptor> registeredServices;

	public IExtensionPoint getExtensionPoint(String extensionPoint) {
		if (extensionPoint.equals("action")) {
			return ActionExtensionPoint.getInstance();
		} else if (extensionPoint.equals("shell")) {
			return ShellExtensionPoint.getInstance();
		} else if (extensionPoint.equals("model")) {
			return MModelExtensionPoint.getInstance();
		} else
			return null;
	}

	/**
	 * Method to get a Plugin by it's name.
	 * 
	 * @param pluginName
	 *            The Plugin's name.
	 * @return The plugin descriptor
	 */
	public IPluginDescriptor getPlugin(String pluginName) {
		return this.registeredPlugins.get(pluginName);
	}

	public Map<String, IPluginDescriptor> getPlugins() {
		if (this.registeredPlugins == null) {
			this.registeredPlugins = new HashMap<String, IPluginDescriptor>();
		}
		return this.registeredPlugins;
	}

	public IPluginService getService(String pluginServiceClassName) {
		PluginServiceDescriptor currentPluginServiceDescriptor = (PluginServiceDescriptor) this.registeredServices
				.get(pluginServiceClassName);
		if (currentPluginServiceDescriptor.getPluginServiceModel()
				.getServiceClass().equals(pluginServiceClassName)) {
			Log.debug("Service [" + pluginServiceClassName
					+ "] found and loading.");
			return currentPluginServiceDescriptor.getServiceClass();
		}
		return null;
	}

	public Map<String, IPluginServiceDescriptor> getServices() {
		if (this.registeredServices == null) {
			this.registeredServices = new HashMap<String, IPluginServiceDescriptor>();
		}
		return this.registeredServices;
	}

	public void registerPlugin(String pluginFilename, URL pluginURL) {
		URL newPluginURL;
		Log.debug("Current plugin Information [" + pluginFilename + ","
				+ pluginURL + "]");
		try {
			newPluginURL = new URL(pluginURL + pluginFilename);
			Log.debug("Current pluginURL [" + newPluginURL + "]");
			PluginRegistry pluginRegistry = PluginRegistry.getInstance();
			IPluginDescriptor currentPluginDescriptor = pluginRegistry
					.registerPlugin(newPluginURL);
			if (currentPluginDescriptor == null) {
				Log.error("Got no Plugin Descriptor !");
			} else {
				Log.debug("Registering plugin ["
						+ currentPluginDescriptor.getPluginModel().getName()
						+ "]");

				getPlugins().put(
						currentPluginDescriptor.getPluginModel().getName(),
						currentPluginDescriptor);
			}
		} catch (MalformedURLException mfurle) {
			Log.error("No valid URL given to register plugin: " + mfurle);
		}
	}

	private void registerService(
			IPluginServiceDescriptor currentPluginServiceDescriptor) {
		getServices().put(
				currentPluginServiceDescriptor.getPluginServiceModel().getId(),
				currentPluginServiceDescriptor);
	}

	public void registerServices(IPluginDescriptor currentPluginDescriptor) {

		ServiceRegistry serviceRegistry = ServiceRegistry.getInstance();

		Vector<PluginServiceModel> pluginServices = currentPluginDescriptor.getPluginModel().getServices();

		for (int cntPluginServices = 0; cntPluginServices < pluginServices.size();) {
			PluginServiceModel currentPluginServiceModel = pluginServices.get(cntPluginServices);
			IPluginServiceDescriptor currentPluginServiceDescriptor = serviceRegistry
					.registerPluginService(currentPluginDescriptor,
							currentPluginServiceModel);

			registerService(currentPluginServiceDescriptor);
			cntPluginServices++;
		}
	}

}
