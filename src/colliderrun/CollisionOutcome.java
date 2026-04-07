package colliderrun;

public record CollisionOutcome(
        boolean valid,
        ParticleType particle,
        double partonFraction,
        double effectiveEnergy,
        String message,
        int scoreDelta
) {
}
