package nl.banksim.seed;

/** ADR 0040: of de rekeninghouder er al is; de gebruikersnaam is de business-sleutel. */
public interface BestaandeKlanten {

    boolean bestaat(String gebruikersnaam);
}
