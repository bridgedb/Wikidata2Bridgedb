/**
Copyright 2020-2023 Martina Kutmon
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.bridgedb.DataSource;
import org.bridgedb.IDMapperException;
import org.bridgedb.Xref;
import org.bridgedb.bio.DataSourceTxt;
import org.bridgedb.rdb.construct.GdbConstruct;
import org.bridgedb.wikidata.utils.Utils;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryResults;
import org.eclipse.rdf4j.query.TupleQuery;

/**
 * Retrieves complex identifier mappings
 * (Wikidata, Complex Portal, Reactome) 
 * and stores them in BridgeDb Derby database file
 * 
 * @author mkutmon
 * @author egonw
 */
public class ComplexIdentifiers {


	private static DataSource dsWikidata;
	private static DataSource dsComplexPortal;
	private static DataSource dsReactome;
	private static GdbConstruct newDb;

	/**
	 * @param args
	 * @throws IOException
	 * @throws IDMapperException
	 * @throws SQLException
	 */
	public static void main(String[] args) throws IOException, IDMapperException, SQLException {
		System.out.println("[INFO]: Initial setup");
		setupDatasources();
		Properties props = new Properties();
		props.load(new FileInputStream(new File("properties/complex.props")));
		String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

		// create output directy and database with date in file name
		System.out.println("[INFO]: Start database creation for " + props.getProperty("output.file"));
		File outputDir = new File("output",dateStr);
		outputDir.mkdir();
		File outputFile = new File(outputDir, props.getProperty("output.file") + "-" + dateStr + ".bridge");
		newDb = Utils.createDb(outputFile, "Complexes", props.getProperty("data.type"), "1.0.0");		
		
		// connect to wikidata
		System.out.println("[INFO]: Connect to Wikidata");
		TupleQuery tupleQuery = Utils.connect2Wikidata("complexes.rq");

		// start filling database
		System.out.println("[INFO]: Start filling BridgeDb database");
		fillDb(tupleQuery);
		
		// write database
		newDb.finalize();
		System.out.println("[INFO]: Database finished: " + outputFile.getName() + " (" + outputFile.getTotalSpace() + ")");
		
		
		if(props.getProperty("old.db") != null && !props.getProperty("old.db").equals("")) {
			System.out.println("[INFO]: Quality control and comparison with previous version\n");
			Utils.runQC(props.getProperty("old.db"), outputFile);
		}
	}
	
	private static void fillDb(TupleQuery tupleQuery) throws IDMapperException {
		Map<Xref, Set<Xref>> map = new HashMap<Xref, Set<Xref>>();
		for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
			String wikidata = bs.getBinding("wikidata").getValue().stringValue();
			Xref x = new Xref(wikidata, dsWikidata);
			map.put(x, new HashSet<Xref>());
			if(bs.getBindingNames().contains("cpx")) {
				String ncbi = bs.getBinding("cpx").getValue().stringValue();
				map.get(x).add(new Xref(ncbi, dsComplexPortal));
			}
			if(bs.getBindingNames().contains("reactome")) {
				String ncbi = bs.getBinding("reactome").getValue().stringValue();
				map.get(x).add(new Xref(ncbi, dsReactome));
			}
		}
		addEntries(map);
	}

	private static void setupDatasources() {
		DataSourceTxt.init();
		dsWikidata = DataSource.getExistingBySystemCode("Wd");
		dsReactome = DataSource.getExistingBySystemCode("Re");
		dsComplexPortal = DataSource.register("Cpx", "Complex Portal").asDataSource();
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
			newDb.commit();
		}
	}
}
