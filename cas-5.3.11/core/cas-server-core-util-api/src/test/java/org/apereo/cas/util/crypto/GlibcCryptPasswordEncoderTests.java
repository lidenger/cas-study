package org.apereo.cas.util.crypto;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * This tests {@link GlibcCryptPasswordEncoder}.
 *
 * @author Martin Böhmer
 * @since 5.3.10
 */
@Slf4j
public class GlibcCryptPasswordEncoderTests {

    private final String passwordClear = "12345abcDEF!$";

    @Test
    public void sha512EncodingTest() {
        assertTrue(testEncodingRoundtrip("SHA-512"));
        assertTrue(testEncodingRoundtrip("6"));
        assertTrue(testMatchWithDifferentSalt("SHA-512", "$6$rounds=1000$df273de606d3609a$GAPcq.K4jO3KkfusCM7Zr8Cci4qf.jOsWj5hkGBpwRg515bKk93afAXHy/lg.2LPr8ZItHoR3AR5X3XOXndZI0"));
    }

    @Test
    public void ha256EncodingTest() {
        assertTrue(testEncodingRoundtrip("SHA-256"));
        assertTrue(testEncodingRoundtrip("5"));
        assertTrue(testMatchWithDifferentSalt("SHA-256", "$5$rounds=1000$e98244bb01b64f47$2qphrK8axtGjgmCJFYwaH7czw5iK9feLl7tKjyTlDy0"));
    }

    @Test
    public void md5EncodingTest() {
        assertTrue(testEncodingRoundtrip("MD5"));
        assertTrue(testEncodingRoundtrip("1"));
        assertTrue(testMatchWithDifferentSalt("MD5", "$1$c4676fd0$HOHZ2CYp45lZAAQyvF4C21"));
    }

    @Test
    public void desUnixCryptEncodingTest() {
        assertTrue(testEncodingRoundtrip("aB"));
        assertTrue(testEncodingRoundtrip("42xyz"));
        assertTrue(testMatchWithDifferentSalt("aB", "aB4fMcNOggJoQ"));
    }

    private boolean testEncodingRoundtrip(final String algorithm) {
        final GlibcCryptPasswordEncoder encoder = new GlibcCryptPasswordEncoder(algorithm, 0, null);
        // Encode
        final String passwordHash = encoder.encode(passwordClear);
        LOGGER.debug("Password [{}] was encoded by algorithm [{}] to hash [{}]", passwordClear, algorithm, passwordHash);
        // Match
        final boolean match = encoder.matches(passwordClear, passwordHash);
        LOGGER.debug("Does password [{}] match original password [{}]: {}", passwordHash, passwordClear, match);
        // Check
        return match;
    }

    private boolean testMatchWithDifferentSalt(final String algorithm, final String encodedPassword) {
        final GlibcCryptPasswordEncoder encoder = new GlibcCryptPasswordEncoder(algorithm, 0, null);
        final boolean match = encoder.matches(passwordClear, encodedPassword);
        LOGGER.debug("Does password [{}] match original password [{}]: {}", encodedPassword, passwordClear, match);
        return match;
    }

}
