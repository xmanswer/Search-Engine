/**
 * An object that stores parameters for the Indri retrieval model (there are
 * none) and indicates to the query operators how the query should be evaluated.
 */
public class RetrievalModelIndri extends RetrievalModel {

	private double mu;
	private double lambda;
	private boolean fb;
	private int	fbDocs;
	private int	fbTerms;
	private int	fbMu;
	private double fbOrigWeight;
	private String fbExpansionQueryFile;
	private String fbInitialRankingFile;
			
	public RetrievalModelIndri(double mu, double lambda) {
		this.setFb(false);
		this.setMu(mu);
		this.setLambda(lambda);
	}
	
	public RetrievalModelIndri(double mu, double lambda, boolean fb, int fbDocs, 
			int fbTerms, int fbMu, double fbOrigWeight, String fbExpansionQueryFile, 
			String fbInitialRankingFile) {
		this.setMu(mu);
		this.setLambda(lambda);
		this.setFb(fb);
		this.setFbDocs(fbDocs);
		this.setFbExpansionQueryFile(fbExpansionQueryFile);
		this.setFbInitialRankingFile(fbInitialRankingFile);
		this.setFbMu(fbMu);
		this.setFbOrigWeight(fbOrigWeight);
		this.setFbTerms(fbTerms);
	}
	
	public String defaultQrySopName() {
		return new String("#and");
	}

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

	public boolean isFb() {
		return fb;
	}

	public void setFb(boolean fb) {
		this.fb = fb;
	}

	public int getFbDocs() {
		return fbDocs;
	}

	public void setFbDocs(int fbDocs) {
		this.fbDocs = fbDocs;
	}

	public int getFbTerms() {
		return fbTerms;
	}

	public void setFbTerms(int fbTerms) {
		this.fbTerms = fbTerms;
	}

	public int getFbMu() {
		return fbMu;
	}

	public void setFbMu(int fbMu) {
		this.fbMu = fbMu;
	}

	public double getFbOrigWeight() {
		return fbOrigWeight;
	}

	public void setFbOrigWeight(double fbOrigWeight) {
		this.fbOrigWeight = fbOrigWeight;
	}

	public String getFbExpansionQueryFile() {
		return fbExpansionQueryFile;
	}

	public void setFbExpansionQueryFile(String fbExpansionQueryFile) {
		this.fbExpansionQueryFile = fbExpansionQueryFile;
	}

	public String getFbInitialRankingFile() {
		return fbInitialRankingFile;
	}

	public void setFbInitialRankingFile(String fbInitialRankingFile) {
		this.fbInitialRankingFile = fbInitialRankingFile;
	}
}
