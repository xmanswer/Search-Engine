import java.util.*;

/**
 * An object that stores parameters for the letor retrieval model (there are
 * none) and indicates to the query operators how the query should be evaluated.
 */
public class RetrievalModelletor extends RetrievalModel {

	//letor parameters
	private String trainingQueryFile;
	private String trainingQrelsFile;
	private String trainingFeatureVectorsFile;
	private String pageRankFile;
	private Set<Integer> featureDisable;
	private String svmRankLearnPath;
	private String svmRankClassifyPath;
	private double svmRankParamC;
	private String svmRankModelFile;
	private String testingFeatureVectorsFile;
	private String testingDocumentScores;
	
	//bm25 parameters
	private double k_1;
	private double b;
	private double k_3;
	
	//indri parameters
	private double mu;
	private double lambda;
	
	public RetrievalModelletor(String trainingQueryFile,
			String trainingQrelsFile, String trainingFeatureVectorsFile,
			String pageRankFile, String featureDisable, String svmRankLearnPath,
			String svmRankClassifyPath, double svmRankParamC,
			String svmRankModelFile, String testingFeatureVectorsFile,
			String testingDocumentScores, double k_1, double b, double k_3,
			double mu, double lambda) {
		
		this.trainingQueryFile = trainingQueryFile;
		this.trainingQrelsFile = trainingQrelsFile;
		this.trainingFeatureVectorsFile = trainingFeatureVectorsFile;
		this.pageRankFile = pageRankFile;
		this.featureDisable = new HashSet<Integer>();
		if(featureDisable != null) {
			String[] featureDisableStrs= featureDisable.split(",");
			for(String s : featureDisableStrs) {
				this.featureDisable.add(Integer.parseInt(s)-1);
			}
		}
		this.svmRankLearnPath = svmRankLearnPath;
		this.svmRankClassifyPath = svmRankClassifyPath;
		this.svmRankParamC = svmRankParamC;
		this.svmRankModelFile = svmRankModelFile;
		this.testingFeatureVectorsFile = testingFeatureVectorsFile;
		this.testingDocumentScores = testingDocumentScores;
		this.k_1 = k_1;
		this.b = b;
		this.k_3 = k_3;
		this.mu = mu;
		this.lambda = lambda;
	}
	
	public String defaultQrySopName() {
		return new String("#or");
	}
	
	//setters and getters for parameters
	public double getMu() {
		return mu;
	}

	public void setMu(double mu) {
		this.mu = mu;
	}

	public double getLambda() {
		return lambda;
	}

	public void setLambda(double lambda) {
		this.lambda = lambda;
	}


	public String getTrainingQueryFile() {
		return trainingQueryFile;
	}


	public void setTrainingQueryFile(String trainingQueryFile) {
		this.trainingQueryFile = trainingQueryFile;
	}


	public String getTrainingQrelsFile() {
		return trainingQrelsFile;
	}


	public void setTrainingQrelsFile(String trainingQrelsFile) {
		this.trainingQrelsFile = trainingQrelsFile;
	}


	public double getK_1() {
		return k_1;
	}


	public void setK_1(double k_1) {
		this.k_1 = k_1;
	}


	public double getB() {
		return b;
	}


	public void setB(double b) {
		this.b = b;
	}


	public double getK_3() {
		return k_3;
	}


	public void setK_3(double k_3) {
		this.k_3 = k_3;
	}


	public String getTrainingFeatureVectorsFile() {
		return trainingFeatureVectorsFile;
	}


	public void setTrainingFeatureVectorsFile(String trainingFeatureVectorsFile) {
		this.trainingFeatureVectorsFile = trainingFeatureVectorsFile;
	}


	public String getPageRankFile() {
		return pageRankFile;
	}


	public void setPageRankFile(String pageRankFile) {
		this.pageRankFile = pageRankFile;
	}


	public String getSvmRankLearnPath() {
		return svmRankLearnPath;
	}


	public void setSvmRankLearnPath(String svmRankLearnPath) {
		this.svmRankLearnPath = svmRankLearnPath;
	}


	public String getSvmRankClassifyPath() {
		return svmRankClassifyPath;
	}


	public void setSvmRankClassifyPath(String svmRankClassifyPath) {
		this.svmRankClassifyPath = svmRankClassifyPath;
	}


	public double getSvmRankParamC() {
		return svmRankParamC;
	}


	public void setSvmRankParamC(double svmRankParamC) {
		this.svmRankParamC = svmRankParamC;
	}


	public String getSvmRankModelFile() {
		return svmRankModelFile;
	}


	public void setSvmRankModelFile(String svmRankModelFile) {
		this.svmRankModelFile = svmRankModelFile;
	}


	public String getTestingFeatureVectorsFile() {
		return testingFeatureVectorsFile;
	}


	public void setTestingFeatureVectorsFile(String testingFeatureVectorsFile) {
		this.testingFeatureVectorsFile = testingFeatureVectorsFile;
	}


	public String getTestingDocumentScores() {
		return testingDocumentScores;
	}


	public void setTestingDocumentScores(String testingDocumentScores) {
		this.testingDocumentScores = testingDocumentScores;
	}

	public Set<Integer> getFeatureDisable() {
		return featureDisable;
	}

	public void setFeatureDisable(Set<Integer> featureDisable) {
		this.featureDisable = featureDisable;
	}
}
