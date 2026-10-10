/* Apache-2.0. Research exporter: calls the compiled production controller unchanged.
 * Compile against liquidglass/build/classes/kotlin/jvm/main and kotlin-stdlib.
 * Geometry is the new three-slot Phone recording, with three alternative resting
 * bodies covering its two measured endpoints. Simulation time is NOT finger time.
 */
import com.wexpa.liquidglass.*;
import java.nio.file.*;
import java.io.*;
import java.util.Locale;

public final class PhoneTapProbe {
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("Supply output CSV path and optional 'touch'");
        boolean touch = args.length == 2 && args[1].equals("touch");
        float[] durations = touch ? new float[] {.04f,.08f,.116f,.16f,.24f} : new float[] {-1f};
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(args[0])))) {
            out.println("restWidth,restHeight,slots,pressSeconds,seconds,phase,cx,width,height,formation,protrusion");
            for (float[] rest : new float[][] {{285,163}, {278,167.5f}, {271,172}}) {
                for (int distance = 1; distance <= 2; distance++) {
                  for (float duration : durations) {
                    GlassSelectorBar bar = new GlassSelectorBar(834,186,21.75f,3,93,
                        rest[0]/2,rest[1]/2,90,150,150,3,800);
                    GlassPoseController c = new GlassPoseController(GlassPoseSpec.Companion.getCalm());
                    c.attach(bar,null,null,0); c.snapToRest(0);
                    if(touch) c.pointerDown(bar.centreOf(distance),bar.getCentreY(),0,true);
                    c.retarget(distance);
                    GlassPoseExtents e = new GlassPoseExtents();
                    boolean released = !touch, acquired = false;
                    for (int i=0;i<=480;i++) {
                        float t=i/480f;
                        // Mirror the adapter's timeout acquisition for longer stationary
                        // contacts. The owner's word "tap" does not supply finger duration.
                        if(touch && duration>.12f && !acquired && t>=.12f) {
                            c.advanceTo(.12f,.5f);
                            c.beginDrag(bar.centreOf(distance),bar.getCentreY()); acquired=true;
                        }
                        if(!released && t>=duration) {
                            c.advanceTo(duration,.5f); c.pointerUp(distance); released=true;
                        }
                        c.advanceTo(t,.5f); c.extents(e);
                        if(c.getSolverFailed() || (c.isHeld() && duration<=.12f))
                            throw new IllegalStateException("Invalid tap state at " + t);
                        float phase=(c.getCentreX()-bar.centreOf(0))/(bar.centreOf(distance)-bar.centreOf(0));
                        out.printf(Locale.ROOT,"%.3f,%.3f,%d,%.3f,%.7f,%.7f,%.4f,%.4f,%.4f,%.6f,%.5f%n",
                            rest[0],rest[1],distance,duration,t,phase,c.getCentreX(),e.getWidth(),
                            e.getHeight(),c.getFormation(),c.protrusion());
                    }
                  }
                }
            }
        }
    }
}
