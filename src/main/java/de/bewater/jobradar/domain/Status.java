package de.bewater.jobradar.domain;

public enum Status {
    /** Frisch gefunden, noch nicht angesehen. */
    NEU,
    /** Angesehen und als interessant markiert. */
    VORGEMERKT,
    /** Bewerbung raus. */
    BEWORBEN,
    /** Passt nicht - taucht nie wieder auf. */
    VERWORFEN,
    /** Bewerbung raus, Absage bekommen - taucht nie wieder unter "Neu" auf. */
    ABGELEHNT
}
