package nl.rug.joptimize.learn.grlvq;

import nl.rug.joptimize.learn.LabeledDataSet;
import nl.rug.joptimize.opt.SeperableCostFunction;

public class GRLVQCostFunction implements SeperableCostFunction<GRLVQOptParam> {
    private final LabeledDataSet ds;

    public GRLVQCostFunction(LabeledDataSet ds) {
        this.ds = ds;
    }

    @Override
    public double error(GRLVQOptParam params) {
        // TODO Vectorized?
        double out = 0;
        int size = this.size();
        for (int i = 0; i < size; i++) {
            out += error(params, i);
        }
        return out;
    }

    @Override
    public double error(GRLVQOptParam params, int exampleNdx) {
        double[] data = ds.getData(exampleNdx);
        int label = ds.getLabel(exampleNdx);
        // J = same, K = different
        int j = params.getClosestCorrectProtoNdx(data, label, exampleNdx);
        int k = params.getClosestIncorrectProtoNdx(data, label, exampleNdx);
        double dj = params.dist(j, data), dk = params.dist(k, data);
        return (dj - dk) / (dk + dj);
    }

    @Override
    public GRLVQOptParam deriv(GRLVQOptParam params) {
        // TODO Vectorized?
        GRLVQOptParam out = params.zero();
        int size = this.size();
        for (int i = 0; i < size; i++) {
            deriv(params, i, out);
        }
        return out;
    }

    @Override
    public GRLVQOptParam deriv(GRLVQOptParam params, int exampleNdx) {
        return deriv(params, exampleNdx, params.zero());
    }

    @Override
    public GRLVQOptParam deriv(GRLVQOptParam params, int exampleNdx, GRLVQOptParam out) {
        // (2*dk*dj' - 2*dj*dk')/(dj+dk)^2
        double[] data = ds.getData(exampleNdx);
        int label = ds.getLabel(exampleNdx);
        // J = same, K = different
        int j = params.getClosestCorrectProtoNdx(data, label, exampleNdx);
        int k = params.getClosestIncorrectProtoNdx(data, label, exampleNdx);

        double[] pj = params.prototypes[j], pk = params.prototypes[k];
        double[] opj = out.prototypes[j], opk = out.prototypes[k];
        double[] weights = params.weights, oweights = out.weights;
        double dj = params.dist(j, data), dk = params.dist(k, data);

        double tmp = 2 / ((dj + dk) * (dj + dk));
        // TODO Check signs.
        double tmpJ = tmp * dk, tmpK = -tmp * dj;
        int dims = params.dimensions();

        for (int ndx = 0; ndx < dims; ndx++) {
            double diffJ = pj[ndx] - data[ndx];
            double diffK = pk[ndx] - data[ndx];
                        
            double djP = 2*weights[ndx]*diffJ, dkP = 0;
            double sav = tmpJ * diffJ * weights[ndx];
            opj[ndx] += sav;
            if(Math.abs(firstDerivLVQ(dj, dk, djP, dkP) - sav) > 1e-4) {
                throw new IllegalStateException();
            }
            
            djP = 0; dkP = 2*weights[ndx]*diffK;
            sav = tmpK * diffK * weights[ndx];
            opk[ndx] += sav;
            if(Math.abs(firstDerivLVQ(dj, dk, djP, dkP) - sav) > 1e-4) {
                throw new IllegalStateException();
            }
            
            // (2*dk*(pj-data)^2 - 2*dj*(pk-data)^2)/(dj+dk)^2
            djP = diffJ * diffJ; dkP = diffK * diffK;
            sav = tmpJ * diffJ * diffJ - tmpK * diffK * diffK;
            oweights[ndx] += sav;
            if(Math.abs(firstDerivLVQ(dj, dk, djP, dkP) - sav) > 1e-4) {
                throw new IllegalStateException();
            }
        }

        return out;
    }

    @Override
    public GRLVQOptParam hesseDiag(GRLVQOptParam params) {
        // TODO Vectorized?
        GRLVQOptParam out = params.zero();
        int size = this.size();
        for (int i = 0; i < size; i++) {
            hesseDiag(params, i, out);
        }
        return out;
    }

    @Override
    public GRLVQOptParam hesseDiag(GRLVQOptParam params, int exampleNdx) {
        return hesseDiag(params, exampleNdx, params.zero());
    }

    @Override
    public GRLVQOptParam hesseDiag(GRLVQOptParam params, int exampleNdx, GRLVQOptParam out) {
        // (dj''-dk'')/(dj+dk) - 2(dj'^2-dk'^2)/(dk+dj)^2 + (dj-dk)(2(dj'-dk')^2/(dj+dk)^3 - (dj''+dk'')/(dj+dk)^2)
        double[] data = ds.getData(exampleNdx);
        int label = ds.getLabel(exampleNdx);
        // J = same, K = different
        int j = params.getClosestCorrectProtoNdx(data, label, exampleNdx);
        int k = params.getClosestIncorrectProtoNdx(data, label, exampleNdx);

        double[] pj = params.prototypes[j], pk = params.prototypes[k];
        double[] opj = out.prototypes[j], opk = out.prototypes[k];
        double[] weights = params.weights, oweights = out.weights;
        double dj = params.dist(j, data), dk = params.dist(k, data);
        double dSum = dj + dk;

        double tmp = 4 / (dSum * dSum * dSum);
        // TODO Check signs.
        double tmpJ = tmp * dk * dSum, tmpJ_ = tmp * dk * -2;
        double tmpK = -tmp * dj * dSum, tmpK_ = tmp * dj * 2;
        int dims = params.dimensions();

        for (int ndx = 0; ndx < dims; ndx++) {
            //TODO Appropriate for weights
            double diffJ = pj[ndx] - data[ndx], diffK = pk[ndx] - data[ndx];
            opj[ndx] += weights[ndx] * (tmpJ + tmpJ_ * diffJ);
            opk[ndx] += weights[ndx] * (tmpK + tmpK_ * diffK);
            
            double djP = diffJ*diffJ, dkP = diffK*diffK;
            double sav = tmp/2*((djP-dkP)*(djP-dkP)*(dj-dk) - (dj+dk)*(djP*djP - dkP*dkP));
            oweights[ndx] += sav;
            if(Math.abs(secondDerivLVQ(dj, dk, djP, dkP, 0, 0) - sav) > 1e-4) {
                throw new IllegalStateException();
            }
        }

        return out;
    }
    
    private double firstDerivLVQ(double dj, double dk, double djP, double dkP) {
        double dSum = dj+dk;
        return 2*(dk*djP - dj*dkP)/(dSum*dSum);
    }
    
    private double secondDerivLVQ(double dj, double dk, double djP, double dkP, double djPP, double dkPP) {
        double dSum = dj+dk;
        return (djPP-dkPP)/(dj+dk) - 2*(djP*djP-dkP*dkP)/(dSum*dSum) + (dj-dk)*(2*(djP-dkP)*(djP-dkP)/(dSum*dSum*dSum) - (djPP+dkPP)/(dSum*dSum));
    }

    @Override
    public int size() {
        return ds.size();
    }
}