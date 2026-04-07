package colliderrun;

public enum ParticleType {
    MUON_PAIR("μ+μ-", 0.212, 0.28, "Clean dimuon track pair"),
    KAON_PAIR("K+K-", 0.988, 0.22, "Charged kaon pair ring"),
    PROTON_ANTIPROTON("p p̄", 1.876, 0.19, "Baryon-antibaryon event"),
    Z_BOSON("Z", 91.0, 0.13, "Neutral current resonance"),
    HIGGS("H", 125.0, 0.09, "Higgs candidate event"),
    W_PAIR("W+W-", 161.0, 0.06, "Diboson production"),
    TOP_PAIR("t t̄", 345.0, 0.03, "Top pair + jets signature");

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
