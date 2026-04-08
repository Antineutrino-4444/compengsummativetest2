package colliderrun;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CollisionEngine {
    private final ParticleDatabase database;
    private final Random random;

    public CollisionEngine(ParticleDatabase database, Random random) {
        this.database = database;
        this.random = random;
    }

    public CollisionOutcome resolve(double beamEnergyGeV,
                                    double magnetFocus,
                                    double detectorCalibration,
                                    double luminosity,
                                    double heat,
                                    double steeringQuality,
                                    ParticleType target) {

        double partonFraction = 0.25 + random.nextDouble() * 0.75;
        double focusFactor = 0.55 + (magnetFocus / 100.0) * 0.65;
        double calibrationFactor = 0.65 + (detectorCalibration / 100.0) * 0.5;
        double luminosityFactor = 0.75 + (luminosity / 100.0) * 0.5;
        double heatPenalty = Math.max(0.35, 1.0 - (heat / 170.0));
        double steeringFactor = 0.7 + Math.max(0, Math.min(1, steeringQuality)) * 0.6;

        double effective = beamEnergyGeV * partonFraction * focusFactor * calibrationFactor * luminosityFactor * heatPenalty * steeringFactor;

        List<ParticleType> available = database.availableAtEnergy(effective);
        if (available.isEmpty()) {
            return new CollisionOutcome(false, null, partonFraction, effective,
                    "No hard scatter: only soft spray observed.", -45);
        }

        ParticleType selected = chooseWeighted(available, target);
        if (selected.ordinal() < target.ordinal()) {
            return new CollisionOutcome(true, selected, partonFraction, effective,
                    "Valid event, below target: " + selected.label, 120);
        }

        int score = 260 + selected.ordinal() * 100 + (int) Math.round(effective * 1.9);
        return new CollisionOutcome(true, selected, partonFraction, effective,
                "Target-grade event: " + selected.label + " confirmed.", score);
    }

    private ParticleType chooseWeighted(List<ParticleType> available, ParticleType target) {
        List<Double> weights = new ArrayList<>();
        double total = 0;
        for (ParticleType p : available) {
            double targetBias = p.ordinal() >= target.ordinal() ? 1.35 : 0.8;
            double w = p.baseWeight * targetBias;
            weights.add(w);
            total += w;
        }

        double x = random.nextDouble() * total;
        double running = 0;
        for (int i = 0; i < available.size(); i++) {
            running += weights.get(i);
            if (x <= running) {
                return available.get(i);
            }
        }
        return available.get(available.size() - 1);
    }
}
