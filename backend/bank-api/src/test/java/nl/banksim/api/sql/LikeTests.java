package nl.banksim.api.sql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Injectie (slice 5): %, _ en \ zijn in de invoer gewone tekens, met ESCAPE '\' in de query. */
class LikeTests {

    @Test
    void maaktDeJokertekensEnHetEscapetekenGewoon() {
        assertThat(Like.letterlijk("50%_\\a")).isEqualTo("50\\%\\_\\\\a");
    }

    @Test
    void laatAndereTekstOngemoeid() {
        assertThat(Like.letterlijk("Albert Heijn")).isEqualTo("Albert Heijn");
    }
}
