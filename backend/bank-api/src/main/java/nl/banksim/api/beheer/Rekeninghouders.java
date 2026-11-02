package nl.banksim.api.beheer;

import java.time.LocalDate;
import java.util.List;

/** S10.1: de rekeninghouders voor de beheerder, in de database van bank-api. */
interface Rekeninghouders {

    /**
     * Hoogstens 50 rekeninghouders met een betaalrekening, op naam, waarvan de naam of een rekeningnummer de tekst
     * bevat; een lege tekst vindt iedereen. De saldi zijn die aan het eind van {@code vandaag}.
     */
    List<Rekeninghouder> zoek(String tekst, LocalDate vandaag);
}
