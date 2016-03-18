/*
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.1.1.
 */

import java.io.*;
import java.util.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * QryEval is a simple application that reads queries from a file,
 * evaluates them against an index, and writes the results to an
 * output file.  This class contains the main method, a method for
 * reading parameter and query files, initialization methods, a simple
 * query parser, a simple query processor, and methods for reporting
 * results.
 * <p>
 * This software illustrates the architecture for the portion of a
 * search engine that evaluates queries.  It is a guide for class
 * homework assignments, so it emphasizes simplicity over efficiency.
 * Everything could be done more efficiently and elegantly.
 * <p>
 * The {@link Qry} hierarchy implements query evaluation using a
 * 'document at a time' (DaaT) methodology.  Initially it contains an
 * #OR operator for the unranked Boolean retrieval model and a #SYN
 * (synonym) operator for any retrieval model.  It is easily extended
 * to support additional query operators and retrieval models.  See
 * the {@link Qry} class for details.
 * <p>
 * The {@link RetrievalModel} hierarchy stores parameters and
 * information required by different retrieval models.  Retrieval
 * models that need these parameters (e.g., BM25 and Indri) use them
 * very frequently, so the RetrievalModel class emphasizes fast access.
 * <p>
 * The {@link Idx} hierarchy provides access to information in the
 * Lucene index.  It is intended to be simpler than accessing the
 * Lucene index directly.
 * <p>
 * As the search engine becomes more complex, it becomes useful to
 * have a standard approach to representing documents and scores.
 * The {@link ScoreList} class provides this capability.
 */
public class QryEval {

	//  --------------- Constants and variables ---------------------

	private static final String USAGE =
			"Usage:  java QryEval paramFile\n\n";

	private static final EnglishAnalyzerConfigurable ANALYZER =
			new EnglishAnalyzerConfigurable(Version.LUCENE_43);
	private static final String[] TEXT_FIELDS =
		{ "body", "title", "url", "inlink" };

	//  --------------- Methods ---------------------------------------

	/**
	 * @param args The only argument is the parameter file name.
	 * @throws Exception Error accessing the Lucene index.
	 */
	public static void main(String[] args) throws Exception {

		//  This is a timer that you may find useful.  It is used here to
		//  time how long the entire program takes, but you can move it
		//  around to time specific parts of your code.

		Timer timer = new Timer();
		timer.start ();

		//  Check that a parameter file is included, and that the required
		//  parameters are present.  Just store the parameters.  They get
		//  processed later during initialization of different system
		//  components.

		if (args.length < 1) {
			throw new IllegalArgumentException (USAGE);
		}

		Map<String, String> parameters = readParameterFile (args[0]);

		//  Configure query lexical processing to match index lexical
		//  processing.  Initialize the index and retrieval model.

		ANALYZER.setLowercase(true);
		ANALYZER.setStopwordRemoval(true);
		ANALYZER.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);

		Idx.initialize (parameters.get ("indexPath"));
		RetrievalModel model = initializeRetrievalModel (parameters);

		//  Perform experiments.

		processQueryFile(parameters.get("queryFilePath"), 
				parameters.get("trecEvalOutputPath"), model);

		//  Clean up.

		timer.stop ();
		System.out.println ("Time:  " + timer);
	}

	/**
	 * Allocate the retrieval model and initialize it using parameters
	 * from the parameter file.
	 * @return The initialized retrieval model
	 * @throws IOException Error accessing the Lucene index.
	 */
	private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
			throws IOException {

		RetrievalModel model = null;
		String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();

		if (modelString.equals("unrankedboolean")) {
			model = new RetrievalModelUnrankedBoolean();
		} else if (modelString.equals("rankedboolean")) {
			model = new RetrievalModelRankedBoolean();
		} else if (modelString.equals("bm25")) {
			double k_1 = Double.parseDouble(parameters.get("BM25:k_1"));
			double b = Double.parseDouble(parameters.get("BM25:b"));
			double k_3 = Double.parseDouble(parameters.get("BM25:k_3"));
			model = new RetrievalModelBM25(k_1, b, k_3);
		} else if (modelString.equals("indri")) {
			double mu = Double.parseDouble(parameters.get("Indri:mu"));
			double lambda = Double.parseDouble(parameters.get("Indri:lambda"));
			//different settings based on if query expansion is needed
			if(parameters.containsKey("fb") && parameters.get("fb").equals("true")) {
				boolean fb = true;
				int	fbDocs = Integer.parseInt(parameters.get("fbDocs"));
				int	fbTerms = Integer.parseInt(parameters.get("fbTerms"));
				int	fbMu = Integer.parseInt(parameters.get("fbMu"));
				double fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
				String fbExpansionQueryFile = parameters.get("fbExpansionQueryFile");
				String fbInitialRankingFile = parameters.get("fbInitialRankingFile");
				model = new RetrievalModelIndri(mu, lambda, fb, fbDocs, 
						fbTerms, fbMu, fbOrigWeight, fbExpansionQueryFile, 
						fbInitialRankingFile);
			} else {
				model = new RetrievalModelIndri(mu, lambda);
			}
		} else {
			throw new IllegalArgumentException
			("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
		}

		return model;
	}

	/**
	 * Optimize the query by removing degenerate nodes produced during
	 * query parsing, for example '#NEAR/1 (of the)' which turns into 
	 * '#NEAR/1 ()' after stopwords are removed; and unnecessary nodes
	 * or subtrees, such as #AND (#AND (a)), which can be replaced by 'a'.
	 */
	static Qry optimizeQuery(Qry q) {

		//  Term operators don't benefit from optimization.

		if (q instanceof QryIopTerm) {
			return q;
		}

		//  Optimization is a depth-first task, so recurse on query
		//  arguments.  This is done in reverse to simplify deleting
		//  query arguments that become null.

		for (int i = q.args.size() - 1; i >= 0; i--) {

			Qry q_i_before = q.args.get(i);
			Qry q_i_after = optimizeQuery (q_i_before);

			if (q_i_after == null) {
				q.removeArg(i);			// optimization deleted the argument
			} else {
				if (q_i_before != q_i_after) {
					q.args.set (i, q_i_after);	// optimization changed the argument
				}
			}
		}

		//  If the operator now has no arguments, it is deleted.

		if (q.args.size () == 0) {
			return null;
		}

		//  Only SCORE operators can have a single argument.  Other
		//  query operators that have just one argument are deleted.

		if ((q.args.size() == 1) &&
				(! (q instanceof QrySopScore))) {
			if(q.getWeight() < 0)
				q = q.args.get (0);
			else { //if the parent operator has weight associated with it
				q.args.get(0).setWeight(q.getWeight());
				q = q.args.get(0);
			}
		}

		return q;

	}

	/**
	 * Return a query tree that corresponds to the query.
	 * 
	 * @param qString
	 *          A string containing a query.
	 * @param qTree
	 *          A query tree
	 * @throws IOException Error accessing the Lucene index.
	 */
	static Qry parseQuery(String qString, RetrievalModel model) throws IOException {

		//  Add a default query operator to every query. This is a tiny
		//  bit of inefficiency, but it allows other code to assume
		//  that the query will return document ids and scores.

		String defaultOp = model.defaultQrySopName ();
		qString = defaultOp + "(" + qString + ")";

		//  Simple query tokenization.  Terms like "near-death" are handled later.

		StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
		String token = null;

		//  This is a simple, stack-based parser.  These variables record
		//  the parser's state.

		Qry currentOp = null;
		Stack<Qry> opStack = new Stack<Qry>();
		boolean weightExpected = false;
		Stack<Double> weightStack = new Stack<Double>();

		//  Each pass of the loop processes one token. The query operator
		//  on the top of the opStack is also stored in currentOp to
		//  make the code more readable.

		while (tokens.hasMoreTokens()) {

			token = tokens.nextToken();
			
			if (token.matches("[ ,(\t\n\r]")) {
				continue;
			} else if (token.equals(")")) {	// Finish current query op.
				// If the current query operator is not an argument to another
				// query operator (i.e., the opStack is empty when the current
				// query operator is removed), we're done (assuming correct
				// syntax - see below).

				opStack.pop();

				if (opStack.empty())
					break;

				// Not done yet.  Add the current operator as an argument to
				// the higher-level operator, and shift processing back to the
				// higher-level operator.

				Qry arg = currentOp;
				
				//if the parent of arg (the new currentOp) is a weight operation
				//assign the last weight to arg, and expect the next operation to be weight
				//if no more weight on stack, that means the next should be no weight
				
				currentOp = opStack.peek();
				String parentOp = currentOp.getDisplayName();
				if(parentOp.equalsIgnoreCase("#wand") || parentOp.equalsIgnoreCase("#wsum")) {
					arg.setWeight(weightStack.pop());
					weightExpected = true;
				} else 
					weightExpected = false;
				
				currentOp.appendArg(arg);
				
			} else if(weightExpected) { //this token should be weight
				double weight = Double.parseDouble(token);
				weightStack.push(weight);
				weightExpected = false; //the next has to be regular arg
			} else if (token.equalsIgnoreCase("#or")) {
				currentOp = new QrySopOr();
				currentOp.setDisplayName(token);
				opStack.push(currentOp);
				weightExpected = false;
			} else if (token.equalsIgnoreCase("#syn")) {
				currentOp = new QryIopSyn();
				currentOp.setDisplayName(token);
				opStack.push(currentOp);
				weightExpected = false;
			} else if (token.equalsIgnoreCase("#and")) {
				currentOp = new QrySopAnd();
				currentOp.setDisplayName(token);
				opStack.push(currentOp);
				weightExpected = false;
			} else if (token.equalsIgnoreCase("#sum")) {
				currentOp = new QrySopSum();
				currentOp.setDisplayName(token);
				opStack.push(currentOp);
				weightExpected = false;
			} else if (token.toLowerCase().startsWith("#near")) {
				int nearInt = Integer.parseInt(token.split("/")[1]);
				currentOp = new QryIopNear(nearInt);
				currentOp.setDisplayName(token);
				opStack.push(currentOp);
				weightExpected = false;
			} else if (token.toLowerCase().startsWith("#window")) {
				int windowSize = Integer.parseInt(token.split("/")[1]);
				currentOp = new QryIopWindow(windowSize);
				currentOp.setDisplayName(token);
				opStack.push(currentOp);
				weightExpected = false;
			} else if (token.equalsIgnoreCase("#wand")) {
				currentOp = new QrySopWand();
				currentOp.setDisplayName(token);
				opStack.push(currentOp);
				weightExpected = true; //next token should be weight
			} else if (token.equalsIgnoreCase("#wsum")) {
				currentOp = new QrySopWsum();
				currentOp.setDisplayName(token);
				opStack.push(currentOp);
				weightExpected = true; //next token should be weight
			} else {
				//  Split the token into a term and a field.

				int delimiter = token.indexOf('.');
				String field = null;
				String term = null;

				if (delimiter < 0) {
					field = "body";
					term = token;
				} else {
					field = token.substring(delimiter + 1).toLowerCase();
					term = token.substring(0, delimiter);
				}

				if ((field.compareTo("url") != 0) &&
						(field.compareTo("keywords") != 0) &&
						(field.compareTo("title") != 0) &&
						(field.compareTo("body") != 0) &&
						(field.compareTo("inlink") != 0)) {
					throw new IllegalArgumentException ("Error: Unknown field " + token);
				}

				//  Lexical processing, stopwords, stemming.  A loop is used
				//  just in case a term (e.g., "near-death") gets tokenized into
				//  multiple terms (e.g., "near" and "death").

				String t[] = tokenizeQuery(term);
				String parentOp = currentOp.getDisplayName();
				
				for (int j = 0; j < t.length; j++) {
					Qry	termOp = new QryIopTerm(t[j], field);
					if(parentOp.equalsIgnoreCase("#wand") || parentOp.equalsIgnoreCase("#wsum")) 
						termOp.setWeight(weightStack.peek());
					currentOp.appendArg (termOp);
				}
				
				//next arg is expect to be weight again
				if(parentOp.equalsIgnoreCase("#wand") || parentOp.equalsIgnoreCase("#wsum")) { 
					weightStack.pop();
					weightExpected = true;
				}
			}
		}


		//  A broken structured query can leave unprocessed tokens on the opStack,

		if (tokens.hasMoreTokens()) {
			throw new IllegalArgumentException
			("Error:  Query syntax is incorrect.  " + qString);
		}

		return currentOp;
	}

	/**
	 * Print a message indicating the amount of memory used. The caller
	 * can indicate whether garbage collection should be performed,
	 * which slows the program but reduces memory usage.
	 * 
	 * @param gc
	 *          If true, run the garbage collector before reporting.
	 */
	public static void printMemoryUsage(boolean gc) {

		Runtime runtime = Runtime.getRuntime();

		if (gc)
			runtime.gc();

		System.out.println("Memory used:  "
				+ ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
	}

	/**
	 * Process one query.
	 * @param qString A string that contains a query.
	 * @param model The retrieval model determines how matching and scoring is done.
	 * @return Search results
	 * @throws IOException Error accessing the index
	 */
	static ScoreList processQuery(String qString, RetrievalModel model)
			throws IOException {

		Qry q = parseQuery(qString, model);
		q = optimizeQuery (q);

		// Show the query that is evaluated

		System.out.println("    --> " + q);

		if (q != null) {

			ScoreList r = new ScoreList ();

			if (q.args.size () > 0) {		// Ignore empty queries

				q.initialize (model);
				
				while (q.docIteratorHasMatch (model)) {
					int docid = q.docIteratorGetMatch ();
					double score = ((QrySop) q).getScore (model);
					r.add (docid, score);
					q.docIteratorAdvancePast (docid);
				}
			}

			return r;
		} else
			return null;
	}

	/**
	 * Process the query file.
	 * @param queryFilePath
	 * @param model
	 * @throws IOException Error accessing the Lucene index.
	 */
	static void processQueryFile(String queryFilePath, String outputFile,
			RetrievalModel model)
					throws IOException {

		BufferedReader input = null;
		PrintWriter writer = null;
		boolean queryExpansionExpected = false;
		if(model instanceof RetrievalModelIndri)
			queryExpansionExpected = ((RetrievalModelIndri) model).isFb();
		PrintWriter fbWriter = null;

		try {
			String qLine = null;
			
			input = new BufferedReader(new FileReader(queryFilePath));
			writer = new PrintWriter(outputFile, "UTF-8");
			
			Map<String, List<TermVector>> termVectorListMap = null;
			if(queryExpansionExpected) {
				fbWriter = new PrintWriter(((RetrievalModelIndri) model).getFbExpansionQueryFile(), "UTF-8");
				//parse fbInitialRankingFile if specified
				String rankingFileName = ((RetrievalModelIndri) model).getFbInitialRankingFile();
				
				if(rankingFileName != null && rankingFileName.length() > 0)
					termVectorListMap = parseRankingFiles(rankingFileName, 
							(RetrievalModelIndri)model);
			}

			//  Each pass of the loop processes one query.

			while ((qLine = input.readLine()) != null) {
				int d = qLine.indexOf(':');

				if (d < 0) {
					throw new IllegalArgumentException
					("Syntax error:  Missing ':' in query line.");
				}

				printMemoryUsage(false);

				String qid = qLine.substring(0, d);
				String query = qLine.substring(d + 1);

				System.out.println("Query " + qLine);
				
				//if query expansion should be performed
				if(queryExpansionExpected) {
					List<TermVector> termVectorList = null;
					//no fbInitialRankingFile specified
					if(termVectorListMap == null || termVectorListMap.size() == 0) { 
						ScoreList preRanking = processQuery(query, model);
						if(preRanking != null) preRanking.sort();
						
						termVectorList = getTermVectorsFromScores(preRanking, 
								(RetrievalModelIndri)model);
					} else {
						termVectorList = termVectorListMap.get(qid);
					}
					
					//generate and print expanded query
					String newQuery = generateExpandQuery(termVectorList, query, 
							(RetrievalModelIndri)model);
					fbWriter.println(qid + ": " + newQuery);
					
					//combine expanded new query and old query
					double fbOrigWeight = ((RetrievalModelIndri) model).getFbOrigWeight();
					query = "#wand ( " + Double.toString(fbOrigWeight) + 
							" #and ( " + query + " ) " +
							Double.toString(1-fbOrigWeight) + " " + newQuery + " )";
				}

				ScoreList r = null;

				r = processQuery(query, model);
				
				if (r != null) {
					r.sort();
					//printResults(qid, r);
					printResultsToFile(qid, r, writer);
					//System.out.println();
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			input.close();
			writer.close();
			if(fbWriter != null)
				fbWriter.close();
		}
	}
	
	/**
	 * parse the ranking files and return a map of key(queryid) 
	 * and value(a list of termvectors)
	 */
	private static Map<String, List<TermVector>> parseRankingFiles(String rankingFileName, 
			RetrievalModelIndri model) throws IOException {
		
		Map<String, List<TermVector>> result = new HashMap<String, List<TermVector>>();
		BufferedReader input = null;
		
		try {
			input = new BufferedReader(new FileReader(rankingFileName));
			String line = null;
			
			while((line = input.readLine()) != null) {
				String[] strs = line.split(" ");
				
				//make sure each list only contains the top fbDocs
				if(result.containsKey(strs[0]) 
						&& result.get(strs[0]).size() == model.getFbDocs())
					continue;
				
				List<TermVector> list = null;
				if(result.containsKey(strs[0])) {
					list = result.get(strs[0]);
				} else {
					list = new ArrayList<TermVector>();
				}
				
				//based on current docid, create a termvector and 
				//assign the indri score, add it to the list for this query
				int docid = Idx.getInternalDocid(strs[2]);
				TermVector termVector = new TermVector(docid, "body");
				termVector.setIndriScore(Double.parseDouble(strs[4]));
				list.add(termVector);
				result.put(strs[0], list);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			input.close();
		}
		return result;
	}
	
	/**
	 * parse the generated scorelist and return a list of term vectors
	 */
	private static List<TermVector> getTermVectorsFromScores(
			ScoreList preRanking, RetrievalModelIndri model) throws IOException {
		List<TermVector> list = new ArrayList<TermVector>();
		//convert the scorelist to a list of termvector with scores
		for (int i = 0; i < preRanking.size() && i < model.getFbDocs(); i++) {
			int docid = preRanking.getDocid(i);
			TermVector termVector = new TermVector(docid, "body");
			termVector.setIndriScore(preRanking.getDocidScore(i));
			list.add(termVector);
		}
		return list;
	}
	
	/**
	 * using the term vectors for this query to generate expanded query
	 * @throws IOException 
	 */	
	private static String generateExpandQuery(
			List<TermVector> termVectorList, String query, 
			RetrievalModelIndri model) throws IOException {
		int fbTerms = model.getFbTerms();
		int fbMu = model.getFbMu();
		
		Map<String, Double> map = new HashMap<String, Double>();
		//go through each document
		for(TermVector termVector : termVectorList) {
			double p_I_d = termVector.getIndriScore();
			//go through each term in the document and calculate weights
			for(int i = 1; i < termVector.stemsLength(); i++) {
				String term = termVector.stemString(i);
				double tf = termVector.stemFreq(i);
				double p_t_C = ((double)termVector.totalStemFreq(i)) / ((double)Idx.getSumOfFieldLengths("body"));
				double p_t_d = (tf + fbMu * p_t_C) / (termVector.positionsLength() + fbMu);
				double weight = p_I_d * p_t_d * Math.log(1 / p_t_C);
				if(map.containsKey(term)) {
					map.put(term, map.get(term) + weight);
				} else {
					map.put(term, weight);
				}
			}
		}
		
		//sort the map by value (weights) in desc order
		List<Map.Entry<String, Double>> list = 
				new ArrayList<Map.Entry<String, Double>>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> o1,
                    Map.Entry<String, Double> o2) {
                if(o2.getValue() > o1.getValue()) return 1;
                else if(o2.getValue() < o1.getValue()) return -1;
                else return 0;
            }
        });
        
        //append the top fbTerms terms to the final string
        int termCnt = 0;
        StringBuilder sb = new StringBuilder();
        sb.append("#wand ( ");
        for(Map.Entry<String, Double> entry : list) {
        	if(termCnt == fbTerms) break;
        	if(entry.getKey().contains(",") || entry.getKey().contains(".")) continue;
        	sb.append(Double.toString(entry.getValue())).append(" ").append(entry.getKey()).append(" ");
        	termCnt++;
        }
        sb.append(")");
        return sb.toString();
	}

	/**
	 * Print the query results.
	 * 
	 * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
	 * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
	 * 
	 * QueryID Q0 DocID Rank Score RunID
	 * 
	 * @param queryName
	 *          Original query.
	 * @param result
	 *          A list of document ids and scores
	 * @throws IOException Error accessing the Lucene index.
	 */
	static void printResults(String queryName, ScoreList result) throws IOException {

		System.out.println(queryName + ":  ");
		if (result.size() < 1) {
			System.out.println("\tNo results.");
		} else {
			for (int i = 0; i < result.size() && i < 100; i++) {
				System.out.println("\t" + i + ":  " + Idx.getExternalDocid(result.getDocid(i)) + ", "
						+ result.getDocidScore(i));
			}
		}
	}
	
	/**
	 * Print the query results to file with correct format.
	 * THE FORMAT IS:
	 * QueryID Q0 DocID Rank Score RunID
	 * 
	 * @param queryID
	 *          Original query ID.
	 * @param result
	 *          A list of document ids and scores
	 * @throws IOException Error accessing the Lucene index.
	 */
	static void printResultsToFile(String queryID, ScoreList result,
			PrintWriter writer) throws IOException {
		
		if (result.size() < 1) {
			writer.println(queryID + " Q0 dummy 1 0 trun-1");
		} else {
			for (int i = 0; i < result.size() && i < 100; i++) {
				writer.println(queryID + " Q0 " + Idx.getExternalDocid(result.getDocid(i)) + " "
						+ Integer.toString(i+1) + " "
						+ result.getDocidScore(i) + " run-1");
			}
		}
	}

	/**
	 * Read the specified parameter file, and confirm that the required
	 * parameters are present.  The parameters are returned in a
	 * HashMap.  The caller (or its minions) are responsible for
	 * processing them.
	 * @return The parameters, in <key, value> format.
	 */
	private static Map<String, String> readParameterFile (String parameterFileName)
			throws IOException {

		Map<String, String> parameters = new HashMap<String, String>();

		File parameterFile = new File (parameterFileName);

		if (! parameterFile.canRead ()) {
			throw new IllegalArgumentException
			("Can't read " + parameterFileName);
		}

		Scanner scan = new Scanner(parameterFile);
		String line = null;
		do {
			line = scan.nextLine();
			String[] pair = line.split ("=");
			parameters.put(pair[0].trim(), pair[1].trim());
		} while (scan.hasNext());

		scan.close();

		if (! (parameters.containsKey ("indexPath") &&
				parameters.containsKey ("queryFilePath") &&
				parameters.containsKey ("trecEvalOutputPath") &&
				parameters.containsKey ("retrievalAlgorithm"))) {
			throw new IllegalArgumentException
			("Required parameters were missing from the parameter file.");
		}

		return parameters;
	}

	/**
	 * Given a query string, returns the terms one at a time with stopwords
	 * removed and the terms stemmed using the Krovetz stemmer.
	 * 
	 * Use this method to process raw query terms.
	 * 
	 * @param query
	 *          String containing query
	 * @return Array of query tokens
	 * @throws IOException Error accessing the Lucene index.
	 */
	static String[] tokenizeQuery(String query) throws IOException {

		TokenStreamComponents comp =
				ANALYZER.createComponents("dummy", new StringReader(query));
		TokenStream tokenStream = comp.getTokenStream();

		CharTermAttribute charTermAttribute =
				tokenStream.addAttribute(CharTermAttribute.class);
		tokenStream.reset();

		List<String> tokens = new ArrayList<String>();

		while (tokenStream.incrementToken()) {
			String term = charTermAttribute.toString();
			tokens.add(term);
		}

		return tokens.toArray (new String[tokens.size()]);
	}

}
