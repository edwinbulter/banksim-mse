package nl.banksim.seed;

import java.util.List;

import nl.banksim.domain.grootboek.Overboeking;

/** ADR 0042: overboekingen tussen twee rekeningen van BankSim, in de database van bank-api. */
public interface Overboekingen {

    void schrijfAlle(List<Overboeking> overboekingen);
}
