/* Apache-2.0. Production-controller sweep, not a recovered original finger trace. */
import com.wexpa.liquidglass.*;
import java.nio.file.*;
import java.io.*;
import java.util.Locale;

public final class PhoneThrowProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply new output CSV path");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(args[0])))) {
            out.println("hold,dragDuration,distanceSlots,secondsAfterUp,cx,width,height,formation,protrusion");
            for (float hold : new float[]{.06f,.24f,.8f})
              for (float duration : new float[]{.06f,.10f,.16f,.24f,.4f})
                for (float distance : new float[]{.5f,1f,1.5f,2f,3f}) {
                    GlassSelectorBar bar = new GlassSelectorBar(834,186,21.75f,3,93,142.5f,80,90,150,150,3,800);
                    GlassPoseController c = new GlassPoseController(GlassPoseSpec.Companion.getCalm());
                    c.attach(bar,null,null,0); c.snapToRest(0);
                    c.pointerDown(bar.centreOf(0),bar.getCentreY(),0,true);
                    c.advanceTo(hold,.5f); c.beginDrag(bar.centreOf(0),bar.getCentreY());
                    int steps=Math.round(duration*480);
                    for(int i=1;i<=steps;i++) {
                        float time=hold+duration*i/steps;
                        c.pointerMove(bar.centreOf(0)+distance*bar.getSlotWidth()*i/steps,bar.getCentreY(),time);
                        c.advanceFrameTo(time);
                    }
                    c.pointerUp(2);
                    GlassPoseExtents e=new GlassPoseExtents();
                    for(int i=0;i<=480;i++) {
                        float elapsed=i/480f;
                        c.advanceTo(hold+duration+elapsed,.5f); c.extents(e);
                        if(c.getSolverFailed()) throw new IllegalStateException("Invalid throw state");
                        out.printf(Locale.ROOT,"%.3f,%.3f,%.3f,%.7f,%.5f,%.5f,%.5f,%.5f,%.5f%n",
                            hold,duration,distance,elapsed,c.getCentreX(),e.getWidth(),e.getHeight(),c.getFormation(),c.protrusion());
                    }
                }
        }
    }
}
