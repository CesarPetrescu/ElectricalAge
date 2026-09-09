package mods.eln.sim.mna;

import org.apache.commons.numbers.core.DD;
import java.util.ArrayList;
import java.util.function.Function;

/** Exact row-update (Woodbury) algebra in the same double-double precision as full inversion.
 * Switchable-source probes change only a few matrix rows. Re-inverting a large common bus for
 * each temporary source mask was cubic work per probe. This reduces a small change to O(k*n*n).
 * Any ineligible/ill-conditioned/non-finite update returns null and requests full inversion.
 */
final class MatrixInverseUpdate {
    private MatrixInverseUpdate() {}
    static DD[][] update(DD[][] oldA, DD[][] newA, DD[][] inverse, Function<DD[][],DD[][]> invert) {
        int n=newA.length;
        if(oldA==null || inverse==null || n<16 || oldA.length!=n || inverse.length!=n)return null;
        var changed=new ArrayList<Integer>();
        for(int r=0;r<n;r++) {
            boolean different=false;
            for(int c=0;c<n;c++)if(!newA[r][c].equals(oldA[r][c])) { different=true;break; }
            if(different) { changed.add(r);if(changed.size()>6)return null; }
        }
        int k=changed.size();if(k==0)return inverse;
        DD[][] w=new DD[k][n];
        for(int i=0;i<k;i++) {
            int row=changed.get(i);
            for(int c=0;c<n;c++)w[i][c]=DD.ZERO;
            for(int j=0;j<n;j++) {
                DD delta=newA[row][j].subtract(oldA[row][j]);
                if(delta.isZero())continue;
                for(int c=0;c<n;c++)w[i][c]=w[i][c].add(delta.multiply(inverse[j][c]));
            }
        }
        DD[][] small=new DD[k][k];
        for(int r=0;r<k;r++)for(int c=0;c<k;c++)small[r][c]=w[r][changed.get(c)].add(r==c?1:0);
        final DD[][] smallInverse;
        try { smallInverse=invert.apply(small); }catch(RuntimeException failure) { return null; }
        DD[][] v=new DD[k][n];
        for(int r=0;r<k;r++)for(int c=0;c<n;c++) {
            DD sum=DD.ZERO;for(int j=0;j<k;j++)sum=sum.add(smallInverse[r][j].multiply(w[j][c]));v[r][c]=sum;
        }
        DD[][] result=new DD[n][n];
        for(int r=0;r<n;r++)for(int c=0;c<n;c++) {
            DD value=inverse[r][c];
            for(int j=0;j<k;j++)value=value.subtract(inverse[r][changed.get(j)].multiply(v[j][c]));
            if(!value.isFinite())return null;
            result[r][c]=value;
        }
        // Validate every changed row of A*inverse, plus two full-system deterministic
        // right-hand sides. Periodic full factorization bounds accumulated update error.
        for(int r:changed)for(int c=0;c<n;c++) {
            DD residual=DD.of(r==c?-1:0);double scale=1;
            for(int j=0;j<n;j++)if(!newA[r][j].isZero()) {
                DD term=newA[r][j].multiply(result[j][c]);residual=residual.add(term);scale+=term.abs().doubleValue();
            }
            if(residual.abs().doubleValue()>1e-24*scale)return null;
        }
        for(int pattern=0;pattern<2;pattern++) {
            DD[] x=new DD[n];
            for(int r=0;r<n;r++) {
                DD sum=DD.ZERO;
                for(int c=0;c<n;c++)sum=sum.add(result[r][c].multiply(rhs(c,pattern)));
                x[r]=sum;
            }
            for(int r=0;r<n;r++) {
                DD residual=DD.of(-rhs(r,pattern));double scale=1;
                for(int c=0;c<n;c++)if(!newA[r][c].isZero()) {
                    DD term=newA[r][c].multiply(x[c]);residual=residual.add(term);scale+=term.abs().doubleValue();
                }
                if(residual.abs().doubleValue()>1e-24*scale)return null;
            }
        }
        return result;
    }
    private static double rhs(int index,int pattern) { return pattern==0?1:((index*17+3)%23-11)/11.0; }
}
