package colliderrun;

import java.util.List;
import java.util.Random;

public class CollisionEngine {
    private final ParticleDatabase database;
    private final Random random;

    public CollisionEngine(ParticleDatabase database, Random random) {
        this.database = database;
        this.random = random;
    }

    public CollisionOutcome trigger(double beamEnergyGeV, double calibration, double luminosity) {
        double partonFraction = 0.1 + random.nextDouble() * 0.9;
        double calibrationFactor = 0.65 + (calibration / 100.0) * 0.55;
        double effective = beamEnergyGeV * partonFraction * calibrationFactor;

        List<ParticleType> available = database.availableAtEnergy(effective);
        if (available.isEmpty()) {
            return new CollisionOutcome(false, null, partonFraction, effective,
                    "Soft collision: no heavy final state identified.", -30);
        }

        ParticleType chosen = chooseWeighted(available, luminosity);
        if (chosen == ParticleType.TOP_PAIR && effective < ParticleType.TOP_PAIR.thresholdGeV * 1.04) {
            return new CollisionOutcome(false, null, partonFraction, effective,
                    "Ambiguous top-like jets failed validation.", -40);
        }

        int score = 100 + (int) Math.round(chosen.thresholdGeV * 3.5 + luminosity * 1.6);
        return new CollisionOutcome(true, chosen, partonFraction, effective,
                "Detected " + chosen.label + " : " + chosen.eventDescription, score);
    }

    private ParticleType chooseWeighted(List<ParticleType> available, double luminosity) {
        double sum = 0.0;
        for (ParticleType p : available) {
            double lumBoost = 1.0 + luminosity / 220.0;
            double energyPenalty = 1.0 / Math.max(1.0, p.thresholdGeV / 20.0);
            sum += p.baseWeight * lumBoost * energyPenalty;
        }

        double x = random.nextDouble() * sum;
        double running = 0.0;
        for (ParticleType p : available) {
            double lumBoost = 1.0 + luminosity / 220.0;
            double energyPenalty = 1.0 / Math.max(1.0, p.thresholdGeV / 20.0);
            running += p.baseWeight * lumBoost * energyPenalty;
            if (x <= running) {
                return p;
            }
        }
        return available.get(available.size() - 1);
    }
}
