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

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.gds.catalog.GraphCreateProc;
import org.neo4j.gds.catalog.GraphDropProc;
import org.neo4j.gds.catalog.GraphListProc;
import org.neo4j.gds.compat.GraphDatabaseApiProxy;
import org.neo4j.gds.traverse.TraverseProc;
import org.neo4j.gds.wcc.WccMutateProc;
import org.neo4j.gds.wcc.WccWriteProc;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;

import net.refractions.chyf.streamorder.IGraphDataSource.FlowpathProperty;
import net.refractions.chyf.streamorder.IGraphDataSource.NexusProperty;

public class Neo4JDatastore {
	
	private Label nexusType;
	private RelationshipType flowpathType ;
	
	private Path directory;
	
	private GraphDatabaseService graphDb;
	private DatabaseManagementService managementService;
	
	public Neo4JDatastore(Path tempDir) {
		this.directory = tempDir;
	}
	
	public Neo4JDatastore() throws IOException {
		this(Files.createTempDirectory("chyfstreamorderneo4j"));
	}

	public void init() throws Exception {
		clearDataDirectory();
		Files.createDirectories(directory);
	
		Map<String, String> settings = new HashMap<>();
		settings.put("dbms.security.procedures.unrestricted", "jwt.security.*,gds.*,apoc.*");
		settings.put("dbms.security.procedures.whitelist", "gds.*");
	
		managementService = new DatabaseManagementServiceBuilder(directory)
				.setConfigRaw(settings)
				.build();
	
		graphDb = managementService.database(DEFAULT_DATABASE_NAME);
		
		var procsToRegister = List.of(
				GraphListProc.class, 
				GraphCreateProc.class, 
				GraphDropProc.class,
				TraverseProc.class,
				WccMutateProc.class,
				WccWriteProc.class);
		for (Class<?> procedureClass : procsToRegister) {
			GraphDatabaseApiProxy.registerProcedures(graphDb, procedureClass);
		}
	
		nexusType = Label.label("Nexus");
		flowpathType = RelationshipType.withName("FLOWPATH");
	}


	public GraphDatabaseService getDatabase() {
		return this.graphDb;
	}
	
	public Label getNexusType() {
		return this.nexusType;
	}
	public RelationshipType getFlowpathType() {
		return this.flowpathType;
	}
	
	public void createNexus(Transaction tx, String id, int type ) {
		Node n = tx.createNode(getNexusType());
		n.setProperty(NexusProperty.ID.key, id);
		n.setProperty(NexusProperty.NEXUS_TYPE.key, type);
	}
	
	public void createRelationship(Transaction tx, String from, String to,
			String id, int type, int subtype, double length, int rank, String nameid) {
		
		Node fromNode = tx.findNode(getNexusType(), NexusProperty.ID.key, from);
		Node toNode = tx.findNode(getNexusType(), NexusProperty.ID.key, to);

		Relationship r = fromNode.createRelationshipTo(toNode, getFlowpathType());
		r.setProperty(FlowpathProperty.ID.key, id);
		r.setProperty(FlowpathProperty.EF_TYPE.key, type);
		r.setProperty(FlowpathProperty.EF_SUBTYPE.key, subtype);
		r.setProperty(FlowpathProperty.LENGTH.key, length);
		r.setProperty(FlowpathProperty.RANK.key, rank);
		if (nameid != null) r.setProperty(FlowpathProperty.NAMEID.key, nameid);
	}
	
	public void shutdown() throws IOException {
		managementService.shutdown();
		clearDataDirectory();
	}
	
	private void clearDataDirectory() throws IOException {
		if (!Files.exists(directory)) return;

		Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
