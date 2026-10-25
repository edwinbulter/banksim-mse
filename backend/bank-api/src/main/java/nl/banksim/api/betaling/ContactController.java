package nl.banksim.api.betaling;

import java.util.List;

import nl.banksim.api.beveiliging.IngelogdeGebruiker;
import nl.banksim.domain.grootboek.Contact;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S7.1 (FO): suggesties uit de contactenlijst bij typen in naam of IBAN op het betaalscherm. */
@RestController
class ContactController {

    private final Contacten contacten;
    private final IngelogdeGebruiker gebruiker;

    ContactController(Contacten contacten, IngelogdeGebruiker gebruiker) {
        this.contacten = contacten;
        this.gebruiker = gebruiker;
    }

    @GetMapping("/api/contacten")
    @PreAuthorize("isAuthenticated()")
    List<ContactJson> zoek(@RequestParam String q) {
        return contacten.zoek(q, gebruiker.gebruikersnaam()).stream().map(ContactJson::van).toList();
    }

    record ContactJson(String naam, String iban) {

        static ContactJson van(Contact contact) {
            return new ContactJson(contact.naam(), contact.iban().toString());
        }
    }
}
