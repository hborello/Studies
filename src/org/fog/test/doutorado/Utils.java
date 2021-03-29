package org.fog.test.doutorado;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.MyActuator;
import org.fog.entities.MyFogDevice;
import org.fog.entities.MySensor;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class Utils {

//	arquivo para gravar a topologia em json

	public static MyFogDevice createFogDevice(String nodeName, long mips, int ram, long upBw, long downBw, int level,
			double ratePerMips, double busyPower, double idlePower) {
		List<Pe> peList = new ArrayList<Pe>();
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));
		int hostId = FogUtils.generateEntityId();
		long storage = 1000000;
		int bw = 10000;

		PowerHost host = new PowerHost(hostId, new RamProvisionerSimple(ram), new BwProvisionerOverbooking(bw), storage,
				peList, new StreamOperatorScheduler(peList), new FogLinearPowerModel(busyPower, idlePower));
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
		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(arch, os, vmm, host, time_zone, cost,
				costPerMem, costPerStorage, costPerBw);

		MyFogDevice fogdevice = null;
		try {
			fogdevice = new MyFogDevice(nodeName, characteristics, new AppModuleAllocationPolicy(hostList), storageList,
					10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}

		fogdevice.setLevel(level);
		fogdevice.setMips((int) mips);
		return fogdevice;
	}

	public static void printTopology(List<MyFogDevice> fogDevices, List<MySensor> sensors, List<MyActuator> actuators) {
		System.out.println("===========================================");
		System.out.println("            fog nodes  ");
		System.out.println("===========================================");

		System.out.println("Level	Id	ParentId	Name");
		for (MyFogDevice dv : fogDevices) {
			System.out.println(dv.getLevel() + "	" + dv.getId() + "	" + dv.getParentId() + "		"
					+ dv.getName());
		}

		System.out.println("===========================================");

		System.out.println("===========================================");
		System.out.println("            sensors  ");
		System.out.println("===========================================");

		System.out.println("Id	GDId	Name	ParentName");
		for (MySensor dv : sensors) {
			System.out.println(dv.getId() + "	" + dv.getGatewayDeviceId() + "	"
					+ dv.getName()+ "	" + getNameFD(dv.getGatewayDeviceId(), fogDevices));
		}

		System.out.println("===========================================");
		
		System.out.println("===========================================");
		System.out.println("            actuators  ");
		System.out.println("===========================================");

		System.out.println("Id	GDId	Name	ParentName");
		for (MyActuator dv : actuators) {
			System.out.println(dv.getId() + "	" + dv.getGatewayDeviceId() + "	"
					+ dv.getName()+ "	" + getNameFD(dv.getGatewayDeviceId(), fogDevices));
		}

		System.out.println("===========================================");

	}

	@SuppressWarnings("unchecked")
	public static void topologyToJson(List<MyFogDevice> fogDevices, List<MySensor> sensors, List<MyActuator> actuators) {
		
		JSONObject obj = new JSONObject();
		JSONArray nodes = new JSONArray();
		JSONArray links = new JSONArray();
		
        
		for (MyFogDevice dv : fogDevices) {
			
			JSONObject node = new JSONObject();
			JSONObject link = new JSONObject();
			
			node.put("ratePerMips", dv.getRatePerMips());
			node.put("downBw", (int)dv.getDownlinkBandwidth());
			node.put("level", dv.getLevel());
			node.put("upBw", (int)dv.getUplinkBandwidth());
			node.put("ram", dv.getHost().getRam());
			node.put("name", dv.getName());
			node.put("mips", dv.getMips());
			node.put("type", "FOG_DEVICE");
			nodes.add(node);
			
//			System.out.println(getNameFD(dv.getId(), fogDevices));
			
			if (!getNameFD(dv.getParentId(), fogDevices).equals("")) {
				link.put("source", dv.getName());
				link.put("destination", getNameFD(dv.getParentId(), fogDevices));
				link.put("latency", 10.0);

				links.add(link);
			}
			
		}
		
//		System.out.println(nodes.toString());
		
		for (MySensor s: sensors) {
			JSONObject sensor = new JSONObject();
			JSONObject link = new JSONObject();
			
			sensor.put("sensorType", s.getTupleType());
			sensor.put("name", s.getName());
			sensor.put("value", 7.0);
			sensor.put("type", "SENSOR");
			sensor.put("distribution", 0);
			
//			System.out.println(sensor.toString());
			nodes.add(sensor);
			
			link.put("source", s.getName());
			link.put("destination", getNameFD(s.getGatewayDeviceId(), fogDevices));
			link.put("latency", 2.0);
			
			links.add(link);
		}
		
		for (MyActuator a: actuators) {
			JSONObject actuator = new JSONObject();
			JSONObject link = new JSONObject();
			
			actuator.put("name", a.getName());
			actuator.put("actuatorType", a.getMyActuatorType());
			actuator.put("type", "ACTUATOR");
			
//			System.out.println(actuator.toString());
			nodes.add(actuator);
			
			link.put("source", getNameFD(a.getGatewayDeviceId(), fogDevices));
			link.put("destination", a.getName());
			link.put("latency", 1.0);
			
//			System.out.println(link.toString());
			
			links.add(link);
		}

		obj.put("nodes", nodes);
		obj.put("links", links);
		
		System.out.println("Printing topology in JSON ....");
		System.out.println(obj.toString());
		System.out.println();
		
	}
	
	private static String getNameFD(int fogId, List<MyFogDevice> fogDevices) {
		String name = "";
		
		for (MyFogDevice dv : fogDevices) {
			if (dv.getId() == fogId)
				name = dv.getName();
		}
		
		return name;
	}

}
