/*
 * Copyright 2022 Canadian Wildlife Federation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */
package net.refractions.chyf.streamorder;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.refractions.chyf.streamorder.IGraphDataSource.NexusProperty;

class TestBasicGraph {

	@Test
	void testOrderMainstemComputations() throws Exception{
		try(BasicGraphDataSource dataSource = new BasicGraphDataSource()){
			StreamOrderMainstemEngine computer = new StreamOrderMainstemEngine(false);
			
			computer.computeOrderValues(dataSource);
			
			Map<String,Map<String,Object>> edges = dataSource.getEdgeProperties();
			
			Map<String, Object> sorder = createMap("E-1",1, "E-2", 1, "E-3", 2,	"E-4", 1,
					"E-5", 1,"E-6", 2,"E-7", 3,"E-8", 1,"E-9", 3,"E-10", 1,
					"E-11", 1,"E-12", 2,"E-13", 1,"E-14", 1,"E-15", 2,"E-16", 3,
					"E-17", 4);
					
			for (Entry<String,Object> so : sorder.entrySet()) {
				Assertions.assertEquals((Integer)so.getValue(), (Integer)edges.get(so.getKey()).get(NexusProperty.SORDER.key));
			}
			
			Map<String, Object> uplength = createMap("E-1",0.0, "E-2", 0.0, "E-3", 3.0,	"E-4", 0.0,
					"E-5", 0.0,"E-6", 6.0,"E-7", 14.0,"E-8", 0.0,"E-9", 15.0,"E-10", 0.0,
					"E-11", 0.0,"E-12", 8.0,"E-13", 0.0,"E-14", 0.0,"E-15", 2.0,"E-16", 13.0,
					"E-17", 17.0);
					
			for (Entry<String,Object> so : uplength.entrySet()) {
				Assertions.assertEquals((Double)so.getValue(), (Double)edges.get(so.getKey()).get(NexusProperty.UPSTREAMLENGTH.key));
			}
			
	
			
			String mid = edges.get("E-17").get(NexusProperty.MAINSTEMID.key).toString();
			String[] edges1 = new String[] {"E-9", "E-7", "E-6", "E-5"};
			for (String eid : edges1) {
				Assertions.assertEquals(mid, edges.get(eid).get(NexusProperty.MAINSTEMID.key));
			}
			
			mid = edges.get("E-16").get(NexusProperty.MAINSTEMID.key).toString();
			edges1 = new String[] {"E-12", "E-10"};
			for (String eid : edges1) {
				Assertions.assertEquals(mid, edges.get(eid).get(NexusProperty.MAINSTEMID.key));
			}
			
			mid = edges.get("E-15").get(NexusProperty.MAINSTEMID.key).toString();
			edges1 = new String[] {"E-14"};
			for (String eid : edges1) {
				Assertions.assertEquals(mid, edges.get(eid).get(NexusProperty.MAINSTEMID.key));
			}
			
			mid = edges.get("E-3").get(NexusProperty.MAINSTEMID.key).toString();
			edges1 = new String[] {"E-3"};
			for (String eid : edges1) {
				Assertions.assertEquals(mid, edges.get(eid).get(NexusProperty.MAINSTEMID.key));
			}
			
			
			Map<String, Object> mainseq = createMap("E-1",1, "E-2", 2, "E-3", 1, "E-4", 1,
					"E-5", 5,"E-6", 4,"E-7", 3,"E-8", 1,"E-9", 2,"E-10", 3,
					"E-11", 1,"E-12", 2,"E-13", 1,"E-14", 2,"E-15", 1,"E-16", 1,
					"E-17", 1);
					
			for (Entry<String,Object> so : mainseq.entrySet()) {
				Assertions.assertEquals((Integer)so.getValue(), (Integer)edges.get(so.getKey()).get(NexusProperty.MAINSTEMID_SEQ.key));
			}
			
			Map<String, Object> hack = createMap("E-1",3, "E-2", 2, "E-3", 2, "E-4", 2,
					"E-5", 1,"E-6", 1,"E-7", 1,"E-8", 2,"E-9", 1,"E-10", 2,
					"E-11", 3,"E-12", 2,"E-13", 4,"E-14", 3,"E-15", 3,"E-16", 2,
					"E-17", 1);
					
			for (Entry<String,Object> so : hack.entrySet()) {
				Assertions.assertEquals((Integer)so.getValue(), (Integer)edges.get(so.getKey()).get(NexusProperty.HKORDER.key));
			}
			
			Map<String, Object> horton = createMap("E-1",1, "E-2", 2, "E-3", 2, "E-4", 1,
					"E-5", 4,"E-6", 4,"E-7", 4,"E-8", 1,"E-9", 4,"E-10", 3,
					"E-11", 1,"E-12", 3,"E-13", 1,"E-14", 2,"E-15", 2,"E-16", 3,
					"E-17", 4);
					
			for (Entry<String,Object> so : horton.entrySet()) {
				Assertions.assertEquals((Integer)so.getValue(), (Integer)edges.get(so.getKey()).get(NexusProperty.HTORDER.key));
			}
			
			Map<String, Object> shreveorder = createMap("E-1",1, "E-2", 1, "E-3", 2, "E-4", 1,
					"E-5", 1,"E-6", 2, "E-7", 4,"E-8", 1,"E-9", 5,"E-10", 1,
					"E-11", 1,"E-12", 2,"E-13", 1,"E-14", 1,"E-15", 2,"E-16", 4,
					"E-17", 9);
					
			for (Entry<String,Object> so : shreveorder.entrySet()) {
				Assertions.assertEquals((Integer)so.getValue(), (Integer)edges.get(so.getKey()).get(NexusProperty.SHORDER.key));
			}
		}
	}

	
	@Test
	void testNamedOrderMainstemComputations() throws Exception{
		try(BasicGraphDataSource dataSource = new BasicGraphDataSource()){
			StreamOrderMainstemEngine computer = new StreamOrderMainstemEngine(true);
			
			computer.computeOrderValues(dataSource);
			
			Map<String,Map<String,Object>> edges = dataSource.getEdgeProperties();
			
			Map<String, Object> sorder = createMap("E-1",1, "E-2", 1, "E-3", 2,	"E-4", 1,
					"E-5", 1,"E-6", 2,"E-7", 3,"E-8", 1,"E-9", 3,"E-10", 1,
					"E-11", 1,"E-12", 2,"E-13", 1,"E-14", 1,"E-15", 2,"E-16", 3,
					"E-17", 4);
					
			for (Entry<String,Object> so : sorder.entrySet()) {
				Assertions.assertEquals((Integer)so.getValue(), (Integer)edges.get(so.getKey()).get(NexusProperty.SORDER.key));
			}
			
			Map<String, Object> uplength = createMap("E-1",0.0, "E-2", 0.0, "E-3", 3.0,	"E-4", 0.0,
					"E-5", 0.0,"E-6", 6.0,"E-7", 14.0,"E-8", 0.0,"E-9", 15.0,"E-10", 0.0,
					"E-11", 0.0,"E-12", 8.0,"E-13", 0.0,"E-14", 0.0,"E-15", 2.0,"E-16", 13.0,
					"E-17", 17.0);
					
			for (Entry<String,Object> so : uplength.entrySet()) {
				Assertions.assertEquals((Double)so.getValue(), (Double)edges.get(so.getKey()).get(NexusProperty.UPSTREAMLENGTH.key));
			}
			
	
			
			String mid = edges.get("E-17").get(NexusProperty.MAINSTEMID.key).toString();
			String[] edges1 = new String[] {"E-16", "E-12", "E-11"};
			for (String eid : edges1) {
				Assertions.assertEquals(mid, edges.get(eid).get(NexusProperty.MAINSTEMID.key));
			}
			
			mid = edges.get("E-15").get(NexusProperty.MAINSTEMID.key).toString();
			edges1 = new String[] {"E-14"};
			for (String eid : edges1) {
				Assertions.assertEquals(mid, edges.get(eid).get(NexusProperty.MAINSTEMID.key));
			}
			
			mid = edges.get("E-9").get(NexusProperty.MAINSTEMID.key).toString();
			edges1 = new String[] {"E-7", "E-3", "E-2"};
			for (String eid : edges1) {
				Assertions.assertEquals(mid, edges.get(eid).get(NexusProperty.MAINSTEMID.key));
			}
			
			mid = edges.get("E-6").get(NexusProperty.MAINSTEMID.key).toString();
			edges1 = new String[] {"E-5"};
			for (String eid : edges1) {
				Assertions.assertEquals(mid, edges.get(eid).get(NexusProperty.MAINSTEMID.key));
			}
			
			
			Map<String, Object> mainseq = createMap("E-1",1, "E-2", 4, "E-3", 3, "E-4", 1,
					"E-5", 2,"E-6", 1,"E-7", 2,"E-8", 1,"E-9", 1,"E-10", 1,
					"E-11", 4,"E-12", 3,"E-13", 1,"E-14", 2,"E-15", 1,"E-16", 2,
					"E-17", 1);
					
			for (Entry<String,Object> so : mainseq.entrySet()) {
				Assertions.assertEquals((Integer)so.getValue(), (Integer)edges.get(so.getKey()).get(NexusProperty.MAINSTEMID_SEQ.key));
			}
			
			Map<String, Object> hack = createMap("E-1",3, "E-2", 2, "E-3", 2, "E-4", 4,
					"E-5", 3,"E-6", 3,"E-7", 2,"E-8", 3,"E-9", 2,"E-10", 2,
					"E-11", 1,"E-12", 1,"E-13", 3,"E-14", 2,"E-15", 2,"E-16", 1,
					"E-17", 1);
					
			for (Entry<String,Object> so : hack.entrySet()) {
				Assertions.assertEquals((Integer)so.getValue(), (Integer)edges.get(so.getKey()).get(NexusProperty.HKORDER.key));
			}
			
			Map<String, Object> horton = createMap("E-1",1, "E-2", 3, "E-3", 3, "E-4", 1,
					"E-5", 2,"E-6", 2,"E-7", 3,"E-8", 1,"E-9", 3,"E-10", 1,
					"E-11", 4,"E-12", 4,"E-13", 1,"E-14", 2,"E-15", 2,"E-16", 4,
					"E-17", 4);
					
			for (Entry<String,Object> so : horton.entrySet()) {
				Assertions.assertEquals((Integer)so.getValue(), (Integer)edges.get(so.getKey()).get(NexusProperty.HTORDER.key));
			}
			
			Map<String, Object> shreveorder = createMap("E-1",1, "E-2", 1, "E-3", 2, "E-4", 1,
					"E-5", 1,"E-6", 2, "E-7", 4,"E-8", 1,"E-9", 5,"E-10", 1,
					"E-11", 1,"E-12", 2,"E-13", 1,"E-14", 1,"E-15", 2,"E-16", 4,
					"E-17", 9);
					
			for (Entry<String,Object> so : shreveorder.entrySet()) {
				Assertions.assertEquals((Integer)so.getValue(), (Integer)edges.get(so.getKey()).get(NexusProperty.SHORDER.key));
			}
		}
	}
	
	private HashMap<String, Object> createMap(Object... objects){
		HashMap<String, Object> values = new HashMap<>();
		for (int i = 0; i < objects.length; i +=2 ) {
			values.put(objects[i].toString(), objects[i+1]);
		}
		
		return values;
	}

}
