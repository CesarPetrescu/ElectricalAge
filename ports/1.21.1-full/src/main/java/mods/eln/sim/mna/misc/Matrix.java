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

        // Copy this matrix data to R
        for (int i = 0; i < n; i++) {
            System.arraycopy(data[i], 0, R[i], 0, n);
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
            for (double val : x) normX += val * val;
            normX = Math.sqrt(normX);

            double rho = (x[0] >= 0) ? -normX : normX;
            double[] v = new double[n - k];
            System.arraycopy(x, 0, v, 0, n - k);
            v[0] -= rho;

            double normV = 0;
            for (double val : v) normV += val * val;
            normV = Math.sqrt(normV);

            if (normV > 1e-15) {
                for (int i = 0; i < n - k; i++) v[i] /= normV;

                // Update R: R = (I - 2vv')R
                for (int j = k; j < n; j++) {
                    double dot = 0;
                    for (int i = 0; i < n - k; i++) dot += v[i] * R[i + k][j];
                    for (int i = 0; i < n - k; i++) R[i + k][j] -= 2 * v[i] * dot;
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
                if (Math.abs(R[i][i]) < 1e-18) {
                    throw new RuntimeException("Matrix is singular.");
                }
                b[i] = (b[i] - sum) / R[i][i];
            }

            for (int i = 0; i < n; i++) {
                inv.setEntry(i, j, b[i]);
            }
        }

        return inv;
    }
}
