package org.fog.test.doutorado.newer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.test.doutorado.Utils2;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;


public class ECGMonitoringHealthcate {

	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();

//	numbers of end devices to compose the topology
	static int numOfAreas = 2;
	static int numOfECG = 1; // number of end devices for ECG
	static int numOfBP = 1; // number of end devices for Blood Pressure
	static int numOfG = 1; // number of end devices for Glucometers
	static int numOfCE = 1; // number of end devices for Call Emergency

//	definition of latency value between layers
	static int level0 = 30; // example: latency between level 0 and 1 (cloud and isp-server)
	static int level1 = 20;
	static int level2 = 15;
	static int level3 = 10;
	static int level4 = 5;
	static double level_sensor = 1;
	static double level_actuator = 1;

	private static boolean CLOUD = false;

	public static void main(String[] args) {

		Log.printLine("Starting Smart Healthcare...");

		try {
//			Initial code to start simulation

			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events
			CloudSim.init(num_user, calendar, trace_flag);
			String appId = "ECGMonitoringHealthcare"; // identifier of the application
			FogBroker broker = new FogBroker("broker");

//			Creating the topology of simulation
			createFogDevices(broker.getId(), appId);

//			Printing topology
//			Utils2.printTopology(fogDevices, sensors, actuators);
			Utils2.topologyToJson(fogDevices, sensors, actuators);
			
//			Creating applicatacion
			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			
			
			Controller controller = null;
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
			
			moduleMapping.addModuleToDevice("HistoricalDataModule", "cloud"); // fixing instances of User Interface module in the Cloud
			
			controller = new Controller("master-controller", fogDevices, sensors, 
					actuators);
			
			controller.submitApplication(application, 
					(CLOUD)?(new ModulePlacementMapping(fogDevices, application, moduleMapping))
							:(new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));
			
			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
			
			CloudSim.startSimulation();

			CloudSim.stopSimulation();

			Log.printLine("Simulation finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}

	}

//	initializing topology simulation
	private static void createFogDevices(int userId, String appId) {
//		creating cloud device level 0
		FogDevice cloud = Utils2.createFogDevice("cloud", 80000, 40000, 10000, 10000, 0, 0.01, 16 * 103, 16 * 83.25);
		cloud.setParentId(-1);
		fogDevices.add(cloud);

//		creating isp-server level 1
		FogDevice isp = Utils2.createFogDevice("isp-server", 20000, 10000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
		isp.setParentId(cloud.getId());
		isp.setUplinkLatency(level1);
		fogDevices.add(isp);

		FogDevice proxy = Utils2.createFogDevice("proxy-server", 8000, 4095, 10000, 10000, 2, 0.0, 107.339, 83.4333);
		proxy.setParentId(isp.getId());
		proxy.setUplinkLatency(level2); // latency of connection between proxy server and cloud is 100 ms
		fogDevices.add(proxy);

		for (int i = 0; i < numOfAreas; i++) {
			addArea(i + "", userId, appId, proxy.getId());
		}
	}

	private static FogDevice addArea(String id, int userId, String appId, int parentId) {
		FogDevice area = Utils2.createFogDevice("area-" + id, 3000, 1024, 10000, 10000, 3, 0.0, 107.339, 83.4333);
		fogDevices.add(area);
		area.setUplinkLatency(level3); // latency of connection between router and proxy server is 2 ms

//		creating end devices and sensor/actuators

		// loop to create ECG devices
		for (int i = 0; i < numOfECG; i++) {
			String mobileId = "ecg-" + id + "-" + i;
			FogDevice endDevice = addECGDevice(mobileId, userId, appId, area.getId());
			endDevice.setUplinkLatency(level4);
			fogDevices.add(endDevice);
		}

//		// loop to create Blood Pressure devices
//		for (int i = 0; i < numOfBP; i++) {
//			String mobileId = "bp-" + id + "-" + i;
//			FogDevice endDevice = addBPDevice(mobileId, userId, appId, area.getId());
//			endDevice.setUplinkLatency(level4);
//			fogDevices.add(endDevice);
//		}
//
//		// loop to create Glucometer devices
//		for (int i = 0; i < numOfG; i++) {
//			String mobileId = "g-" + id + "-" + i;
//			FogDevice endDevice = addGDevice(mobileId, userId, appId, area.getId());
//			endDevice.setUplinkLatency(level4);
//			fogDevices.add(endDevice);
//		}
//
		// loop to create Emergency Call devices
		for (int i = 0; i < numOfCE; i++) {
			String mobileId = "ec-" + id + "-" + i;
			FogDevice endDevice = addECDevice(mobileId, userId, appId, area.getId());
			endDevice.setUplinkLatency(level4);
			fogDevices.add(endDevice);
		}

		area.setParentId(parentId);
		return area;
	}

	// creating end devices to ECG sensor and monitor
	private static FogDevice addECGDevice(String id, int userId, String appId, int parentId) {
		FogDevice ecg = Utils2.createFogDevice(id, 256, 256, 100, 200, 4, 0, 87.53, 82.44);
		ecg.setParentId(parentId);

		// creating sensor ECG
		Sensor sensor = new Sensor("s-" + id, "ECGSensor", userId, appId, new DeterministicDistribution(10)); // inter-transmission
																												// time
																												// of
																												// camera
																												// (sensor)
																												// follows
																												// a
																												// deterministic
																												// distribution
		sensors.add(sensor);

		// creating monitor ECG
		Actuator actuator = new Actuator("m-" + id, userId, appId, "ECGMonitor");
		actuators.add(actuator);

		sensor.setGatewayDeviceId(ecg.getId());
		sensor.setLatency(level_sensor);

		actuator.setGatewayDeviceId(ecg.getId());
		actuator.setLatency(level_actuator);
		
		// creating monitor ECG
//		Actuator actuator1 = new Actuator("a-" + id, userId, appId, "DisplayAlarm");
//		actuators.add(actuator1);
//
//		actuator1.setGatewayDeviceId(ecg.getId());
//		actuator1.setLatency(level_actuator); 

				

		return ecg;
	}

	// creating end devices Blood Pressure sensor and alarm
	private static FogDevice addBPDevice(String id, int userId, String appId, int parentId) {
		FogDevice bp = Utils2.createFogDevice(id, 500, 512, 100, 200, 4, 0, 87.53, 82.44);
		bp.setParentId(parentId);

		// creating sensor Blood Pressure
		Sensor sensor = new Sensor("s-" + id, "bpSensor", userId, appId, new DeterministicDistribution(10)); 
		sensors.add(sensor);

		// creating alarm Blood Pressure
		Actuator actuator = new Actuator("a-" + id, userId, appId, "bpAlarm");
		actuators.add(actuator);

		sensor.setGatewayDeviceId(bp.getId());
		sensor.setLatency(level_sensor); 

		actuator.setGatewayDeviceId(bp.getId());
		actuator.setLatency(level_actuator);

		return bp;
	}

	// creating end devices Blood Pressure sensor and alarm
	private static FogDevice addGDevice(String id, int userId, String appId, int parentId) {
		FogDevice glucometer = Utils2.createFogDevice(id, 500, 512, 100, 200, 4, 0, 87.53, 82.44);
		glucometer.setParentId(parentId);

		// creating sensor ECG
		Sensor sensor = new Sensor("s-" + id, "gSensor", userId, appId, new DeterministicDistribution(10)); 
		sensors.add(sensor);

		// creating monitor ECG
		Actuator actuator = new Actuator("a-" + id, userId, appId, "gAlarm");
		actuators.add(actuator);

		sensor.setGatewayDeviceId(glucometer.getId());
		sensor.setLatency(level_sensor); 

		actuator.setGatewayDeviceId(glucometer.getId());
		actuator.setLatency(level_actuator); 

		return glucometer;
	}

	// creating end devices Blood Pressure sensor and alarm
	private static FogDevice addECDevice(String id, int userId, String appId, int parentId) {
		FogDevice ec = Utils2.createFogDevice(id, 500, 512, 100, 200, 4, 0, 87.53, 82.44);
		ec.setParentId(parentId);

		
		
		// creating monitor ECG
		Actuator actuator = new Actuator("a-" + id, userId, appId, "DisplayAlarm");
		actuators.add(actuator);

		actuator.setGatewayDeviceId(ec.getId());
		actuator.setLatency(level_actuator); 

		return ec;
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
		application.addAppModule("SmartHealthModule", 128, 2000, 4000, 3500);
		application.addAppModule("EmergencyCallerModule", 16, 500, 800, 300);
		application.addAppModule("RoutineServicesModule", 64, 1500, 2000, 2000);
		application.addAppModule("EmergencyModule", 32, 1000, 2000, 1500);
		application.addAppModule("HistoricalDataModule", 256, 2000, 4000, 3500);
		
		
//		Parameters:
//			source
//			destination
//			tupleCpuLength
//			tupleNwLength
//			tupleType
//			direction
//			edgeType
		application.addAppEdge("ECGSensor", "ECGModule", 300, 350, "ECGSensor", Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge("ECGModule", "SmartHealthModule", 800, 1600, "ECGData", Tuple.UP, AppEdge.MODULE);
		application.addAppEdge("SmartHealthModule", "RoutineServicesModule", 1600, 2200, "ServicesData", Tuple.UP, AppEdge.MODULE);
		application.addAppEdge("RoutineServicesModule", "HistoricalDataModule", 1600, 2200, "HistoricalData", Tuple.UP, AppEdge.MODULE);
		application.addAppEdge("SmartHealthModule", "EmergencyModule", 300, 400, "EmergencyData", Tuple.UP, AppEdge.MODULE);
		application.addAppEdge("EmergencyModule", "EmergencyCallerModule", 200, 300, "ECData", Tuple.DOWN, AppEdge.MODULE);
		application.addAppEdge("EmergencyCallerModule", "DisplayAlarm", 100, 50, "CallerActuator", Tuple.DOWN, AppEdge.ACTUATOR);
		
		
		application.addTupleMapping("ECGModule", "ECGSensor", "ECGData", new FractionalSelectivity(1.0));
		application.addTupleMapping("SmartHealthModule", "ECGData", "ServicesData", new FractionalSelectivity(1.0));
		application.addTupleMapping("RoutineServicesModule", "ServicesData", "HistoricalData", new FractionalSelectivity(1.0));
		application.addTupleMapping("SmartHealthModule", "ECGData", "EmergencyData", new FractionalSelectivity(1.0));
		application.addTupleMapping("EmergencyModule", "EmergencyData", "ECData", new FractionalSelectivity(1.0));
		application.addTupleMapping("EmergencyCallerModule", "ECData", "CallerActuator", new FractionalSelectivity(1.0));
		

		/*
		 * Defining application loops (maybe incomplete loops) to monitor the latency of. 
		 * Here, we add two loops for monitoring : Motion Detector -> Object Detector -> Object Tracker and Object Tracker -> PTZ Control
		 */
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("ECGSensor");add("ECGModule");add("SmartHealthModule");add("EmergencyModule");add("EmergencyCallerModule");add("DisplayAlarm");}});
		final AppLoop loop2 = new AppLoop(new ArrayList<String>(){{add("SmartHealthModule");add("RoutineServicesModule");add("HistoricalDataModule");}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);add(loop2);}};
		
		application.setLoops(loops);
		return application;
	}
		
		
//	@SuppressWarnings({"serial", "unused" })
//	private static Application createApplication_1(String appId, int userId){
//		
//		Application application = Application.createApplication(appId, userId);
//		/*
//		 * Adding modules (vertices) to the application model (directed graph)
//		 */
////		Parameters:
////			moduleName
////			ram
////			mips
////			size
////			bw
//		application.addAppModule("ECGModule", 32, 1000, 2000, 600);
////		application.addAppModule("BloodPressureModule", 16, 500, 800, 300);
////		application.addAppModule("GlucometerModule", 16, 500, 800, 300);
//		application.addAppModule("EmergencyCallerModule", 16, 500, 800, 300);
//		application.addAppModule("EmergencyModule", 32, 1000, 2000, 1500);
//		application.addAppModule("SmartHealthModule", 128, 2000, 4000, 3500);
//		application.addAppModule("RoutineServicesModule", 64, 1500, 2000, 2000);
//		application.addAppModule("HistoricalDataModule", 256, 2000, 4000, 3500);
//		
//		
////		Parameters:
////			source
////			destination
////			tupleCpuLength
////			tupleNwLength
////			tupleType
////			direction
////			edgeType
//		application.addAppEdge("ECGSensor", "ECGModule", 300, 350, "ECGSensor", Tuple.UP, AppEdge.SENSOR);
////		application.addAppEdge("BPSensor", "BloodPressureModule", 100, 150, "BPSensor", Tuple.UP, AppEdge.SENSOR);
////		application.addAppEdge("GSensor", "GlucometerModule", 100, 150, "GSensor", Tuple.UP, AppEdge.SENSOR);
//		application.addAppEdge("ECGModule", "SmartHealthModule", 800, 1600, "ECGData", Tuple.UP, AppEdge.MODULE);
////		application.addAppEdge("BloodPressureModule", "SmartHealthModule", 800, 1600, "BPData", Tuple.UP, AppEdge.MODULE);
////		application.addAppEdge("GlucometerModule", "SmartHealthModule", 800, 1600, "GData", Tuple.UP, AppEdge.MODULE);
//		application.addAppEdge("SmartHealthModule", "RoutineServicesModule", 1600, 2200, "ServicesData", Tuple.UP, AppEdge.MODULE);
//		application.addAppEdge("RoutineServicesModule", "HistoricalDataModule", 1600, 2200, "HistoricalData", Tuple.UP, AppEdge.MODULE);
//		application.addAppEdge("SmartHealthModule", "EmergencyModule", 300, 400, "EmergencyData", Tuple.DOWN, AppEdge.MODULE);
//		application.addAppEdge("EmergencyModule", "EmergencyCallerModule", 200, 300, "ECData", Tuple.DOWN, AppEdge.MODULE);
//		application.addAppEdge("ECGModule", "ECGMonitor", 100, 50, "ECGActuator", Tuple.DOWN, AppEdge.ACTUATOR);
////		application.addAppEdge("BloodPressureModule", "BPAlarm", 100, 50, "BPActuator", Tuple.DOWN, AppEdge.ACTUATOR);
////		application.addAppEdge("GlucometerModule", "GAlarm", 100, 50, "GActuator", Tuple.DOWN, AppEdge.ACTUATOR);
//		application.addAppEdge("EmergencyCallerModule", "DisplayAlarm", 100, 50, "CallerActuator", Tuple.DOWN, AppEdge.ACTUATOR);
//		
//		application.addTupleMapping("ECGModule", "ECGSensor", "ECGData", new FractionalSelectivity(1.0));
////		application.addTupleMapping("BloodPressureModule", "BPSensor", "BPData", new FractionalSelectivity(1.0));
////		application.addTupleMapping("GlucometerModule", "GSensor", "GData", new FractionalSelectivity(1.0));
//		
//		application.addTupleMapping("ECGModule", "ECGSensor", "ECGActuator", new FractionalSelectivity(1.0));
////		application.addTupleMapping("BloodPressureModule", "BPSensor", "BPActuator", new FractionalSelectivity(1.0));
////		application.addTupleMapping("GlucometerModule", "GSensor", "GActuator", new FractionalSelectivity(1.0));
//		
//		application.addTupleMapping("SmartHealthModule", "ECGData", "ServicesData", new FractionalSelectivity(0.4));
////		application.addTupleMapping("SmartHealthModule", "BPGData", "ServicesData", new FractionalSelectivity(0.3));
////		application.addTupleMapping("SmartHealthModule", "GData", "ServicesData", new FractionalSelectivity(0.3));
//		application.addTupleMapping("RoutineServicesModule", "ServicesData", "HistoricalData", new FractionalSelectivity(1.0));
//		
//		application.addTupleMapping("SmartHealthModule", "ECGData", "EmergencyData", new FractionalSelectivity(0.4));
////		application.addTupleMapping("SmartHealthModule", "BPGData", "EmergencyData", new FractionalSelectivity(0.3));
////		application.addTupleMapping("SmartHealthModule", "GData", "EmergencyData", new FractionalSelectivity(0.3));
//		application.addTupleMapping("EmergencyModule", "EmergencyData", "ECData", new FractionalSelectivity(0.3));
//		application.addTupleMapping("EmergencyCallerModule", "ECData", "CallerActuator", new FractionalSelectivity(1.0));
//
//		/*
//		 * Defining application loops (maybe incomplete loops) to monitor the latency of. 
//		 * Here, we add two loops for monitoring : Motion Detector -> Object Detector -> Object Tracker and Object Tracker -> PTZ Control
//		 */
//		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("ECGSensor");add("ECGModule");add("SmartHealthModule");add("RoutineServicesModule");}});
//		final AppLoop loop2 = new AppLoop(new ArrayList<String>(){{add("SmartHealthModule");add("EmergencyModule");add("EmergencyCallerModule");add("DisplayAlarm");}});
//		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);add(loop2);}};
//		
//		application.setLoops(loops);
//		return application;
//	}

}
