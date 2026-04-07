package colliderrun;

import java.util.ArrayList;
import java.util.List;

public class ParticleDatabase {

    public List<ParticleType> availableAtEnergy(double effectiveGeV) {
        List<ParticleType> out = new ArrayList<>();
        for (ParticleType particle : ParticleType.values()) {
            if (effectiveGeV >= particle.thresholdGeV) {
                out.add(particle);
            }
        }
        return out;
    }
}
