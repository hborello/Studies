package org.fog.test.doutorado;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class AnotherTests {
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
        
		JSONObject obj = new JSONObject();
        obj.put("name", "mkyong.com");
        obj.put("age", 100);

        JSONArray list = new JSONArray();
        list.add("msg 1");
        list.add("msg 2");
        list.add("msg 3");

        obj.put("messages", list);
        
        System.out.print(obj);

	}

}

