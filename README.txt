QryEval is an application built on top of part of Lucene-4.3.0 APIs. It parses, optimizes, 
analyzes queries and retrieve ranked Lucene-indexed documents from ClueWeb09 dataset.
Qry is the root class in the query operator hierarchy, which is inherited by every detailed
query operator. QryEval takes a single argument input, which specifies the path to the parameter
file for the specific task. Examples of parameter files can be found in the parameterFiles folder. For
detailed parameter requirements for each model, please check the implementation of each RetrievalModel
class. QryEval supports four basic retrieval models (UnrankedBoolean, RankedBoolean, BM25 and Indir)
and ten different query operators. It also supports query expansion for Indri model, and learning to
rank for Indri and BM25. For learning to rank, it calls svm_rank software in another process to 
perform SVM ranking based on computed feature vectors. PageRankInIndex file may need to be specified 
for some tasks in the parameter file. 

Use requirements: 
1.add Lucene-4.3.0 APIs to the class build path
2.add ClueWeb09 indexed dataset to the parameter file

Usage:

Java QryEval $PATH_TO_PARAMETER_FILE 
