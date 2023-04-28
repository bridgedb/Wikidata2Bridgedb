# Wikidata2Bridgedb

Creation of human and SARS-related corona virus gene/protein mapping database.

## How to get the data in the BridgeDb files?

The data comes from Wikidata, and can be retrieved by running the SPARQL queries in the [queries](queries) folder
with the [Wikidata Query Service](https://query.wikidata.org/).

```shell
curl -H "Accept: text/tab-separated-values" --data-urlencode query@queries/publications.rq -G https://query.wikidata.org/bigdata/namespace/wdq/sparql -o publications.tsv
```

## How to create a Derby file

```shell
mvn clean install assembly:single
java -cp target/Wikidata2BridgeDb-0.0.2-SNAPSHOT-jar-with-dependencies.jar org.bridgedb.wikidata.Publications
```

## How to cite?

If you use material from this repository, please cite either or both of these:

* Kutmon, Martina, & Willighagen, Egon. (2020). BridgeDb: Human and SARS-related corona virus gene/protein mapping database derived from Wikidata (Version 2020-05-20) [Data set]. Zenodo. http://doi.org/10.5281/zenodo.3835696
* Waagmeester A, Willighagen EL, Su AI, Kutmon M, Labra Gayo JE, Fernández-Álvarez D, et al. (2020). A protocol for adding knowledge to Wikidata, a case report. bioRxiv. http://biorxiv.org/lookup/doi/10.1101/2020.04.05.026336

