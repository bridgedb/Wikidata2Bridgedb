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
 * Retrieves human coronavirus gene-protein mappings (Wikidata, NCBI Gene,
 * Refseq, UniProt) and stores them in BridgeDb derby database file
 * 
 * @author mkutmon
 *
 */
public class CoronavirusProteins {

	private static DataSource dsWikiData;
	private static DataSource dsNcbi;
	private static DataSource dsRefseq;
	private static DataSource dsUniprot;
	private static DataSource dsGuideToPharma;
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
		props.load(new FileInputStream(new File("properties/coronavirus-proteins.props")));
		String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

		// create output directy and database with date in file name
		System.out.println("[INFO]: Start database creation for " + props.getProperty("output.file"));
		File outputDir = new File("output",dateStr);
		outputDir.mkdir();
		File outputFile = new File(outputDir, props.getProperty("output.file") + "-" + dateStr + ".bridge");
		newDb = Utils.createDb(outputFile, "Coronavirus", props.getProperty("data.type"), "1.0.0");
		
		// connect to wikidata
		System.out.println("[INFO]: Connect to Wikidata");
		TupleQuery tupleQuery = Utils.connect2Wikidata("coronavirus-proteins.rq");


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
		Map<Xref, String> virusLabel = new HashMap<Xref, String>();
		for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
			String wikidata = bs.getBinding("wikidata").getValue().stringValue();
			String vl = bs.getBinding("virusLabel").getValue().stringValue();
			Xref x = new Xref(wikidata, dsWikiData);
			map.put(x, new HashSet<Xref>());
			virusLabel.put(x,vl);
			if(bs.getBindingNames().contains("ncbi") && bs.getBinding("ncbi") != null) {
				String ncbi = bs.getBinding("ncbi").getValue().stringValue();
				map.get(x).add(new Xref(ncbi, dsNcbi));
			}
			if(bs.getBindingNames().contains("refseq") && bs.getBinding("refseq") != null) {
				String refseq = bs.getBinding("refseq").getValue().stringValue();
				map.get(x).add(new Xref(refseq, dsRefseq));
			}
			if(bs.getBindingNames().contains("uniprot") && bs.getBinding("uniprot") != null) {
				String uniprot = bs.getBinding("uniprot").getValue().stringValue();
				map.get(x).add(new Xref(uniprot, dsUniprot));
			}
			if(bs.getBindingNames().contains("guideToPharma") && bs.getBinding("guideToPharma") != null) {
				String gptarget = bs.getBinding("guideToPharma").getValue().stringValue();
				map.get(x).add(new Xref(gptarget, dsGuideToPharma));
			}
		}
		addEntries(map, virusLabel);
	}

	private static void setupDatasources() {
		DataSourceTxt.init();
		dsWikiData = DataSource.getExistingBySystemCode("Wd");
		dsNcbi = DataSource.getExistingBySystemCode("L");
		dsRefseq = DataSource.getExistingBySystemCode("Q");
		dsUniprot = DataSource.getExistingBySystemCode("S");
		dsGuideToPharma = DataSource.getExistingBySystemCode("Gpt");
	}

	private static void addEntries(Map<Xref, Set<Xref>> dbEntries, Map<Xref, String> virusLabel)
			throws IDMapperException {
		Set<Xref> addedXrefs = new HashSet<Xref>();
		for (Xref ref : dbEntries.keySet()) {
			Xref mainXref = ref;
			if (addedXrefs.add(mainXref)) {
				newDb.addGene(mainXref);
				newDb.addAttribute(mainXref, "virus", virusLabel.get(mainXref));
			}
			newDb.addLink(mainXref, mainXref);

			for (Xref rightXref : dbEntries.get(mainXref)) {
				if (!rightXref.equals(mainXref) && rightXref != null) {
					if (addedXrefs.add(rightXref))
						newDb.addGene(rightXref);
					newDb.addLink(mainXref, rightXref);
				}
			}
			newDb.commit();
		}
	}
}
