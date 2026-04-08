package colliderrun;

public enum ParticleType {
    MUON_PAIR("MUON PAIR", 0.212, 0.28, "Clean dimuon tracks"),
    KAON_PAIR("KAON PAIR", 0.988, 0.22, "Charged kaon ring"),
    PROTON_ANTIPROTON("P-ANTI-P", 1.876, 0.19, "Baryon pair event"),
    Z_BOSON("Z BOSON", 91.0, 0.13, "Neutral resonance"),
    HIGGS("HIGGS", 125.0, 0.09, "Higgs-like candidate"),
    W_PAIR("W PAIR", 161.0, 0.06, "Diboson signature"),
    TOP_PAIR("TOP PAIR", 345.0, 0.03, "Top pair plus jets");

    public final String label;
    public final double thresholdGeV;
    public final double baseWeight;
    public final String eventDescription;

    ParticleType(String label, double thresholdGeV, double baseWeight, String eventDescription) {
        this.label = label;
        this.thresholdGeV = thresholdGeV;
        this.baseWeight = baseWeight;
        this.eventDescription = eventDescription;
    }
}
