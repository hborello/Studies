package org.fog.test.doutorado;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.fog.entities.MyFogDevice;
import org.fog.entities.MySensor;
import org.fog.entities.Tuple;
import org.fog.placement.ModuleMapping;
import org.fog.placement.MyController;
import org.fog.placement.MyModulePlacement;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.MyApplication;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.MyActuator;

public class Application02 {

//	Declaracao das estruturas de listas de dispositivos, sensores e atuadores
	static List<MyFogDevice> fogDevices = new ArrayList<MyFogDevice>();
	static List<MySensor> sensors = new ArrayList<MySensor>();
	static List<MyActuator> actuators = new ArrayList<MyActuator>();
	
//	buscar saber o que faz
	static Map<Integer, Map<String, Double>> deadlineInfo = new HashMap<Integer, Map<String, Double>>();
	static Map<Integer, Map<String, Integer>> additionalMipsInfo = new HashMap<Integer, Map<String, Integer>>();

	static Map<Integer, MyFogDevice> deviceById = new HashMap<Integer, MyFogDevice>();
	static List<Integer> idOfEndDevices = new ArrayList<Integer>();

//	Referencia para o posicionamento dos operadores da aplicacao
	static boolean CLOUD = false;

	static double sensingInterval = 5;

//	Numero de dispositivos da topologia da Fog
	static int numFMDevPerGW = 2; // Fog Middle
	static int numEndDevPerFM = 2; // Dispositivos finais (ex.: smartphones)

//	Latencias
	static int ltLevel_0_1 = 50;
	static int ltLevel_1_2 = 25;
	static int ltLevel_2_3 = 5;
	static double ltLevel_sensor = 2;
	static double ltLevel_actuator = 1;

	public static void main(String[] args) {

		Log.printLine("Starting topology and application ...");

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
			Utils.printTopology(fogDevices, sensors, actuators);

			MyApplication application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
			moduleMapping.addModuleToDevice("storageModule", "cloud");
			
			for(int i=0;i<idOfEndDevices.size();i++)
			{
				MyFogDevice fogDevice = deviceById.get(idOfEndDevices.get(i));
				moduleMapping.addModuleToDevice("clientModule", fogDevice.getName()); 
			}
			
			MyController controller = new MyController("master-controller", fogDevices, sensors, actuators);
			controller.submitApplication(application, 0, new MyModulePlacement(fogDevices, sensors, actuators, application, moduleMapping,"mainModule"));
			
			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
			
			CloudSim.startSimulation();

			CloudSim.stopSimulation();
			
			Log.printLine("TestApplication finished!");

		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}

	}

//	metodo para criar cloud node e chamar matodo para criar fog gateway
	private static void createFogDevices(int userId, String appId) {
		MyFogDevice cloud = Utils.createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16 * 103, 16 * 83.25);
		cloud.setParentId(-1);
//		MyFogDevice proxy = Utils.createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333); // creates the fog device Proxy Server (level=1)
//		proxy.setParentId(cloud.getId()); // setting Cloud as parent of the Proxy Server
//		proxy.setUplinkLatency(100); // latency of connection from Proxy Server to the Cloud is 100 ms
		
		fogDevices.add(cloud);
//		fogDevices.add(proxy);
		
		deviceById.put(cloud.getId(), cloud);
//		deviceById.put(proxy.getId(), proxy);

		for (int i = 0; i < numFMDevPerGW; i++) {
			addFM(i + "", userId, appId, cloud.getId());
		}

	}

//	metodo para criar fog gateway e chamar matodo para criar fog middle
//	private static void addGw(String gwPartialName, int userId, String appId, int parentId) {
//		MyFogDevice gw = Utils.createFogDevice("gw-" + gwPartialName, 2800, 4000, 10000, 10000, 1, 0.0, 107.339,
//				83.4333);
//		fogDevices.add(gw);
//		deviceById.put(gw.getId(), gw);
//		gw.setParentId(parentId);
//		gw.setUplinkLatency(ltLevel_1_2);
//		
//		for (int i = 0; i < numFMDevPerGW; i++) {
//			addFM(gwPartialName+"-"+i, userId, appId, gw.getId());
//		}
//
//	}

//	metodo para criar fog middle e chamar matodo para criar end devices
	private static void addFM(String fmPartialName, int userId, String appId, int parentId) {
		MyFogDevice fm = Utils.createFogDevice("fm-" + fmPartialName, 2800, 4000, 10000, 10000, 2, 0.0, 107.339,
				83.4333);
		fogDevices.add(fm);
		deviceById.put(fm.getId(), fm);
		fm.setParentId(parentId);
		fm.setUplinkLatency(ltLevel_0_1);

		for (int i = 0; i < numEndDevPerFM; i++) {
			String endPartialName = fmPartialName + "-" + i;
			MyFogDevice end = addEnd(endPartialName, userId, appId, fm.getId());
			end.setUplinkLatency(ltLevel_2_3);
			fogDevices.add(end);
			deviceById.put(end.getId(), end);
		}
		
	}

//	metodo para criar end devices, sensores e atuadores (fixos em um sensor e um atuador por end device)
	private static MyFogDevice addEnd(String endPartialName, int userId, String appId, int parentId) {
		MyFogDevice end = Utils.createFogDevice("end-" + endPartialName, 3200, 1000, 10000, 270, 3, 0, 87.53, 82.44);
		end.setParentId(parentId);
		idOfEndDevices.add(end.getId());
		MySensor sensor = new MySensor("s-" + endPartialName, "IoTSensor", userId, appId,
				new DeterministicDistribution(sensingInterval)); // inter-transmission time of EEG sensor follows a
																	// deterministic distribution
		sensors.add(sensor);
		MyActuator actuator = new MyActuator("a-" + endPartialName, userId, appId, "IoTActuator");
		actuators.add(actuator);
		sensor.setGatewayDeviceId(end.getId());
		sensor.setLatency(ltLevel_sensor); // latency of connection between EEG sensors and the parent Smartphone is 6
											// ms
		actuator.setGatewayDeviceId(end.getId());
		actuator.setLatency(ltLevel_actuator); // latency of connection between Display actuator and the parent
												// Smartphone is 1 ms
		return end;
	}

	
	@SuppressWarnings({"serial" })
	private static MyApplication createApplication(String appId, int userId){
		
		MyApplication application = MyApplication.createApplication(appId, userId); 
		application.addAppModule("clientModule",10, 1000, 1000, 100); 
		application.addAppModule("mainModule", 50, 1500, 4000, 800); 
		application.addAppModule("storageModule", 10, 50, 12000, 100); 
		
		application.addAppEdge("IoTSensor", "clientModule", 100, 200, "IoTSensor", Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge("clientModule", "mainModule", 6000, 600  , "RawData", Tuple.UP, AppEdge.MODULE); 
		application.addAppEdge("mainModule", "storageModule", 1000, 300, "StoreData", Tuple.UP, AppEdge.MODULE); 
		application.addAppEdge("mainModule", "clientModule", 100, 50, "ResultData", Tuple.DOWN, AppEdge.MODULE); 
		application.addAppEdge("clientModule", "IoTActuator", 100, 50, "Response", Tuple.DOWN, AppEdge.ACTUATOR); 
		
		application.addTupleMapping("clientModule", "IoTSensor", "RawData", new FractionalSelectivity(1.0)); 
		application.addTupleMapping("mainModule", "RawData", "ResultData", new FractionalSelectivity(1.0)); 
		application.addTupleMapping("mainModule", "RawData", "StoreData", new FractionalSelectivity(1.0)); 
		application.addTupleMapping("clientModule", "ResultData", "Response", new FractionalSelectivity(1.0)); 
	
		
		for(int id:idOfEndDevices)
		{
			Map<String,Double>moduleDeadline = new HashMap<String,Double>();
			moduleDeadline.put("mainModule", getvalue(3.00, 5.00));
			Map<String,Integer>moduleAddMips = new HashMap<String,Integer>();
			moduleAddMips.put("mainModule", getvalue(0, 500));
			deadlineInfo.put(id, moduleDeadline);
			additionalMipsInfo.put(id,moduleAddMips);
			
		}
		
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("IoTSensor");add("clientModule");add("mainModule");add("clientModule");add("IoTActuator");}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
		application.setLoops(loops);
		application.setDeadlineInfo(deadlineInfo);
		application.setAdditionalMipsInfo(additionalMipsInfo);
		
		return application;
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
}
