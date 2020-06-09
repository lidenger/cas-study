package org.apereo.cas.util.crypto;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.Crypt;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.util.gen.AbstractRandomStringGenerator;
import org.apereo.cas.util.gen.HexRandomStringGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * this is {@link GlibcCryptPasswordEncoder}.
 *
 * @author Martin Böhmer
 * @since 5.3.10
 */
@Slf4j
@AllArgsConstructor
public class GlibcCryptPasswordEncoder implements PasswordEncoder {

    private static final int SALT_LENGTH = 8;

    private final String encodingAlgorithm;
    private final int strength;
    private String secret;

    @Override
    public String encode(final CharSequence password) {
        if (password == null) {
            return null;
        }

        if (StringUtils.isBlank(this.encodingAlgorithm)) {
            LOGGER.warn("No encoding algorithm is defined. Password cannot be encoded; Returning null");
            return null;
        }

        return Crypt.crypt(password.toString(), generateCryptSalt());
    }

    @Override
    public boolean matches(final CharSequence rawPassword, final String encodedPassword) {
        if (StringUtils.isBlank(encodedPassword)) {
            LOGGER.warn("The encoded password provided for matching is null. Returning false");
            return false;
        }
        final String providedSalt;
        final int lastDollarIndex = encodedPassword.lastIndexOf('$');
        if (lastDollarIndex == -1) {
            // DES UnixCrypt, so first two letters are the salt
            providedSalt = encodedPassword.substring(0, 2);
            LOGGER.debug("Assuming DES UnixCrypt as no delimiter could be found in the encoded password provided");
        } else {
            // Other algorithms
            providedSalt = encodedPassword.substring(0, lastDollarIndex);
            LOGGER.debug("Encoded password uses algorithm [{}]", providedSalt.charAt(1));
        }
        final String encodedRawPassword = Crypt.crypt(rawPassword.toString(), providedSalt);
        final boolean matched = StringUtils.equals(encodedRawPassword, encodedPassword);
        LOGGER.debug("Provided password does {}match the encoded password", BooleanUtils.toString(matched, StringUtils.EMPTY, "not "));
        return matched;
    }

    private String generateCryptSalt() {
        if (StringUtils.isBlank(this.encodingAlgorithm)) {
            return null;
        }
        final StringBuilder cryptSalt = new StringBuilder();
        if ("1".equals(this.encodingAlgorithm) || "MD5".equals(this.encodingAlgorithm.toUpperCase())) {
            // MD5
            cryptSalt.append("$1$");
            LOGGER.debug("Encoding with MD5 algorithm");
        } else if ("5".equals(this.encodingAlgorithm) || "SHA-256".equals(this.encodingAlgorithm.toUpperCase())) {
            // SHA-256
            cryptSalt.append("$5$rounds=").append(this.strength).append('$');
            LOGGER.debug("Encoding with SHA-256 algorithm and {} rounds", this.strength);
        } else if ("6".equals(this.encodingAlgorithm) || "SHA-512".equals(this.encodingAlgorithm.toUpperCase())) {
            // SHA-512
            cryptSalt.append("$6$rounds=").append(this.strength).append('$');
            LOGGER.debug("Encoding with SHA-512 algorithm and {} rounds", this.strength);
        } else {
            // DES UnixCrypt algorithm
            cryptSalt.append(this.encodingAlgorithm);
            LOGGER.debug("Encoding with DES UnixCrypt algorithm as no indicator for another algorithm was found.");
        }
        // Add real salt
        if (StringUtils.isBlank(this.secret)) {
            LOGGER.debug("No secret was found. Generating a salt with length {}", SALT_LENGTH);
            final AbstractRandomStringGenerator keygen = new HexRandomStringGenerator(SALT_LENGTH);
            this.secret = keygen.getNewString();
        } else {
            LOGGER.debug("The provided secrect is used as a salt");
        }
        cryptSalt.append(this.secret);
        // Done
        return cryptSalt.toString();
    }

}
