/* Apache-2.0. CPU-only production-controller diagnostic, not GPU/presented FPS.
 * Run after renderer tests finish; compile like PhoneTapProbe.java. */
import com.wexpa.liquidglass.*;
import java.util.Arrays;
import java.util.Locale;

public final class PhoneTapBenchmark {
    private static volatile float sink;

    private static double sample(GlassPoseSpec spec, int hz) {
        GlassSelectorBar bar = new GlassSelectorBar(834,186,21.75f,3,93,139,83.75f,90,150,150,3,800);
        GlassPoseController c = new GlassPoseController(spec);
        c.attach(bar, null, null, 0);
        c.snapToRest(0);
        long start = System.nanoTime();
        int frames = 0;
        float time = 0;
        for (int trip = 0; trip < 10; trip++) {
            c.retarget((trip & 1) == 0 ? 2 : 0);
            for (int i = 0; i < hz; i++) {
                time += 1f / hz;
                c.advanceFrameTo(time);
                sink = c.getCentreX();
                frames++;
            }
        }
        return (System.nanoTime() - start) / 1e6 / frames;
    }

    public static void main(String[] args) {
        System.out.println("model,hz,median_batch_mean_ms_per_advance,max_batch_mean_ms_per_advance");
        for (int hz : new int[]{60, 120}) {
            for (boolean coupled : new boolean[]{false, true}) {
                GlassPoseSpec spec = coupled ? GlassPoseSpec.Companion.getCalmVolume() :
                    GlassPoseSpec.Companion.getCalmIndependent();
                for (int i = 0; i < 3; i++) sample(spec, hz);
                double[] samples = new double[7];
                for (int i = 0; i < samples.length; i++) samples[i] = sample(spec, hz);
                Arrays.sort(samples);
                System.out.printf(Locale.ROOT, "%s,%d,%.6f,%.6f%n", coupled ? "coupled" : "independent",
                    hz, samples[3], samples[6]);
            }
        }
    }
}
