package nl.banksim.api.transactie;

/** Een cursor die niet te lezen is, is aangepast, of bij een andere rekening hoort. */
class OngeldigeCursor extends RuntimeException {

    OngeldigeCursor() {
        super("Ongeldige cursor");
    }
}
