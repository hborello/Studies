package org.fog.placement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppEdge;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.utils.Logger;

public class ModulePlacementEdgewardsMOD extends ModulePlacement {

	protected ModuleMapping moduleMapping;
	protected List<Sensor> sensors;
	protected List<Actuator> actuators;
	protected Map<Integer, Double> currentCpuLoad;

	/**
	 * Stores the current mapping of application modules to fog devices
	 */
	protected Map<Integer, List<String>> currentModuleMap;
	protected Map<Integer, Map<String, Double>> currentModuleLoadMap;
	protected Map<Integer, Map<String, Integer>> currentModuleInstanceNum;

	public ModulePlacementEdgewardsMOD(List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators,
			Application application, ModuleMapping moduleMapping) {
		this.setFogDevices(fogDevices);
		this.setApplication(application);
		this.setModuleMapping(moduleMapping);
		this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
		this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
		setSensors(sensors);
		setActuators(actuators);
		setCurrentCpuLoad(new HashMap<Integer, Double>());
		setCurrentModuleMap(new HashMap<Integer, List<String>>());
		setCurrentModuleLoadMap(new HashMap<Integer, Map<String, Double>>());
		setCurrentModuleInstanceNum(new HashMap<Integer, Map<String, Integer>>());
		for (FogDevice dev : getFogDevices()) {
			getCurrentCpuLoad().put(dev.getId(), 0.0);
			getCurrentModuleLoadMap().put(dev.getId(), new HashMap<String, Double>());
			getCurrentModuleMap().put(dev.getId(), new ArrayList<String>());
			getCurrentModuleInstanceNum().put(dev.getId(), new HashMap<String, Integer>());
		}

		mapModules();
		setModuleInstanceCountMap(getCurrentModuleInstanceNum());

		System.out.println("\nModulePlacementEdgewards finished!!!\n");
	}

	@Override
	protected void mapModules() {

		for (String deviceName : getModuleMapping().getModuleMapping().keySet()) {
			for (String moduleName : getModuleMapping().getModuleMapping().get(deviceName)) {
				int deviceId = CloudSim.getEntityId(deviceName);
				getCurrentModuleMap().get(deviceId).add(moduleName); // guardando quais m??dulos j?? est??o colocados
				getCurrentModuleLoadMap().get(deviceId).put(moduleName, 0.0);
				getCurrentModuleInstanceNum().get(deviceId).put(moduleName, 0);
			}
		}

		List<List<Integer>> leafToRootPaths = getLeafToRootPaths(); // montando os caminhos poss??veis da ??rvore (folha
																	// para ra??z)
		for (List<Integer> path : leafToRootPaths) { // chamando o m??todo de coloca????o para cada caminho gerado
			placeModulesInPath(path); // artigo: linha 1 do pseudoc??digo do artigo
		}

		for (int deviceId : getCurrentModuleMap().keySet()) {
			for (String module : getCurrentModuleMap().get(deviceId)) {
				createModuleInstanceOnDevice(getApplication().getModuleByName(module), getFogDeviceById(deviceId));
			}
		}
	}

	/**
	 * Get the list of modules that are ready to be placed
	 * 
	 * @param placedModules Modules that have already been placed in current path
	 * @return list of modules ready to be placed
	 */
	private List<String> getModulesToPlace(List<String> placedModules) { // busca m??dulos predecessores com base no
																			// grafo direcionado especificado na
																			// montagem da aplica????o
		Application app = getApplication();
		List<String> modulesToPlace_1 = new ArrayList<String>();
		List<String> modulesToPlace = new ArrayList<String>();
		for (AppModule module : app.getModules()) { // artigo: linha 5
			if (!placedModules.contains(module.getName())) // artigo: linha 6
				modulesToPlace_1.add(module.getName()); // artigo: linha 7			
		}
		
//		System.out.println("placedModules:"+placedModules);
		
		/*
		 * Filtering based on whether modules (to be placed) lower in physical topology
		 * are already placed
		 */
		for (String moduleName : modulesToPlace_1) {
			boolean toBePlaced = true;

			for (AppEdge edge : app.getEdges()) {
				// CHECK IF OUTGOING DOWN EDGES ARE PLACED
//				if (edge.getSource().equals(moduleName) && edge.getDirection() == Tuple.DOWN
//						&& !placedModules.contains(edge.getDestination()))
//					toBePlaced = false;
//				// CHECK IF INCOMING UP EDGES ARE PLACED
//				if (edge.getDestination().equals(moduleName) && edge.getDirection() == Tuple.UP
//						&& !placedModules.contains(edge.getSource()))
//					toBePlaced = false;

				if ((edge.getSource().equals(moduleName) && edge.getDirection() == Tuple.DOWN
						&& !placedModules.contains(edge.getDestination()))
				|| (edge.getDestination().equals(moduleName) && edge.getDirection() == Tuple.UP
						&& !placedModules.contains(edge.getSource())))
					toBePlaced = false;

				if (!toBePlaced) {
//					System.out.println("["+edge.getSource()+"], ["+edge.getDestination()+"], ["+moduleName+"]");
					break;
				}

			}

			if (toBePlaced)
				modulesToPlace.add(moduleName);
		}
		
//		if (modulesToPlace.isEmpty() && !modulesToPlace_1.isEmpty() && !placedModules.contains(modulesToPlace_1.get(0)))
//			modulesToPlace.add(modulesToPlace_1.get(0));
//		System.out.println("modulesToPlace:"+modulesToPlace_1);
//		System.out.println("moduleToPlace:"+modulesToPlace);
		return modulesToPlace; // retorna o m??dulo caso seus predecessores j?? estejam colocados
	}

	protected double getRateOfSensor(String sensorType) { // calculo da taxa de transmiss??o do sensor com base no valor
															// sensingInterval
		for (Sensor sensor : getSensors()) {
			if (sensor.getTupleType().equals(sensorType)) {
				return 1 / sensor.getTransmitDistribution().getMeanInterTransmitTime();
			}
		}
		return 0;
	}

	private void placeModulesInPath(List<Integer> path) {

		if (path.size() == 0)
			return;
		
		System.out.println("path: "+path);
		List<String> placedModules = new ArrayList<String>(); // artigo: linha 2 do pseudoc??digo
		Map<AppEdge, Double> appEdgeToRate = new HashMap<AppEdge, Double>();

		/**
		 * Periodic edges have a fixed periodicity of tuples, so setting the tuple rate
		 * beforehand
		 */
		for (AppEdge edge : getApplication().getEdges()) { // calculando taxa de tupla caso haja periodicidade para
															// aresta
			if (edge.isPeriodic()) { // periodicidade definida na especifica????o da aplica????o
										// (createApplication/addAppEdge)
				appEdgeToRate.put(edge, 1 / edge.getPeriodicity());
			}
		}

		for (Integer deviceId : path) { // artigo: linha 3 do pseudoc??digo
			FogDevice device = getFogDeviceById(deviceId);
			Map<String, Integer> sensorsAssociated = getAssociatedSensors(device);
			Map<String, Integer> actuatorsAssociated = getAssociatedActuators(device);
			placedModules.addAll(sensorsAssociated.keySet()); // ADDING ALL SENSORS TO PLACED LIST
			placedModules.addAll(actuatorsAssociated.keySet()); // ADDING ALL ACTUATORS TO PLACED LIST
			
			System.out.println("getDevicesPerLevel: "+getDevicesPerLevel(device.getLevel()));
			/*
			 * Setting the rates of application edges emanating from sensors
			 */
			for (String sensor : sensorsAssociated.keySet()) { // calculando taxa de tupla com base no valor de
																// intervalo de sensor (sensorInterval)
				for (AppEdge edge : getApplication().getEdges()) {
//					System.out.println("edge: " + edge + ", sensorsAssociated * getRateOfSensor: " + sensorsAssociated.get(sensor)+" * "+getRateOfSensor(sensor));
					if (edge.getSource().equals(sensor)) {
						appEdgeToRate.put(edge, sensorsAssociated.get(sensor) * getRateOfSensor(sensor));
					}
				}
			}

			/*
			 * Updating the AppEdge rates for the entire application based on knowledge so
			 * far
			 */
			boolean changed = true;
			while (changed) { // Loop runs as long as some new information is added
				changed = false;
				Map<AppEdge, Double> rateMap = new HashMap<AppEdge, Double>(appEdgeToRate);
				for (AppEdge edge : rateMap.keySet()) {
					AppModule destModule = getApplication().getModuleByName(edge.getDestination());
					if (destModule == null)
						continue;
					Map<Pair<String, String>, SelectivityModel> map = destModule.getSelectivityMap();
					for (Pair<String, String> pair : map.keySet()) {
						if (pair.getFirst().equals(edge.getTupleType())) {
							double outputRate = appEdgeToRate.get(edge) * map.get(pair).getMeanRate(); // getting mean
																										// rate from
																										// SelectivityModel
							AppEdge outputEdge = getApplication().getEdgeMap().get(pair.getSecond());
							if (!appEdgeToRate.containsKey(outputEdge) || appEdgeToRate.get(outputEdge) != outputRate) {
								// if some new information is available
								changed = true;
							}
							appEdgeToRate.put(outputEdge, outputRate);
						}
					}
				}
			}

//			System.out.println("******* EDGE TO RATE: "+appEdgeToRate);

			/*
			 * Getting the list of modules ready to be placed on current device on path
			 */
//			System.out.println(path);
			
			List<String> modulesToPlace = getModulesToPlace(placedModules); // artigo: linha 4 do pseudoc??digo + chamada
																			// para linha 5 (getModulesToPlace)
			// Loop runs until all modules in modulesToPlace are deployed in the path
			while (modulesToPlace.size() > 0) { // artigo: equivalenta a linha 10 do pseudoc??digo
				String moduleName = modulesToPlace.get(0);
				double totalCpuLoad = 0;

				// IF MODULE IS ALREADY PLACED UPSTREAM, THEN UPDATE THE EXISTING MODULE
				int upsteamDeviceId = isPlacedUpstream(moduleName, path); // artigo: linha 11
				if (upsteamDeviceId > 0) { // artigo: linha 11
//					System.out.println("upsteamDeviceId: " +upsteamDeviceId + "= deviceId: " + deviceId);
					if (upsteamDeviceId == deviceId) {
						placedModules.add(moduleName);
						modulesToPlace = getModulesToPlace(placedModules); // artigo: linha 4 novamente

						// NOW THE MODULE TO PLACE IS IN THE CURRENT DEVICE. CHECK IF THE NODE CAN
						// SUSTAIN THE MODULE
						for (AppEdge edge : getApplication().getEdges()) { // take all incoming edges
							if (edge.getDestination().equals(moduleName)) {
//								double rate = appEdgeToRate.get(edge);
								double rate = (appEdgeToRate.get(edge) != null) ? appEdgeToRate.get(edge) : 0.0;
								totalCpuLoad += rate * edge.getTupleCpuLength();
//								System.out.println(edge.getSource()+"-"+edge.getDestination()+"-"+edge.getTupleCpuLength());
							}

						}

						// artigo: linha 12
						if (totalCpuLoad + getCurrentCpuLoad().get(deviceId) > device.getHost().getTotalMips()) {
							Logger.debug("ModulePlacementEdgeward",
									"Need to shift module " + moduleName + " upstream from device " + device.getName());
							System.out.println("ModulePlacementEdgeward " + "Need to shift module " + moduleName
									+ " upstream from device " + device.getName());
							List<String> _placedOperators = shiftModuleNorth(moduleName, totalCpuLoad, deviceId,
									modulesToPlace); // artigo: linha 15
							for (String placedOperator : _placedOperators) {
								if (!placedModules.contains(placedOperator))
									placedModules.add(placedOperator);
							}
						} else { // artigo: linha 21
							placedModules.add(moduleName);
							getCurrentCpuLoad().put(deviceId, getCurrentCpuLoad().get(deviceId) + totalCpuLoad);
							getCurrentModuleInstanceNum().get(deviceId).put(moduleName,
									getCurrentModuleInstanceNum().get(deviceId).get(moduleName) + 1);
							Logger.debug("ModulePlacementEdgeward",
									"AppModule " + moduleName + " can be created on device " + device.getName());
						}
					}
				} else {
					// FINDING OUT WHETHER PLACEMENT OF OPERATOR ON DEVICE IS POSSIBLE
					for (AppEdge edge : getApplication().getEdges()) { // take all incoming edges
						if (edge.getDestination().equals(moduleName)) {
//							System.out.println("appEdgeToRate: "+appEdgeToRate.get(edge));
							double rate = (appEdgeToRate.get(edge) != null) ? appEdgeToRate.get(edge) : 0.0;
//							double rate = appEdgeToRate.get(edge);
//							System.out.println("rate: " + rate);
							totalCpuLoad += rate * edge.getTupleCpuLength();
						}
					}

//					System.out.println(moduleName+"	"+totalCpuLoad + "	" + 
//							getCurrentCpuLoad().get(deviceId)+ "	" + (totalCpuLoad+getCurrentCpuLoad().get(deviceId)) +
//							"		"+device.getHost().getTotalMips() + "(" + device.getId()+")");

//					Helberth: Acrescentei par??metros de mem??ria para fazer o placement

//					String moduleplaced = String.valueOf(getModuleMapping().moduleMapping.get(device.getName())).replace("[","").replace("]", "");
//					
//					boolean memory = true;
//					
//					int moduleMemory = getApplication().getModuleByName(moduleName).getRam();
//					int busyMemory = 0;
//					if (!moduleplaced.equals("null"))
//						busyMemory = getApplication().getModuleByName(moduleplaced).getRam();;
//					
//					if (device.getHost().getRam() < (moduleMemory + busyMemory))
//						memory = false;
//					
//					if(totalCpuLoad + getCurrentCpuLoad().get(deviceId) > device.getHost().getTotalMips() && !memory){
					if (totalCpuLoad + getCurrentCpuLoad().get(deviceId) > device.getHost().getTotalMips()) { // artigo:
																												// linha
																												// 26
						Logger.debug("ModulePlacementEdgeward",
								"Placement of operator " + moduleName + " NOT POSSIBLE on device " + device.getName());
						System.out.println(
								"Placement of operator " + moduleName + " NOT POSSIBLE on device " + device.getName());
					} else {
						Logger.debug("ModulePlacementEdgeward", "Placement of operator " + moduleName + " on device "
								+ device.getName() + " successful.");
						getCurrentCpuLoad().put(deviceId, totalCpuLoad + getCurrentCpuLoad().get(deviceId));
//						System.out.println("Placement of operator "+moduleName+ " on device "+device.getName() + " successful.");

						if (!currentModuleMap.containsKey(deviceId))
							currentModuleMap.put(deviceId, new ArrayList<String>());
						currentModuleMap.get(deviceId).add(moduleName);
						placedModules.add(moduleName);
						modulesToPlace = getModulesToPlace(placedModules);
						getCurrentModuleLoadMap().get(device.getId()).put(moduleName, totalCpuLoad);

						int max = 1;
						for (AppEdge edge : getApplication().getEdges()) {
							if (edge.getSource().equals(moduleName)
									&& actuatorsAssociated.containsKey(edge.getDestination()))
								max = Math.max(actuatorsAssociated.get(edge.getDestination()), max);
							if (edge.getDestination().equals(moduleName)
									&& sensorsAssociated.containsKey(edge.getSource()))
								max = Math.max(sensorsAssociated.get(edge.getSource()), max);
						}
						getCurrentModuleInstanceNum().get(deviceId).put(moduleName, max);
					}
				}

				modulesToPlace.remove(moduleName);
			}

		}

	}

	/**
	 * Shifts a module moduleName from device deviceId northwards. This involves
	 * other modules that depend on it to be shifted north as well.
	 * 
	 * @param moduleName
	 * @param cpuLoad    cpuLoad of the module
	 * @param deviceId
	 */
	private List<String> shiftModuleNorth(String moduleName, double cpuLoad, Integer deviceId,
			List<String> operatorsToPlace) {
		System.out.println(CloudSim.getEntityName(deviceId) + " is shifting " + moduleName + " north.");
		List<String> modulesToShift = findModulesToShift(moduleName, deviceId);

		Map<String, Integer> moduleToNumInstances = new HashMap<String, Integer>(); // Map of number of instances of
																					// modules that need to be shifted
		double totalCpuLoad = 0;
		Map<String, Double> loadMap = new HashMap<String, Double>();
		for (String module : modulesToShift) {
			loadMap.put(module, getCurrentModuleLoadMap().get(deviceId).get(module));
			moduleToNumInstances.put(module, getCurrentModuleInstanceNum().get(deviceId).get(module) + 1);
			totalCpuLoad += getCurrentModuleLoadMap().get(deviceId).get(module);
			getCurrentModuleLoadMap().get(deviceId).remove(module);
			getCurrentModuleMap().get(deviceId).remove(module);
			getCurrentModuleInstanceNum().get(deviceId).remove(module);
		}

		getCurrentCpuLoad().put(deviceId, getCurrentCpuLoad().get(deviceId) - totalCpuLoad); // change info of current
																								// CPU load on device
		loadMap.put(moduleName, loadMap.get(moduleName) + cpuLoad);
		totalCpuLoad += cpuLoad;

		int id = getParentDevice(deviceId);
		while (true) { // Loop iterates over all devices in path upstream from current device. Tries to
						// place modules (to be shifted northwards) on each of them.
			if (id == -1) {
				// Loop has reached the apex fog device in hierarchy, and still could not place
				// modules.
				Logger.debug("ModulePlacementEdgeward", "Could not place modules " + modulesToShift + " northwards.");
				break;
			}
			FogDevice fogDevice = getFogDeviceById(id);
			if (getCurrentCpuLoad().get(id) + totalCpuLoad > fogDevice.getHost().getTotalMips()) {
				// Device cannot take up CPU load of incoming modules. Keep searching for device
				// further north.
				List<String> _modulesToShift = findModulesToShift(modulesToShift, id); // All modules in _modulesToShift
																						// are currently placed on
																						// device id
				double cpuLoadShifted = 0; // the total CPU load shifted from device id to its parent
				for (String module : _modulesToShift) {
					if (!modulesToShift.contains(module)) {
						// Add information of all newly added modules (to be shifted)
						moduleToNumInstances.put(module,
								getCurrentModuleInstanceNum().get(id).get(module) + moduleToNumInstances.get(module));
						loadMap.put(module, getCurrentModuleLoadMap().get(id).get(module));
						cpuLoadShifted += getCurrentModuleLoadMap().get(id).get(module);
						totalCpuLoad += getCurrentModuleLoadMap().get(id).get(module);
						// Removing information of all modules (to be shifted north) in device with ID
						// id
						getCurrentModuleLoadMap().get(id).remove(module);
						getCurrentModuleMap().get(id).remove(module);
						getCurrentModuleInstanceNum().get(id).remove(module);
					}
				}
				getCurrentCpuLoad().put(id, getCurrentCpuLoad().get(id) - cpuLoadShifted); // CPU load on device id gets
																							// reduced due to modules
																							// shifting northwards

				modulesToShift = _modulesToShift;
				id = getParentDevice(id); // iterating to parent device
			} else {
				// Device (@ id) can accommodate modules. Placing them here.
				double totalLoad = 0;
				for (String module : loadMap.keySet()) {
					totalLoad += loadMap.get(module);
					getCurrentModuleLoadMap().get(id).put(module, loadMap.get(module));
					getCurrentModuleMap().get(id).add(module);
					String module_ = module;
					int initialNumInstances = 0;
					if (getCurrentModuleInstanceNum().get(id).containsKey(module_))
						initialNumInstances = getCurrentModuleInstanceNum().get(id).get(module_);
					int finalNumInstances = initialNumInstances + moduleToNumInstances.get(module_);
					getCurrentModuleInstanceNum().get(id).put(module_, finalNumInstances);
				}
				getCurrentCpuLoad().put(id, totalLoad);
				operatorsToPlace.removeAll(loadMap.keySet());
				List<String> placedOperators = new ArrayList<String>();
				for (String op : loadMap.keySet())
					placedOperators.add(op);
				return placedOperators;
			}
		}
		return new ArrayList<String>();
	}

	/**
	 * Get all modules that need to be shifted northwards along with <b>module</b>.
	 * Typically, these other modules are those that are hosted on device with ID
	 * <b>deviceId</b> and lie upstream of <b>module</b> in application model.
	 * 
	 * @param module   the module that needs to be shifted northwards
	 * @param deviceId the fog device ID that it is currently on
	 * @return list of all modules that need to be shifted north along with
	 *         <b>module</b>
	 */
	private List<String> findModulesToShift(String module, Integer deviceId) {
		List<String> modules = new ArrayList<String>();
		modules.add(module);
		return findModulesToShift(modules, deviceId);
		/*
		 * List<String> upstreamModules = new ArrayList<String>();
		 * upstreamModules.add(module); boolean changed = true; while(changed){ // Keep
		 * loop running as long as new information is added. changed = false;
		 * for(AppEdge edge : getApplication().getEdges()){
		 * 
		 * If there is an application edge UP from the module to be shifted to another
		 * module in the same device
		 * 
		 * if(upstreamModules.contains(edge.getSource()) &&
		 * edge.getDirection()==Tuple.UP &&
		 * getCurrentModuleMap().get(deviceId).contains(edge.getDestination()) &&
		 * !upstreamModules.contains(edge.getDestination())){
		 * upstreamModules.add(edge.getDestination()); changed = true; } } } return
		 * upstreamModules;
		 */
	}

	/**
	 * Get all modules that need to be shifted northwards along with <b>modules</b>.
	 * Typically, these other modules are those that are hosted on device with ID
	 * <b>deviceId</b> and lie upstream of modules in <b>modules</b> in application
	 * model.
	 * 
	 * @param module   the module that needs to be shifted northwards
	 * @param deviceId the fog device ID that it is currently on
	 * @return list of all modules that need to be shifted north along with
	 *         <b>modules</b>
	 */
	private List<String> findModulesToShift(List<String> modules, Integer deviceId) {
		List<String> upstreamModules = new ArrayList<String>();
		upstreamModules.addAll(modules);
		boolean changed = true;
		while (changed) { // Keep loop running as long as new information is added.
			changed = false;
			/*
			 * If there is an application edge UP from the module to be shifted to another
			 * module in the same device
			 */
			for (AppEdge edge : getApplication().getEdges()) {
				if (upstreamModules.contains(edge.getSource()) && edge.getDirection() == Tuple.UP
						&& getCurrentModuleMap().get(deviceId).contains(edge.getDestination())
						&& !upstreamModules.contains(edge.getDestination())) {
					upstreamModules.add(edge.getDestination());
					changed = true;
				}
			}
		}
		return upstreamModules;
	}

	private int isPlacedUpstream(String operatorName, List<Integer> path) {
		for (int deviceId : path) {
			if (currentModuleMap.containsKey(deviceId) && currentModuleMap.get(deviceId).contains(operatorName))
				return deviceId;
		}
		return -1;
	}

	/**
	 * Gets all sensors associated with fog-device <b>device</b>
	 * 
	 * @param device
	 * @return map from sensor type to number of such sensors
	 */
	private Map<String, Integer> getAssociatedSensors(FogDevice device) {
		Map<String, Integer> endpoints = new HashMap<String, Integer>();
		for (Sensor sensor : getSensors()) {
			if (sensor.getGatewayDeviceId() == device.getId()) {
				if (!endpoints.containsKey(sensor.getTupleType()))
					endpoints.put(sensor.getTupleType(), 0);
				endpoints.put(sensor.getTupleType(), endpoints.get(sensor.getTupleType()) + 1);
			}
		}
		return endpoints;
	}

	/**
	 * Gets all actuators associated with fog-device <b>device</b>
	 * 
	 * @param device
	 * @return map from actuator type to number of such sensors
	 */
	private Map<String, Integer> getAssociatedActuators(FogDevice device) {
		Map<String, Integer> endpoints = new HashMap<String, Integer>();
		for (Actuator actuator : getActuators()) {
			if (actuator.getGatewayDeviceId() == device.getId()) {
				if (!endpoints.containsKey(actuator.getActuatorType()))
					endpoints.put(actuator.getActuatorType(), 0);
				endpoints.put(actuator.getActuatorType(), endpoints.get(actuator.getActuatorType()) + 1);
			}
		}
		return endpoints;
	}

	@SuppressWarnings("serial")
	protected List<List<Integer>> getPaths(final int fogDeviceId) {
		FogDevice device = (FogDevice) CloudSim.getEntity(fogDeviceId);
		if (device.getChildrenIds().size() == 0) {
			final List<Integer> path = (new ArrayList<Integer>() {
				{
					add(fogDeviceId);
				}
			});
			List<List<Integer>> paths = (new ArrayList<List<Integer>>() {
				{
					add(path);
				}
			});
			return paths;
		}
		List<List<Integer>> paths = new ArrayList<List<Integer>>();
		for (int childId : device.getChildrenIds()) {
			List<List<Integer>> childPaths = getPaths(childId);
			for (List<Integer> childPath : childPaths)
				childPath.add(fogDeviceId);
			paths.addAll(childPaths);
		}
		return paths;
	}

	protected List<List<Integer>> getLeafToRootPaths() {
		FogDevice cloud = null;
		for (FogDevice device : getFogDevices()) {
			if (device.getName().equals("cloud"))
				cloud = device;
		}
		return getPaths(cloud.getId());
	}

	protected List<Integer> getDevicesPerLevel(int level) {
		List<Integer> getList = new ArrayList<Integer>();

		List<FogDevice> devices = this.getFogDevices();
		
		for(FogDevice d: devices) {
			if (d.getLevel()==level)
				getList.add(d.getId());
		}
		
		return getList;
	}
	
	public ModuleMapping getModuleMapping() {
		return moduleMapping;
	}

	public void setModuleMapping(ModuleMapping moduleMapping) {
		this.moduleMapping = moduleMapping;
	}

	public Map<Integer, List<String>> getCurrentModuleMap() {
		return currentModuleMap;
	}

	public void setCurrentModuleMap(Map<Integer, List<String>> currentModuleMap) {
		this.currentModuleMap = currentModuleMap;
	}

	public List<Sensor> getSensors() {
		return sensors;
	}

	public void setSensors(List<Sensor> sensors) {
		this.sensors = sensors;
	}

	public List<Actuator> getActuators() {
		return actuators;
	}

	public void setActuators(List<Actuator> actuators) {
		this.actuators = actuators;
	}

	public Map<Integer, Double> getCurrentCpuLoad() {
		return currentCpuLoad;
	}

	public void setCurrentCpuLoad(Map<Integer, Double> currentCpuLoad) {
		this.currentCpuLoad = currentCpuLoad;
	}

	public Map<Integer, Map<String, Double>> getCurrentModuleLoadMap() {
		return currentModuleLoadMap;
	}

	public void setCurrentModuleLoadMap(Map<Integer, Map<String, Double>> currentModuleLoadMap) {
		this.currentModuleLoadMap = currentModuleLoadMap;
	}

	public Map<Integer, Map<String, Integer>> getCurrentModuleInstanceNum() {
		return currentModuleInstanceNum;
	}

	public void setCurrentModuleInstanceNum(Map<Integer, Map<String, Integer>> currentModuleInstanceNum) {
		this.currentModuleInstanceNum = currentModuleInstanceNum;
	}
}
