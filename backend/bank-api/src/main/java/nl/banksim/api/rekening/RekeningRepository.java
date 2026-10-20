package nl.banksim.api.rekening;

import java.util.List;

interface RekeningRepository {

    /** De rekeningen van de rekeninghouder met deze gebruikersnaam; leeg als die niet bestaat. */
    List<Rekening> vanGebruiker(String gebruikersnaam);
}
