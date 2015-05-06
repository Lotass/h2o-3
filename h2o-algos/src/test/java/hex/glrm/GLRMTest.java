package hex.glrm;

import hex.DataInfo;
import hex.glrm.GLRMModel.GLRMParameters;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.FrameUtils;
import water.util.Log;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

public class GLRMTest extends TestUtil {
  public final double TOLERANCE = 1e-6;
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  private static String colFormat(String[] cols, String format) {
    StringBuilder sb = new StringBuilder();
    for(int i = 0; i < cols.length; i++) sb.append(String.format(format, cols[i]));
    sb.append("\n");
    return sb.toString();
  }

  private static String colExpFormat(String[] cols, String[][] domains, String format) {
    StringBuilder sb = new StringBuilder();
    for(int i = 0; i < domains.length; i++) {
      if(domains[i] == null)
        sb.append(String.format(format, cols[i]));
      else {
        for(int j = 0; j < domains[i].length; j++)
          sb.append(String.format(format, domains[i][j]));
      }
    }
    sb.append("\n");
    return sb.toString();
  }

  public double errStddev(double[] expected, double[] actual) {
    double err = 0;
    for(int i = 0; i < actual.length; i++) {
      double diff = expected[i] - actual[i];
      err += diff * diff;
    }
    return err;
  }

  public double errEigvec(double[][] expected, double[][] actual) { return errEigvec(expected, actual, TOLERANCE); }
  public double errEigvec(double[][] expected, double[][] actual, double threshold) {
    double err = 0;
    for(int j = 0; j < actual[0].length; j++) {
      boolean flipped = Math.abs(expected[0][j] - actual[0][j]) > threshold;
      for(int i = 0; i < actual.length; i++) {
        double diff = expected[i][j] - (flipped ? -actual[i][j] : actual[i][j]);
        err += diff * diff;
      }
    }
    return err;
  }

  @Test public void testArrests() throws InterruptedException, ExecutionException {
    // Initialize using first k rows of standardized training frame
    Frame yinit = frame(ard(ard(1.24256408, 0.7828393, -0.5209066, -0.003416473),
            ard(0.50786248, 1.1068225, -1.2117642, 2.484202941),
            ard(0.07163341, 1.4788032, 0.9989801, 1.042878388)));
    GLRM job = null;
    GLRMModel model = null;
    Frame train = null;
    long seed = 1234;

    try {
      train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._gamma_x = parms._gamma_y = 0.5;
      parms._k = 3;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._recover_pca = false;
      parms._user_points = yinit._key;
      parms._seed = seed;

      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      yinit.delete();
      if (train != null) train.delete();
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
    }
  }

  @Test public void testBenignSVD() throws InterruptedException, ExecutionException {
    GLRM job = null;
    GLRMModel model = null;
    Frame train = null;
    try {
      train = parse_test_file(Key.make("benign.hex"), "smalldata/logreg/benign.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 14;
      parms._gamma_x = parms._gamma_y = 0;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._init = GLRM.Initialization.SVD;
      parms._recover_pca = false;
      parms._max_iterations = 2000;

      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (train != null) train.delete();
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
    }
  }

  @Test public void testArrestsPCA() throws InterruptedException, ExecutionException {
    // Initialize using first k rows of standardized training frame
    Frame yinit = frame(ard(ard(1.24256408, 0.7828393, -0.5209066, -0.003416473),
            ard(0.50786248, 1.1068225, -1.2117642, 2.484202941),
            ard(0.07163341, 1.4788032, 0.9989801, 1.042878388),
            ard(0.23234938, 0.2308680, -1.0735927, -0.184916602)));
    double[] stddev = new double[] {1.5748783, 0.9948694, 0.5971291, 0.4164494};
    double[][] eigvec = ard(ard(-0.5358995, 0.4181809, -0.3412327, 0.64922780),
            ard(-0.5831836, 0.1879856, -0.2681484, -0.74340748),
            ard(-0.2781909, -0.8728062, -0.3780158, 0.13387773),
            ard(-0.5434321, -0.1673186, 0.8177779, 0.08902432));

    GLRMModel model = null;
    Frame train = null;
    try {
      train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 4;
      parms._gamma_x = parms._gamma_y = 0;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._init = GLRM.Initialization.PlusPlus;
      parms._max_iterations = 1000;
      parms._min_step_size = 1e-8;
      parms._user_points = yinit._key;
      parms._recover_pca = true;

      GLRM job = new GLRM(parms);
      try {
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        // checkStddev(stddev, model._output._std_deviation, 1e-4);
        // checkEigvec(eigvec, model._output._eigenvectors_raw, 1e-4);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      yinit.delete();
      if (train != null) train.delete();
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
    }
  }

  @Test public void testArrestsMissing() throws InterruptedException, ExecutionException {
    // Expected eigenvectors and their standard deviations with standardized data
    double[] stddev = new double[] {1.5748783, 0.9948694, 0.5971291, 0.4164494};
    double[][] eigvec = ard(ard(-0.5358995, 0.4181809, -0.3412327, 0.64922780),
            ard(-0.5831836, 0.1879856, -0.2681484, -0.74340748),
            ard(-0.2781909, -0.8728062, -0.3780158, 0.13387773),
            ard(-0.5434321, -0.1673186, 0.8177779, 0.08902432));

    long seed = 1234;
    Frame train = null;
    GLRMModel model = null;
    GLRMParameters parms;

    Map<Double,Double> sd_map = new TreeMap<>();
    Map<Double,Double> ev_map = new TreeMap<>();
    StringBuilder sb = new StringBuilder();

    for (double missing_fraction : new double[]{0, 0.1, 0.25, 0.5, 0.75, 0.9}) {
      try {
        Scope.enter();
        train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");

        // Add missing values to the training data
        if (missing_fraction > 0) {
          Frame frtmp = new Frame(Key.make(), train.names(), train.vecs());
          DKV.put(frtmp._key, frtmp); // Need to put the frame (to be modified) into DKV for MissingInserter to pick up
          FrameUtils.MissingInserter j = new FrameUtils.MissingInserter(frtmp._key, seed, missing_fraction);
          j.execImpl();
          j.get(); // MissingInserter is non-blocking, must block here explicitly
          DKV.remove(frtmp._key); // Delete the frame header (not the data)
        }

        parms = new GLRMParameters();
        parms._train = train._key;
        parms._k = train.numCols();
        parms._gamma_x = parms._gamma_y = 0;
        parms._transform = DataInfo.TransformType.STANDARDIZE;
        parms._init = GLRM.Initialization.PlusPlus;
        parms._max_iterations = 1000;
        parms._seed = seed;
        parms._recover_pca = true;

        GLRM job = new GLRM(parms);
        try {
          model = job.trainModel().get();
          Log.info(100 * missing_fraction + "% missing values: Objective = " + model._output._objective);
          double sd_err = errStddev(stddev, model._output._std_deviation)/parms._k;
          double ev_err = errEigvec(eigvec, model._output._eigenvectors_raw)/parms._k;
          Log.info("Avg SSE in Std Dev = " + sd_err + "\tAvg SSE in Eigenvectors = " + ev_err);
          sd_map.put(missing_fraction, sd_err);
          ev_map.put(missing_fraction, ev_err);
        } catch (Throwable t) {
          t.printStackTrace();
          throw new RuntimeException(t);
        } finally {
          job.remove();
        }
        Scope.exit();
      } catch(Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        if (train != null) train.delete();
        if (model != null) {
          model._parms._loading_key.get().delete();
          model.delete();
        }
      }
    }
    sb.append("Missing Fraction --> Avg SSE in Std Dev\n");
    for (String s : Arrays.toString(sd_map.entrySet().toArray()).split(",")) sb.append(s.replace("=", " --> ")).append("\n");
    sb.append("\n");
    sb.append("Missing Fraction --> Avg SSE in Eigenvectors\n");
    for (String s : Arrays.toString(ev_map.entrySet().toArray()).split(",")) sb.append(s.replace("=", " --> ")).append("\n");
    Log.info(sb.toString());
  }

  @Test @Ignore public void testCategoricalIris() throws InterruptedException, ExecutionException {
    GLRM job = null;
    GLRMModel model = null;
    Frame train = null;

    try {
      train = parse_test_file(Key.make("iris.hex"), "smalldata/iris/iris_wheader.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._gamma_x = parms._gamma_y = 0;
      parms._k = 5;
      parms._init = GLRM.Initialization.PlusPlus;
      parms._transform = DataInfo.TransformType.NONE;
      parms._recover_pca = false;

      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (train != null) train.delete();
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
    }
  }

  @Test public void testExpandCategoricals() {
    double[][] iris = ard(ard(6.3, 2.5, 4.9, 1.5, 1),
                             ard(5.7, 2.8, 4.5, 1.3, 1),
                             ard(5.6, 2.8, 4.9, 2.0, 2),
                             ard(5.0, 3.4, 1.6, 0.4, 0),
                             ard(6.0, 2.2, 5.0, 1.5, 2));
    double[][] iris_expandR = ard(ard(6.3, 2.5, 4.9, 1.5, 0, 1, 0),
                                  ard(5.7, 2.8, 4.5, 1.3, 0, 1, 0),
                                  ard(5.6, 2.8, 4.9, 2.0, 0, 0, 1),
                                  ard(5.0, 3.4, 1.6, 0.4, 1, 0, 0),
                                  ard(6.0, 2.2, 5.0, 1.5, 0, 0, 1));
    String[] iris_cols = new String[] {"sepal_len", "sepal_wid", "petal_len", "petal_wid", "class"};
    String[][] iris_domains = new String[][] { null, null, null, null, new String[] {"setosa", "versicolor", "virginica"} };

    double[][] iris_expand = GLRM.expandCategoricals(iris, iris_domains);
    Log.info("Original matrix:\n" + colFormat(iris_cols, " %7.7s") + ArrayUtils.pprint(iris));
    Log.info("Expanded matrix:\n" + colExpFormat(iris_cols, iris_domains, " %7.7s") + ArrayUtils.pprint(iris_expand));
    Assert.assertArrayEquals(iris_expandR, iris_expand);

    double[][] prostate = ard(ard(1, 75, 0, 0,  4.8, 26.3, 1, 7),
                              ard(0, 67, 0, 2, 18.1,  0.0, 1, 8),
                              ard(0, 54, 1, 0, 64.3,  0.0, 1, 7),
                              ard(0, 76, 0, 0,  7.6,  3.7, 1, 6));
    double[][] pros_expandR = ard(ard(0, 1, 75, 1, 0, 1, 0, 0, 0,  4.8, 26.3, 0, 1, 7),
                                  ard(1, 0, 67, 1, 0, 0, 0, 1, 0, 18.1,  0.0, 0, 1, 8),
                                  ard(1, 0, 54, 0, 1, 1, 0, 0, 0, 64.3,  0.0, 0, 1, 7),
                                  ard(1, 0, 76, 1, 0, 1, 0, 0, 0,  7.6,  3.7, 0, 1, 6));
    String[] pros_cols = new String[] {"Capsule", "Age", "Race", "Dpros", "PSA", "Vol", "Dcaps", "Gleason"};
    String[][] pros_domains = new String[][] { new String[] {"No", "Yes"}, null, new String[] {"White", "Black"},
            new String[] {"None", "UniLeft", "UniRight", "Bilobar"}, null, null, new String[] {"No", "Yes"}, null };

    double[][] pros_expand = GLRM.expandCategoricals(prostate, pros_domains);
    Log.info("\nOriginal matrix:\n" + colFormat(pros_cols, " %7.7s") + ArrayUtils.pprint(prostate));
    Log.info("Expanded matrix:\n" + colExpFormat(pros_cols, pros_domains, " %7.7s") + ArrayUtils.pprint(pros_expand));
    Assert.assertArrayEquals(pros_expandR, pros_expand);
  }
}
