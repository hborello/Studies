package org.fog.test.doutorado.newer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

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

public class HomeCareFog1 {

	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	
	static int numOfAreas = 2;
	static int numOfSensors = 2;
	
	static double sensingInterval = 5; 
	
	private static boolean CLOUD = false;
	
//	==================================================
//	Latencies of network
//	==================================================
	static int lcloud = 10; // area to cloud
	static int lareas = 2; // device to area
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
//			Utils2.topologyToJson(fogDevices, sensors, actuators);
//			Utils2.printTopology(fogDevices, sensors, actuators);
			
			Controller controller = null;
			
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
			
//			for(FogDevice device : fogDevices){
//				if(device.getName().startsWith("e")){ // names of all Smart Cameras start with 'm' 
//					moduleMapping.addModuleToDevice("clientModule", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart Camera
//				}
//			}
//			
//			moduleMapping.addModuleToDevice("storageModule", "cloud"); // fixing instances of User Interface module in the Cloud
//			
//			if(CLOUD){
//				// if the mode of deployment is cloud-based
//				moduleMapping.addModuleToDevice("mainModule", "cloud"); // placing all instances of Object Tracker module in the Cloud
//				moduleMapping.addModuleToDevice("eventHandler", "cloud"); // placing all instances of Object Tracker module in the Cloud
//			}
			
			controller = new Controller("master-controller", fogDevices, sensors, 
					actuators);
			
//			start - placing modules manually
			
//			moduleMapping.addModuleToDevice("ECGModule", "e-0-0");
//			moduleMapping.addModuleToDevice("ECGModule", "e-0-1");
//			moduleMapping.addModuleToDevice("ECGModule", "e-1-0");
//			moduleMapping.addModuleToDevice("ECGModule", "e-1-1");
//			
//			moduleMapping.addModuleToDevice("EmergencyCallerModule", "e-0-0");
//			moduleMapping.addModuleToDevice("EmergencyCallerModule", "e-0-1");
//			moduleMapping.addModuleToDevice("EmergencyCallerModule", "e-1-0");
//			moduleMapping.addModuleToDevice("EmergencyCallerModule", "e-1-1");
//			
//			moduleMapping.addModuleToDevice("SmartHealthModule", "a-0");
//			moduleMapping.addModuleToDevice("SmartHealthModule", "a-1");
//			
//			moduleMapping.addModuleToDevice("EmergencyModule", "a-0");
//			moduleMapping.addModuleToDevice("EmergencyModule", "a-1");
//			
//			controller.submitApplication(application, new ModulePlacementMapping(fogDevices, application, moduleMapping));
			
//			end - placing modules manually
			
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
		FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25);
		cloud.setParentId(-1);
		fogDevices.add(cloud);
		
		FogDevice isp = createFogDevice("isp-server", 15000, 10000, 100, 1000, 1, 0.01, 16*103, 16*83.25);
		isp.setParentId(cloud.getId());
		isp.setUplinkLatency(5);
		fogDevices.add(isp);
		
		FogDevice proxy = createFogDevice("proxy-server", 8000, 4095, 10000, 10000, 2, 0.0, 107.339, 83.4333);
		proxy.setParentId(isp.getId());
		proxy.setUplinkLatency(lcloud); // latency of connection between proxy server and cloud is 100 ms
		fogDevices.add(proxy);
		for(int i=0;i<numOfAreas;i++){
			addArea(i+"", userId, appId, proxy.getId(), proxy.getLevel()+1);
		}
		
	}

	private static FogDevice addArea(String id, int userId, String appId, int parentId, int level){
		FogDevice gateway = createFogDevice("a-"+id, 3000, 4000, 10000, 10000, level, 0.0, 107.339, 83.4333);
		fogDevices.add(gateway);
		gateway.setUplinkLatency(lcloud); // latency of connection between gateway and proxy server is 2 ms
		for(int i=0;i<numOfSensors;i++){
			String mobileId = id+"-"+i;
			FogDevice camera = addEndDev(mobileId, userId, appId, gateway.getId(), gateway.getLevel()+1); // adding a smart camera to the physical topology. Smart cameras have been modeled as fog devices as well.
			camera.setUplinkLatency(lareas); // latency of connection between camera and gateway is 2 ms
			fogDevices.add(camera);
		}
		gateway.setParentId(parentId);
		return gateway;
	}
	
	private static FogDevice addEndDev(String id, int userId, String appId, int parentId, int level){
		FogDevice camera = createFogDevice("e-"+id, 500, 64, 10000, 270, level, 0.0, 87.53, 82.44);
		camera.setParentId(parentId);
		
		Sensor sensor = new Sensor("s-"+id, "IoTSensor", userId, appId, new DeterministicDistribution(sensingInterval)); // inter-transmission time of camera (sensor) follows a deterministic distribution
		sensors.add(sensor);
		
//		Sensor sensor2 = new Sensor("s-"+id, "IoTSensor", userId, appId, new DeterministicDistribution(sensingInterval)); // inter-transmission time of camera (sensor) follows a deterministic distribution
//		sensors.add(sensor2);
//		
		
		Actuator ptz = new Actuator("d-"+id, userId, appId, "IoTActuator");
		actuators.add(ptz);
		
		sensor.setGatewayDeviceId(camera.getId());
		sensor.setLatency(lsensor);  // latency of connection between camera (sensor) and the parent Smart Camera is 1 ms
		
//		sensor2.setGatewayDeviceId(camera.getId());
//		sensor2.setLatency(lsensor);  // latency of connection between camera (sensor) and the parent Smart Camera is 1 ms
		
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
	private static Application createApplicationX(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId);
		/*
		 * Adding modules (vertices) to the application model (directed graph)
		 */
		application.addAppModule("mainModule", 64, 1500, 4000, 800);
		application.addAppModule("clientModule", 32, 1000, 1000, 100);
		application.addAppModule("storageModule", 64, 500, 12000, 100);
		application.addAppModule("eventHandler", 32, 1000, 1000, 100);
		
		/*
		 * Connecting the application modules (vertices) in the application model (directed graph) with edges
		 */
		application.addAppEdge("IoTSensor", "clientModule", 100, 200, "IoTSensor", Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge("clientModule", "mainModule", 6000, 600  , "RawData", Tuple.UP, AppEdge.MODULE); 
		application.addAppEdge("mainModule", "storageModule", 1000, 300, "StoreData", Tuple.UP, AppEdge.MODULE); 
		application.addAppEdge("mainModule", "eventHandler", 1000, 50, "ResultData", Tuple.DOWN, AppEdge.MODULE);
		application.addAppEdge("eventHandler", "clientModule", 1000, 50, "Response", Tuple.DOWN, AppEdge.MODULE);
		application.addAppEdge("clientModule", "IoTActuator", 100, 50, "Action", Tuple.DOWN, AppEdge.ACTUATOR);
		
		/*
		 * Defining the input-output relationships (represented by selectivity) of the application modules. 
		 */
		application.addTupleMapping("clientModule", "IoTSensor", "RawData", new FractionalSelectivity(0.9));
		application.addTupleMapping("mainModule", "RawData", "StoreData", new FractionalSelectivity(1.0));
		application.addTupleMapping("mainModule", "RawData", "ResultData", new FractionalSelectivity(1.0));
		application.addTupleMapping("eventHandler", "ResultData", "Response", new FractionalSelectivity(1.0));
		application.addTupleMapping("clientModule", "Response", "Action", new FractionalSelectivity(0.8));
		
		/*
		 * Defining application loops (maybe incomplete loops) to monitor the latency of. 
		 * Here, we add two loops for monitoring : Motion Detector -> Object Detector -> Object Tracker and Object Tracker -> PTZ Control
		 */
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("IoTSensor");add("clientModule");add("mainModule");add("eventHandler");add("clientModule");add("IoTActuator");}});
//		final AppLoop loop2 = new AppLoop(new ArrayList<String>(){{add("mainModule");add("storageModule");}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
		
		application.setLoops(loops);
		return application;
	}
	
	
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId);
		/*
		 * Adding modules (vertices) to the application model (directed graph)
		 */
//		Parameters:
//			moduleName
//			ram
//			mips
//			size
//			bw
		application.addAppModule("ECGModule", 32, 1000, 2000, 600);
		application.addAppModule("SmartHealthModule", 32, 2000, 4000, 3500);
		application.addAppModule("EmergencyModule", 32, 1000, 2000, 1500);
		application.addAppModule("EmergencyCallerModule", 16, 500, 800, 300);
		
		
		application.addAppEdge("IoTSensor", "ECGModule", 100, 200, "IoTSensor", Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge("ECGModule", "SmartHealthModule", 6000, 600  , "RawData", Tuple.UP, AppEdge.MODULE); 
		application.addAppEdge("SmartHealthModule", "EmergencyModule", 1000, 50, "ResultData", Tuple.UP, AppEdge.MODULE);
		application.addAppEdge("EmergencyModule", "EmergencyCallerModule", 1000, 50, "Response", Tuple.DOWN, AppEdge.MODULE);
		application.addAppEdge("EmergencyCallerModule", "IoTActuator", 100, 50, "Action", Tuple.DOWN, AppEdge.ACTUATOR);
		
		application.addTupleMapping("ECGModule", "IoTSensor", "RawData", new FractionalSelectivity(0.9));
		application.addTupleMapping("SmartHealthModule", "RawData", "ResultData", new FractionalSelectivity(1.0));
		application.addTupleMapping("EmergencyModule", "ResultData", "Response", new FractionalSelectivity(1.0));
		application.addTupleMapping("EmergencyCallerModule", "Response", "Action", new FractionalSelectivity(0.8));
		
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("IoTSensor");add("ECGModule");add("SmartHealthModule");add("EmergencyModule");add("EmergencyCallerModule");add("IoTActuator");}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
				
		application.setLoops(loops);
		return application;
	}
		


}
