/**
 * An object that stores parameters for the BM25 retrieval model (there are
 * none) and indicates to the query operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {
	private double k_1;
	private double b;
	private double k_3;
	
	public RetrievalModelBM25(double k_1, double b, double k_3) {
		this.setK_1(k_1);
		this.setB(b);
		this.setK_3(k_3);
	}
	
	public String defaultQrySopName() {
		return new String("#sum");
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
}
