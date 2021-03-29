package org.fog.test.doutorado.newer;

import java.util.ArrayList;
import java.util.Calendar;
//import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
//import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.test.doutorado.Utils2;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

public class HomeCareFog {

	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	
	static int numOfAreas = 2;
	static int numOfSensors = 2;
	
	private static boolean CLOUD = false;
	
//	==================================================
//	Latencies of network
//	==================================================
	
	static int lcloud = 50; // proxy to cloud
	static int lproxy = 5; // area to proxy
	static int lareas = 3; // device to area
	static double lsensor = 2; // sensor to device
	static double lactuat = 1; // actuator to device
	
	public static void main(String[] args) {

		Log.printLine("Starting Smart Healthcare...");

		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events

			CloudSim.init(num_user, calendar, trace_flag);

			String appId = "healthcare"; // identifier of the application
			
			FogBroker broker = new FogBroker("broker");
			
			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			
			createFogDevices(broker.getId(), appId);
			Utils2.topologyToJson(fogDevices, sensors, actuators);
			
			Controller controller = null;
			
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
			for(FogDevice device : fogDevices){
				if(device.getName().startsWith("m")){ // names of all Smart Cameras start with 'm' 
					moduleMapping.addModuleToDevice("clientModule", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart Camera
				}
			}
			moduleMapping.addModuleToDevice("dataStorage", "cloud"); // fixing instances of User Interface module in the Cloud
			
			if(CLOUD){
				// if the mode of deployment is cloud-based
				moduleMapping.addModuleToDevice("dataProcessing", "cloud"); // placing all instances of Object Detector module in the Cloud
				moduleMapping.addModuleToDevice("eventHandler", "cloud"); // placing all instances of Object Tracker module in the Cloud
			}
			
			controller = new Controller("master-controller", fogDevices, sensors, 
					actuators);
			
			controller.submitApplication(application, 
					(CLOUD)?(new ModulePlacementMapping(fogDevices, application, moduleMapping))
							:(new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));
			
			
			
			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
			
			CloudSim.startSimulation();

			CloudSim.stopSimulation();

			Log.printLine("VRGame finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}
	
	
	/**
	 * Creates the fog devices in the physical topology of the simulation.
	 * @param userId
	 * @param appId
	 */
	private static void createFogDevices(int userId, String appId) {
		FogDevice cloud = createFogDevice("cloud", 80000, 40000, 10000, 10000, 0, 0.01, 16*103, 16*83.25);
		cloud.setParentId(-1);
		fogDevices.add(cloud);
		FogDevice proxy = createFogDevice("proxy-server", 8000, 4095, 10000, 10000, 1, 0.0, 107.339, 83.4333);
		proxy.setParentId(cloud.getId());
		proxy.setUplinkLatency(lcloud); // latency of connection between proxy server and cloud is 100 ms
		fogDevices.add(proxy);
		for(int i=0;i<numOfAreas;i++){
			addArea(i+"", userId, appId, proxy.getId());
		}
	}

	private static FogDevice addArea(String id, int userId, String appId, int parentId){
		FogDevice router = createFogDevice("d-"+id, 3000, 1024, 10000, 10000, 2, 0.0, 107.339, 83.4333);
		fogDevices.add(router);
		router.setUplinkLatency(lproxy); // latency of connection between router and proxy server is 2 ms
		for(int i=0;i<numOfSensors;i++){
			String mobileId = id+"-"+i;
			FogDevice camera = addCamera(mobileId, userId, appId, router.getId()); // adding a smart camera to the physical topology. Smart cameras have been modeled as fog devices as well.
			camera.setUplinkLatency(lareas); // latency of connection between camera and router is 2 ms
			fogDevices.add(camera);
		}
		router.setParentId(parentId);
		return router;
	}
	
	private static FogDevice addCamera(String id, int userId, String appId, int parentId){
		FogDevice camera = createFogDevice("m-"+id, 500, 512, 100, 200, 3, 0, 87.53, 82.44);
		camera.setParentId(parentId);
		Sensor sensor = new Sensor("s-"+id, "healthSensor", userId, appId, new DeterministicDistribution(10)); // inter-transmission time of camera (sensor) follows a deterministic distribution
		sensors.add(sensor);
		
		Actuator ptz = new Actuator("d-"+id, userId, appId, "displayActuator");
		actuators.add(ptz);
		
		sensor.setGatewayDeviceId(camera.getId());
		sensor.setLatency(lsensor);  // latency of connection between camera (sensor) and the parent Smart Camera is 1 ms
		ptz.setGatewayDeviceId(camera.getId());
		ptz.setLatency(lactuat);  // latency of connection between PTZ Control and the parent Smart Camera is 1 ms
		return camera;
	}
	
	/**
	 * Creates a vanilla fog device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
		
		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage
		int bw = 10000;

		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		return fogdevice;
	}

	/**
	 * Function to create the Intelligent Surveillance application in the DDF model. 
	 * @param appId unique identifier of the application
	 * @param userId identifier of the user of the application
	 * @return
	 */
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId);
		/*
		 * Adding modules (vertices) to the application model (directed graph)
		 */
		application.addAppModule("dataProcessing", 50);
		application.addAppModule("clientModule", 10);
		application.addAppModule("eventHandler", 10);
		application.addAppModule("dataStorage", 30);
		
	
		application.addAppEdge("healthSensor", "clientModule", 100, 200, "healthSensor", Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge("clientModule", "dataProcessing", 2000, 2000, "RawData", Tuple.UP, AppEdge.MODULE);
		application.addAppEdge("dataProcessing", "dataStorage", 500, 300, "DataStore", Tuple.UP, AppEdge.MODULE); 
		application.addAppEdge("dataProcessing", "eventHandler", 1000, 100, "ResultData", Tuple.DOWN, AppEdge.MODULE);
		application.addAppEdge("eventHandler", "clientModule", 100, 50, "Response", Tuple.DOWN, AppEdge.MODULE);
		application.addAppEdge("clientModule", "displayActuator", 100, 50, "Action", Tuple.DOWN, AppEdge.ACTUATOR);
		
		application.addTupleMapping("clientModule", "healthSensor", "RawData", new FractionalSelectivity(1.0));
		application.addTupleMapping("dataProcessing", "RawData", "DataStore", new FractionalSelectivity(1.0));
		application.addTupleMapping("dataProcessing", "RawData", "ResultData", new FractionalSelectivity(1.0));
		application.addTupleMapping("eventHandler", "ResultData", "Response", new FractionalSelectivity(1.0));
		application.addTupleMapping("clientModule", "Response", "Action", new FractionalSelectivity(1.0));
		
		/*
		 * Defining application loops (maybe incomplete loops) to monitor the latency of. 
		 * Here, we add two loops for monitoring : Motion Detector -> Object Detector -> Object Tracker and Object Tracker -> PTZ Control
		 */
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("healthSensor");add("clientModule");add("dataProcessing");add("eventHandler");add("clientModule");add("displayActuator");}});
		final AppLoop loop2 = new AppLoop(new ArrayList<String>(){{add("dataProcessing");add("dataStorage");}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);add(loop2);}};
		
		application.setLoops(loops);
		return application;
	}

}
