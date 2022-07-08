/**
Copyright 2020-2022 Martina Kutmon
                    Egon Willighagen

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 **/
package org.bridgedb.wikidata;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bridgedb.DataSource;
import org.bridgedb.IDMapperException;
import org.bridgedb.Xref;
import org.bridgedb.bio.DataSourceTxt;
import org.bridgedb.rdb.construct.DBConnector;
import org.bridgedb.rdb.construct.DataDerby;
import org.bridgedb.rdb.construct.GdbConstruct;
import org.bridgedb.rdb.construct.GdbConstructImpl3;
import org.bridgedb.tools.qc.BridgeQC;

/**
 * Retrieves identifiers for nanomaterials from Wikidata.
 * 
 * @author mkutmon
 * @author egonw
 */
public class Nanomaterials {

	private static DataSource dsWikiData;
	private static DataSource dsJRCNM;
	private static DataSource dsENM;
	private static GdbConstruct newDb;
	
	public static void main(String[] args) throws IOException, IDMapperException, SQLException {
		setupDatasources();
		File outputDir = new File("output");
		outputDir.mkdir();
		File outputFile = new File(outputDir, "nanomaterials.bridge");
		createDb(outputFile);
		File releasedDb = new File(outputDir, "nanomaterials_20220708.bridge");
		
		BufferedReader file = new BufferedReader(new FileReader("nanomaterials.tsv"));
        String dataRow = file.readLine(); // skip the first line
        dataRow = file.readLine();

		Map<Xref, Set<Xref>> map = new HashMap<Xref, Set<Xref>>();
		int counter = 0;
		int counter2 = 0;
		boolean finished = false;
        while (dataRow != null && !finished) {
        	String[] fields = dataRow.split("\\t");
        	String wikidata = fields[0].replaceAll("\"", "");
			Xref wdid = new Xref(wikidata, dsWikiData);
			map.put(wdid, new HashSet<Xref>());
			
			if (fields.length > 1) {
				String doi = fields[1].replaceAll("\"", "");
				Xref doiRef = new Xref(doi, dsJRCNM);
				map.get(wdid).add(doiRef);
				if (fields.length > 2) {
					String pmid = fields[2].replaceAll("\"", "");
					Xref pmidRef = new Xref(pmid, dsENM);
					map.get(wdid).add(pmidRef);
					System.out.println(wdid);
					System.out.println(doiRef);
					System.out.println(pmidRef);
				}
			}
			dataRow = file.readLine();
			counter++;
			if (counter == 5000) {
				counter2++;
				System.out.println("5k mark " + counter2 + ": " + wdid);
				counter = 0;
				addEntries(map);
				map.clear();
				// finished = true;
			}
		}
		addEntries(map);
		newDb.finalize();
		file.close();
		System.out.println("[INFO]: Database finished.");
		runQC(releasedDb, outputFile);
	}
	
	private static void createDb(File outputFile) throws IDMapperException {
		newDb = new GdbConstructImpl3(outputFile.getAbsolutePath(),new DataDerby(), DBConnector.PROP_RECREATE);
		newDb.createGdbTables();
		newDb.preInsert();
		
		String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
		newDb.setInfo("BUILDDATE", dateStr);
		newDb.setInfo("DATASOURCENAME", "Wikidata");
		newDb.setInfo("DATASOURCEVERSION", "1.0.0");
		newDb.setInfo("BRIDGEDBVERSION", "3.0.15");
		newDb.setInfo("SERIES", "nanomaterials");
		newDb.setInfo("DATATYPE", "Article");	
	}
	
	private static void setupDatasources() {
		DataSourceTxt.init();
		dsWikiData = DataSource.getExistingBySystemCode("Wd");
		dsJRCNM = DataSource.register("Nmj", "JRC representative industrial materials").asDataSource();
		dsENM = DataSource.register("ENM", "eNanoMapper Ontology").asDataSource();
	}

	private static String readQuery(String path) throws IOException {
		String content = readFile(path, StandardCharsets.UTF_8);
		return content;
	}

	private static String readFile(String path, Charset encoding) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, encoding);
	}

	private static void addEntries(Map<Xref, Set<Xref>> dbEntries) throws IDMapperException {
		Set<Xref> addedXrefs = new HashSet<Xref>();
		for (Xref ref : dbEntries.keySet()) {
			Xref mainXref = ref;
			if (addedXrefs.add(mainXref)) newDb.addGene(mainXref);
			newDb.addLink(mainXref, mainXref);

			for (Xref rightXref : dbEntries.get(mainXref)) {
				if (!rightXref.equals(mainXref) && rightXref != null) {
					if (addedXrefs.add(rightXref)) newDb.addGene(rightXref);
					newDb.addLink(mainXref, rightXref);
				}
			}
			// System.out.println("[INFO]: Commit " + mainXref);
			newDb.commit();
		}
	}
	
	private static void runQC(File oldDB, File newDB) throws IDMapperException, SQLException{
		BridgeQC qc = new BridgeQC (oldDB, newDB);
		qc.run();
	}
}
