package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
import org.fog.application.MyApplication;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.MyActuator;
import org.fog.entities.MyFogDevice;
import org.fog.entities.MySensor;
import org.fog.entities.Tuple;
import org.fog.placement.ModuleMapping;
import org.fog.placement.MyController;
import org.fog.placement.MyModulePlacement;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.test.doutorado.Utils;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;


public class TestApplication2 {
	static List<MyFogDevice> fogDevices = new ArrayList<MyFogDevice>();
	static Map<Integer,MyFogDevice> deviceById = new HashMap<Integer,MyFogDevice>();
	static List<MySensor> sensors = new ArrayList<MySensor>();
	static List<MyActuator> actuators = new ArrayList<MyActuator>();
	static List<Integer> idOfEndDevices = new ArrayList<Integer>();
	static Map<Integer, Map<String, Double>> deadlineInfo = new HashMap<Integer, Map<String, Double>>();
	static Map<Integer, Map<String, Integer>> additionalMipsInfo = new HashMap<Integer, Map<String, Integer>>();
	
	static boolean CLOUD = false;
	
	static int numOfGateways = 2;
	static int numOfEndDevPerGateway = 2;
	static double sensingInterval = 5; 
	
	public static void main(String[] args) {

		Log.printLine("Starting TestApplication2...");

		try {
			Log.disable();
			int num_user = 1;
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; 
			CloudSim.init(num_user, calendar, trace_flag);
			String appId = "test_app"; 
			FogBroker broker = new FogBroker("broker");
			
			createFogDevices(broker.getId(), appId);
			Utils.topologyToJson(fogDevices, sensors, actuators);
			
			MyApplication application = createApplication2(appId, broker.getId());
			application.setUserId(broker.getId());
			
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); 
			
			moduleMapping.addModuleToDevice("storageModule", "cloud"); 
			for(int i=0;i<idOfEndDevices.size();i++)
			{
				MyFogDevice fogDevice = deviceById.get(idOfEndDevices.get(i));
				moduleMapping.addModuleToDevice("clientModule", fogDevice.getName()); 
			}
			
			
			MyController controller = new MyController("master-controller", fogDevices, sensors, actuators);
			
			controller.submitApplication(application, 0, new MyModulePlacement(fogDevices, sensors, actuators, application, moduleMapping,"dataProcessingModule"));

			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

			CloudSim.startSimulation();

			CloudSim.stopSimulation();

			Log.printLine("TestApplication finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}
	
	private static double getvalue(double min, double max)
	{
		Random r = new Random();
		double randomValue = min + (max - min) * r.nextDouble();
		return randomValue;
	}
	
	private static int getvalue(int min, int max)
	{
		Random r = new Random();
		int randomValue = min + r.nextInt()%(max - min);
		return randomValue;
	}

	private static void createFogDevices(int userId, String appId) {
		MyFogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25);
		cloud.setParentId(-1);
		fogDevices.add(cloud);
		deviceById.put(cloud.getId(), cloud);
		
		for(int i=0;i<numOfGateways;i++){
			addGw(i+"", userId, appId, cloud.getId()); 
		}
		
		
//		MyFogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333); // creates the fog device Proxy Server (level=1)
//		proxy.setParentId(cloud.getId()); // setting Cloud as parent of the Proxy Server
//		proxy.setUplinkLatency(100); // latency of connection from Proxy Server to the Cloud is 100 ms
//		
//		fogDevices.add(cloud);
//		fogDevices.add(proxy);
//		
//		for(int i=0;i<numOfGateways;i++){
//			addGw(i+"", userId, appId, proxy.getId()); // adding a fog device for every Gateway in physical topology. The parent of each gateway is the Proxy Server
//		}
	}

	private static void addGw(String gwPartialName, int userId, String appId, int parentId){
		MyFogDevice gw = createFogDevice("g-"+gwPartialName, 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
		fogDevices.add(gw);
		deviceById.put(gw.getId(), gw);
		gw.setParentId(parentId);
		gw.setUplinkLatency(4); 
		for(int i=0;i<numOfEndDevPerGateway;i++){
			String endPartialName = gwPartialName+"-"+i;
			MyFogDevice end  = addEnd(endPartialName, userId, appId, gw.getId()); 
			end.setUplinkLatency(2); 
			fogDevices.add(end);
			deviceById.put(end.getId(), end);
		}
		
	}
	
	private static MyFogDevice addEnd(String endPartialName, int userId, String appId, int parentId){
		MyFogDevice end = createFogDevice("e-"+endPartialName, 3200, 1000, 10000, 270, 2, 0, 87.53, 82.44);
		end.setParentId(parentId);
		idOfEndDevices.add(end.getId());
		MySensor sensor = new MySensor("s-"+endPartialName, "IoTSensor", userId, appId, new DeterministicDistribution(sensingInterval)); // inter-transmission time of EEG sensor follows a deterministic distribution
		sensors.add(sensor);
		MyActuator actuator = new MyActuator("a-"+endPartialName, userId, appId, "IoTActuator");
		actuators.add(actuator);
		sensor.setGatewayDeviceId(end.getId());
		sensor.setLatency(6.0);  // latency of connection between EEG sensors and the parent Smartphone is 6 ms
		actuator.setGatewayDeviceId(end.getId());
		actuator.setLatency(1.0);  // latency of connection between Display actuator and the parent Smartphone is 1 ms
		return end;
	}
	
	private static MyFogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
		List<Pe> peList = new ArrayList<Pe>();
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); 
		int hostId = FogUtils.generateEntityId();
		long storage = 1000000;
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
		String arch = "x86"; 
		String os = "Linux"; 
		String vmm = "Xen";
		double time_zone = 10.0; 
		double cost = 3.0; 
		double costPerMem = 0.05; 
		double costPerStorage = 0.001; 
		double costPerBw = 0.0; 
		LinkedList<Storage> storageList = new LinkedList<Storage>(); 
		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		MyFogDevice fogdevice = null;
		try {
			fogdevice = new MyFogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		fogdevice.setMips((int) mips);
		return fogdevice;
	}

	@SuppressWarnings({"serial" })
	private static MyApplication createApplication(String appId, int userId){
		
		MyApplication application = MyApplication.createApplication(appId, userId); 
		application.addAppModule("dataProcessingModule", 50, 1500, 4000, 800);
		application.addAppModule("clientModule",10, 1000, 1000, 100);
		application.addAppModule("eventHandlerModule", 10, 1000, 1000, 100);
		application.addAppModule("storageModule", 30, 50, 12000, 100);
		
		application.addAppEdge("healthSensor", "clientModule", 100, 200, "healthSensor", Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge("clientModule", "dataProcessingModule", 2000, 2000, "RawData", Tuple.UP, AppEdge.MODULE);
		application.addAppEdge("dataProcessingModule", "storageModule", 500, 300, "DataStore", Tuple.UP, AppEdge.MODULE); 
		application.addAppEdge("dataProcessingModule", "eventHandlerModule", 1000, 100, "ResultData", Tuple.UP, AppEdge.MODULE);
		application.addAppEdge("eventHandlerModule", "clientModule", 100, 50, "Response", Tuple.DOWN, AppEdge.MODULE);
		application.addAppEdge("clientModule", "displayActuator", 100, 50, "Action", Tuple.DOWN, AppEdge.ACTUATOR);
		
		application.addTupleMapping("clientModule", "healthSensor", "RawData", new FractionalSelectivity(1.0));
		application.addTupleMapping("dataProcessingModule", "RawData", "DataStore", new FractionalSelectivity(1.0));
		application.addTupleMapping("dataProcessingModule", "RawData", "ResultData", new FractionalSelectivity(1.0));
		application.addTupleMapping("eventHandlerModule", "ResultData", "Response", new FractionalSelectivity(1.0));
		application.addTupleMapping("clientModule", "Response", "Action", new FractionalSelectivity(1.0));
		
		for(int id:idOfEndDevices)
		{
			Map<String,Double>moduleDeadline = new HashMap<String,Double>();
			moduleDeadline.put("dataProcessingModule", getvalue(3.00, 5.00));
			Map<String,Integer>moduleAddMips = new HashMap<String,Integer>();
			moduleAddMips.put("dataProcessingModule", getvalue(0, 500));
			deadlineInfo.put(id, moduleDeadline);
			additionalMipsInfo.put(id,moduleAddMips);
			
			
			moduleDeadline.put("eventHandlerModule", getvalue(3.00, 5.00));
			moduleAddMips.put("eventHandlerModule", getvalue(0, 500));
			deadlineInfo.put(id, moduleDeadline);
			additionalMipsInfo.put(id,moduleAddMips);
			
		}
		
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("clientModule");add("dataProcessingModule");add("eventHandlerModule");add("displayActuator");}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
		application.setLoops(loops);
		application.setDeadlineInfo(deadlineInfo);
		application.setAdditionalMipsInfo(additionalMipsInfo);
		
		System.out.println(deadlineInfo);System.out.println(additionalMipsInfo);
		
		return application;
	}
	
	@SuppressWarnings({"serial" })
	private static MyApplication createApplication2(String appId, int userId){
		
		MyApplication application = MyApplication.createApplication(appId, userId); 
		application.addAppModule("clientModule",10, 1000, 1000, 100); 
		application.addAppModule("dataProcessingModule", 50, 1500, 4000, 800);
		application.addAppModule("eventHandlerModule", 10, 50, 500, 50);
		application.addAppModule("storageModule", 10, 50, 12000, 100); 
		
		application.addAppEdge("IoTSensor", "clientModule", 100, 200, "IoTSensor", Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge("clientModule", "dataProcessingModule", 6000, 600  , "RawData", Tuple.UP, AppEdge.MODULE); 
		application.addAppEdge("dataProcessingModule", "eventHandlerModule", 100, 50, "ResultData", Tuple.UP, AppEdge.MODULE); 
		application.addAppEdge("dataProcessingModule", "storageModule", 1000, 300, "StoreData", Tuple.UP, AppEdge.MODULE); 
		application.addAppEdge("eventHandlerModule", "clientModule", 100, 50, "Response", Tuple.DOWN, AppEdge.MODULE);
		application.addAppEdge("clientModule", "IoTActuator", 100, 50, "Action", Tuple.DOWN, AppEdge.ACTUATOR); 
		
//		application.addAppEdge("healthSensor", "clientModule", 100, 200, "healthSensor", Tuple.UP, AppEdge.SENSOR);
//		application.addAppEdge("clientModule", "dataProcessingModule", 2000, 2000, "RawData", Tuple.UP, AppEdge.MODULE);
//		application.addAppEdge("dataProcessingModule", "storageModule", 500, 300, "DataStore", Tuple.UP, AppEdge.MODULE); 
//		application.addAppEdge("dataProcessingModule", "eventHandlerModule", 1000, 100, "ResultData", Tuple.UP, AppEdge.MODULE);
//		application.addAppEdge("eventHandlerModule", "clientModule", 100, 50, "Response", Tuple.DOWN, AppEdge.MODULE);
//		application.addAppEdge("clientModule", "displayActuator", 100, 50, "Action", Tuple.DOWN, AppEdge.ACTUATOR);
		
		
		application.addTupleMapping("clientModule", "IoTSensor", "RawData", new FractionalSelectivity(1.0)); 
		application.addTupleMapping("dataProcessingModule", "RawData", "StoreData", new FractionalSelectivity(1.0)); 
		application.addTupleMapping("dataProcessingModule", "RawData", "ResultData", new FractionalSelectivity(1.0)); 
		application.addTupleMapping("eventHandlerModule", "ResultData", "Response", new FractionalSelectivity(1.0)); 
		application.addTupleMapping("clientModule", "Response", "Action", new FractionalSelectivity(1.0));
	
//		application.addTupleMapping("clientModule", "healthSensor", "RawData", new FractionalSelectivity(1.0));
//		application.addTupleMapping("dataProcessingModule", "RawData", "DataStore", new FractionalSelectivity(1.0));
//		application.addTupleMapping("dataProcessingModule", "RawData", "ResultData", new FractionalSelectivity(1.0));
//		application.addTupleMapping("eventHandlerModule", "ResultData", "Response", new FractionalSelectivity(1.0));
//		application.addTupleMapping("clientModule", "Response", "Action", new FractionalSelectivity(1.0));
//		
		
		for(int id:idOfEndDevices)
		{
			Map<String,Double>moduleDeadline = new HashMap<String,Double>();
			Map<String,Integer>moduleAddMips = new HashMap<String,Integer>();
			
			moduleDeadline.put("dataProcessingModule", getvalue(3.00, 5.00));
			moduleAddMips.put("dataProcessingModule", getvalue(0, 500));
			deadlineInfo.put(id, moduleDeadline);
			additionalMipsInfo.put(id,moduleAddMips);
			
		}
		
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("IoTSensor");add("clientModule");add("dataProcessingModule");add("eventHandlerModule");add("clientModule");add("IoTActuator");}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
		application.setLoops(loops);
		application.setDeadlineInfo(deadlineInfo);
		application.setAdditionalMipsInfo(additionalMipsInfo);
		
		return application;
	}	
}