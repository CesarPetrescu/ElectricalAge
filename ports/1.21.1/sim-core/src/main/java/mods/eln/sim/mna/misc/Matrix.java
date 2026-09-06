package mods.eln.sim.mna.misc;

/**
 * A simple matrix class to replace Apache Commons Math.
 * Implements basic operations and QR decomposition for matrix inversion.
 */
public class Matrix {
    private final int rows;
    private final int cols;
    private final double[][] data;

    public Matrix(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.data = new double[rows][cols];
    }

    public void setEntry(int row, int col, double value) {
        data[row][col] = value;
    }

    public void addToEntry(int row, int col, double value) {
        data[row][col] += value;
    }

    public double getEntry(int row, int col) {
        return data[row][col];
    }

    public int getRowDimension() {
        return rows;
    }

    public int getColumnDimension() {
        return cols;
    }

    public double[][] getData() {
        double[][] copy = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(data[i], 0, copy[i], 0, cols);
        }
        return copy;
    }

    /**
     * Inverts the matrix using QR decomposition (Householder reflections).
     * Assumes the matrix is square.
     * @return The inverse matrix.
     * @throws RuntimeException if the matrix is singular.
     */
    public Matrix getInverse() {
        if (rows != cols) {
            throw new IllegalArgumentException("Matrix must be square to invert.");
        }
        int n = rows;
        double[][] Q = new double[n][n];
        double[][] R = new double[n][n];

        // Column-relative rank tolerance must not depend on physical units.
        double[] columnNorm = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double value = data[i][j];
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("Matrix contains a non-finite value.");
                }
                R[i][j] = value;
                columnNorm[j] = Math.hypot(columnNorm[j], value);
                if (!Double.isFinite(columnNorm[j])) {
                    throw new ArithmeticException("Matrix column norm is not representable.");
                }
            }
        }

        // Initialize Q as Identity
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                Q[i][j] = (i == j) ? 1.0 : 0.0;
            }
        }

        // QR Decomposition using Householder reflections
        for (int k = 0; k < n - 1; k++) {
            double[] x = new double[n - k];
            for (int i = k; i < n; i++) x[i - k] = R[i][k];

            double normX = 0;
            for (double value : x) normX = Math.hypot(normX, value);

            // Normalize before constructing the Householder vector. Squaring
            // raw values under/overflowed and a fixed 1e-15 cutoff skipped
            // valid reflections for small but nonsingular matrices.
            double[] v = new double[n - k];
            if (normX != 0.0) {
                for (int i = 0; i < v.length; i++) v[i] = x[i] / normX;
                v[0] += Math.copySign(1.0, x[0]);
                double normV = 0;
                for (double value : v) normV = Math.hypot(normV, value);
                for (int i = 0; i < v.length; i++) v[i] /= normV;

                // Update R: R = (I - 2vv')R
                for (int j = k; j < n; j++) {
                    double dot = 0;
                    for (int i = 0; i < n - k; i++) dot += v[i] * R[i + k][j];
                    for (int i = 0; i < n - k; i++) {
                        double projection = v[i] * dot;
                        // Avoid overflowing 2 * projection when the reflected
                        // result itself is finite (e.g. a 1e308 diagonal).
                        R[i + k][j] = (R[i + k][j] - projection) - projection;
                    }
                }

                // Update Q: Q = Q(I - 2vv')' = Q(I - 2vv')
                for (int i = 0; i < n; i++) {
                    double dot = 0;
                    for (int j = 0; j < n - k; j++) dot += Q[i][j + k] * v[j];
                    for (int j = 0; j < n - k; j++) Q[i][j + k] -= 2 * dot * v[j];
                }
            }
        }

        // Solve R * inv = Q'
        // Since Q is orthogonal, Q' = Q_transpose
        // R is upper triangular. Solve Rx = b for each column b of Q'
        Matrix inv = new Matrix(n, n);
        for (int j = 0; j < n; j++) {
            // Column j of Q' is Row j of Q
            double[] b = new double[n];
            for (int i = 0; i < n; i++) b[i] = Q[j][i];

            // Back substitution
            for (int i = n - 1; i >= 0; i--) {
                double sum = 0;
                for (int l = i + 1; l < n; l++) {
                    sum += R[i][l] * b[l];
                }
                double tolerance = 16.0 * n * Math.ulp(columnNorm[i]);
                if (!Double.isFinite(R[i][i]) || Math.abs(R[i][i]) <= tolerance) {
                    throw new ArithmeticException("Matrix is singular or numerically rank-deficient.");
                }
                b[i] = (b[i] - sum) / R[i][i];
                if (!Double.isFinite(b[i])) {
                    throw new ArithmeticException("Matrix inverse contains an unrepresentable value.");
                }
            }

            for (int i = 0; i < n; i++) {
                inv.setEntry(i, j, b[i]);
            }
        }

        return inv;
    }
}
