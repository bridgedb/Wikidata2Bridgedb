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
 * Retrieves geme identifier mappings
 * (Wikidata, Ensembl)
 * and stores them in BridgeDb Derby database file
 *
 * @author mkutmon
 * @author egonw
 */
public class GeneIdentifiers {

	private static DataSource dsWikidata;
	private static DataSource dsEnsembl;
	private static GdbConstruct newDb;

	public static void main(String[] args) throws IOException, IDMapperException, SQLException {
		setupDatasources();
		File outputDir = new File("output");
		outputDir.mkdir();
		File outputFile = new File(outputDir, "genes.bridge");
		createDb(outputFile);

		String query = readQuery("queries/genes.rq");
		SPARQLRepository sparqlRepository = new SPARQLRepository("https://query.wikidata.org/sparql");
		RepositoryConnection sparqlConnection = sparqlRepository.getConnection();

		TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);

		Map<Xref, Set<Xref>> map = new HashMap<Xref, Set<Xref>>();
		for (BindingSet bs : QueryResults.asList(tupleQuery.evaluate())) {
//			System.out.println(bs);
			String wikidata = bs.getBinding("wikidata").getValue().stringValue();
			Xref x = new Xref(wikidata, dsWikidata);
			map.put(x, new HashSet<Xref>());
			if(bs.getBindingNames().contains("ensembl")) {
				String ncbi = bs.getBinding("ensembl").getValue().stringValue();
				map.get(x).add(new Xref(ncbi, dsEnsembl));
			}
		}
		addEntries(map);
		newDb.finalize();
		System.out.println("[INFO]: Database finished.");
		runQC(outputFile, outputFile);
	}

	private static void createDb(File outputFile) throws IDMapperException {
		newDb = new GdbConstructImpl3(outputFile.getAbsolutePath(),new DataDerby(), DBConnector.PROP_RECREATE);
		newDb.createGdbTables();
		newDb.preInsert();

		String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
		newDb.setInfo("BUILDDATE", dateStr);
		newDb.setInfo("DATASOURCENAME", "Wikidata");
		newDb.setInfo("DATASOURCEVERSION", "1.0.0");
		newDb.setInfo("SERIES", "Homo sapiens genes and proteins");
		newDb.setInfo("DATATYPE", "GeneProduct");
	}

	private static void setupDatasources() {
		DataSourceTxt.init();
		dsWikidata = DataSource.getExistingBySystemCode("Wd");
		dsEnsembl = DataSource.getExistingBySystemCode("En");
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
			System.out.println("[INFO]: Commit " + mainXref);
			newDb.commit();
		}
	}

	private static void runQC(File oldDB, File newDB) throws IDMapperException, SQLException{
		BridgeQC qc = new BridgeQC (oldDB, newDB);
		qc.run();
	}
}
