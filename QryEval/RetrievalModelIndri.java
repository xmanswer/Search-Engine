/**
 * An object that stores parameters for the Indri retrieval model (there are
 * none) and indicates to the query operators how the query should be evaluated.
 */
public class RetrievalModelIndri extends RetrievalModel {

	private double mu;
	private double lambda;
	
	public RetrievalModelIndri(double mu, double lambda) {
		this.setMu(mu);
		this.setLambda(lambda);
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
}
