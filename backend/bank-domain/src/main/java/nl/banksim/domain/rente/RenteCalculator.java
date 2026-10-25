package nl.banksim.domain.rente;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import nl.banksim.domain.geld.Bedrag;

/** ADR 0028 (FO): de rente over een reeks dagen, zoals die aan het eind van de maand wordt bijgeschreven. */
public final class RenteCalculator {

    /** 3% per jaar (FO). */
    static final BigDecimal PERCENTAGE = new BigDecimal("0.03");

    private RenteCalculator() {
    }

    /**
     * Per dag: eindsaldo × 3% / het aantal dagen van dat jaar (365 of 366), op 10 decimalen. De som wordt bij het
     * bijschrijven afgerond op centen. Een negatief eindsaldo levert geen rente op: rente is nooit negatief.
     */
    public static Bedrag rente(List<DagSaldo> dagen) {
        BigDecimal som = BigDecimal.ZERO;
        for (DagSaldo dag : dagen) {
            BigDecimal saldo = dag.eindsaldo().waarde().max(BigDecimal.ZERO);
            som = som.add(saldo.multiply(PERCENTAGE).divide(BigDecimal.valueOf(dag.dag().lengthOfYear()), 10, RoundingMode.HALF_UP));
        }
        return new Bedrag(som.setScale(2, RoundingMode.HALF_UP));
    }
}
