/**
Copyright 2020 Martina Kutmon

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
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.QueryResults;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;

/**
 * Retrieves human coronavirus gene-protein mappings
 * (Wikidata, NCBI Gene, Refseq, UniProt) 
 * and stores them in BridgeDb derby database file
 * @author mkutmon
 *
 */
public class Wikidata2BridgeDb {

	private static DataSource dsWikiData;
	private static DataSource dsNcbi;
	private static DataSource dsRefseq;
	private static DataSource dsUniprot;
	private static GdbConstruct newDb;
	
	public static void main(String[] args) throws IOException, IDMapperException, SQLException {
		setupDatasources();
		File outputDir = new File("output");
		outputDir.mkdir();
		File outputFile = new File(outputDir, "humancorona-2020-04-14.bridge");
		createDb(outputFile);
		
		File oldDb = new File(outputDir, "humancorona-2020-04-02.bridge");
		
		String query = readQuery("queries/idmapping.rq");
		SPARQLRepository sparqlRepository = new SPARQLRepository("https://query.wikidata.org/sparql");
		RepositoryConnection sparqlConnection = sparqlRepository.getConnection();

		TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);

		Map<Xref, Set<Xref>> map = new HashMap<Xref, Set<Xref>>();
		Map<Xref, String> virusLabel = new HashMap<Xref, String>();
		for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
			String wikidata = bs.getBinding("wikidata").getValue().stringValue();
			String vl = bs.getBinding("virusLabel").getValue().stringValue();
			Xref x = new Xref(wikidata, dsWikiData);
			map.put(x, new HashSet<Xref>());
			virusLabel.put(x,vl);
			if(bs.getBindingNames().contains("ncbi")) {
				String ncbi = bs.getBinding("ncbi").getValue().stringValue();
				map.get(x).add(new Xref(ncbi, dsNcbi));
			}
			if(bs.getBindingNames().contains("refseq")) {
				String refseq = bs.getBinding("refseq").getValue().stringValue();
				map.get(x).add(new Xref(refseq, dsRefseq));
			}
			if(bs.getBindingNames().contains("uniprot")) {
				String uniprot = bs.getBinding("uniprot").getValue().stringValue();
				map.get(x).add(new Xref(uniprot, dsUniprot));
			}
		}
		addEntries(map, virusLabel);
		newDb.finalize();
		System.out.println("[INFO]: Database finished.");
		runQC(oldDb, outputFile);
	}
	
	private static void createDb(File outputFile) throws IDMapperException {
		newDb = new GdbConstructImpl3(outputFile.getAbsolutePath(),new DataDerby(), DBConnector.PROP_RECREATE);
		newDb.createGdbTables();
		newDb.preInsert();
		
		String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
		newDb.setInfo("BUILDDATE", dateStr);
		newDb.setInfo("DATASOURCENAME", "Wikidata");
		newDb.setInfo("DATASOURCEVERSION", "1.0.0");
		newDb.setInfo("SERIES", "humancorona");
		newDb.setInfo("DATATYPE", "GeneProduct");	
	}
	
	private static void setupDatasources() {
		DataSourceTxt.init();
		dsWikiData = DataSource.getExistingBySystemCode("Wd");
		dsNcbi = DataSource.getExistingBySystemCode("L");
		dsRefseq = DataSource.getExistingBySystemCode("Q");
		dsUniprot = DataSource.getExistingBySystemCode("S");
	}

	private static String readQuery(String path) throws IOException {
		String content = readFile(path, StandardCharsets.UTF_8);
		return content;
	}

	private static String readFile(String path, Charset encoding) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, encoding);
	}

	private static void addEntries(Map<Xref, Set<Xref>> dbEntries, Map<Xref, String> virusLabel) throws IDMapperException {
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
					if (addedXrefs.add(rightXref)) newDb.addGene(rightXref);
					newDb.addLink(mainXref, rightXref);
				}
			}
			System.out.println("[INFO]: Commit " + mainXref);
			newDb.commit();
		}
	}
	
	private static void runQC(File oldDB, File newDB) throws IDMapperException, SQLException{
		BridgeQC qc = new BridgeQC (oldDB, newDB);
		qc.run();
	}
}
