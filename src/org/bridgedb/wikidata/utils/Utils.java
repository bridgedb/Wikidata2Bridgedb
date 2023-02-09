package org.bridgedb.wikidata.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.bridgedb.IDMapperException;
import org.bridgedb.rdb.construct.DBConnector;
import org.bridgedb.rdb.construct.DataDerby;
import org.bridgedb.rdb.construct.GdbConstruct;
import org.bridgedb.rdb.construct.GdbConstructImpl3;
import org.bridgedb.tools.qc.BridgeQC;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;

public class Utils {

	public static void runQC(String urlOldDb, File newDb) throws IDMapperException, SQLException, IOException {
		File oldDb = new File("tmp.bridge");
		URL website = new URL(urlOldDb);
		ReadableByteChannel rbc = Channels.newChannel(website.openStream());
		FileOutputStream fos = new FileOutputStream(oldDb);
		fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
		fos.close();
		BridgeQC qc = new BridgeQC(oldDb, newDb);
		qc.run();
		oldDb.delete();
	}
	
	public static GdbConstruct createDb(File outputFile, String name, String dataType, String wikidataVersion) throws IDMapperException {
		GdbConstruct newDb = new GdbConstructImpl3(outputFile.getAbsolutePath(), new DataDerby(), DBConnector.PROP_RECREATE);
		newDb.createGdbTables();
		newDb.preInsert();

		String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
		newDb.setInfo("BUILDDATE", dateStr);
		newDb.setInfo("DATASOURCENAME", "Wikidata");
		newDb.setInfo("DATASOURCEVERSION", wikidataVersion);
		newDb.setInfo("SERIES", name);
		newDb.setInfo("DATATYPE", dataType);	
		return newDb;
	}
	
	public static TupleQuery connect2Wikidata(String rqFile) throws IOException {
		String query = readQuery("queries/"+rqFile);
		
		SPARQLRepository sparqlRepository = new SPARQLRepository("https://query.wikidata.org/sparql");
		RepositoryConnection sparqlConnection = sparqlRepository.getConnection();

		TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query);
		return tupleQuery;
	}
	
	private static String readQuery(String path) throws IOException {
		String content = readFile(path, StandardCharsets.UTF_8);
		return content;
	}

	private static String readFile(String path, Charset encoding) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, encoding);
	}
}


