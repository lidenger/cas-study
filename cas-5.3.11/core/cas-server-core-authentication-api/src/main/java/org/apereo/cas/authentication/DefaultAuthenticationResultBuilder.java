package org.apereo.cas.authentication;

import org.apereo.cas.authentication.principal.Principal;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.util.CollectionUtils;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apereo.services.persondir.support.merger.IAttributeMerger;
import org.apereo.services.persondir.support.merger.MultivaluedAttributeMerger;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This is {@link DefaultAuthenticationResultBuilder}.
 *
 * @author Misagh Moayyed
 * @since 4.2.0
 */
@Slf4j
@NoArgsConstructor
public class DefaultAuthenticationResultBuilder implements AuthenticationResultBuilder {

    private static final long serialVersionUID = 6180465589526463843L;

    private final List<Credential> providedCredentials = new ArrayList<>();

    private final Set<Authentication> authentications = Collections.synchronizedSet(new LinkedHashSet<>());

    @Override
    public Optional<Authentication> getInitialAuthentication() {
        if (this.authentications.isEmpty()) {
            LOGGER.warn("Authentication chain is empty as no authentications have been collected");
        }

        return this.authentications.stream().findFirst();
    }

    @Override
    public AuthenticationResultBuilder collect(final Authentication authentication) {
        if (authentication != null) {
            this.authentications.add(authentication);
        }
        return this;
    }

    @Override
    public AuthenticationResultBuilder collect(final Credential credential) {
        if (credential != null) {
            this.providedCredentials.add(credential);
        }
        return this;
    }

    @Override
    public AuthenticationResult build(final PrincipalElectionStrategy principalElectionStrategy) {
        return build(principalElectionStrategy, null);
    }

    @Override
    public AuthenticationResult build(final PrincipalElectionStrategy principalElectionStrategy, final Service service) {
        final Authentication authentication = buildAuthentication(principalElectionStrategy);
        if (authentication == null) {
            LOGGER.info("Authentication result cannot be produced because no authentication is recorded into in the chain. Returning null");
            return null;
        }
        LOGGER.debug("Building an authentication result for authentication [{}] and service [{}]", authentication, service);
        final DefaultAuthenticationResult res = new DefaultAuthenticationResult(authentication, service);
        res.setCredentialProvided(!this.providedCredentials.isEmpty());
        return res;
    }

    private boolean isEmpty() {
        return this.authentications.isEmpty();
    }

    private Authentication buildAuthentication(final PrincipalElectionStrategy principalElectionStrategy) {
        if (isEmpty()) {
            LOGGER.warn("No authentication event has been recorded; CAS cannot finalize the authentication result");
            return null;
        }
        final Map<String, Object> authenticationAttributes = new HashMap<>();
        final Map<String, Object> principalAttributes = new HashMap<>();
        final AuthenticationBuilder authenticationBuilder = DefaultAuthenticationBuilder.newInstance();

        buildAuthenticationHistory(this.authentications, authenticationAttributes, principalAttributes, authenticationBuilder);
        final Principal primaryPrincipal = getPrimaryPrincipal(principalElectionStrategy, this.authentications, principalAttributes);
        authenticationBuilder.setPrincipal(primaryPrincipal);
        LOGGER.debug("Determined primary authentication principal to be [{}]", primaryPrincipal);

        authenticationBuilder.setAttributes(authenticationAttributes);
        LOGGER.debug("Collected authentication attributes for this result are [{}]", authenticationAttributes);

        authenticationBuilder.setAuthenticationDate(ZonedDateTime.now());
        final Authentication auth = authenticationBuilder.build();
        LOGGER.debug("Authentication result commenced at [{}]", auth.getAuthenticationDate());
        return auth;
    }

    private static void buildAuthenticationHistory(final Set<Authentication> authentications,
                                                   final Map<String, Object> authenticationAttributes,
                                                   final Map<String, Object> principalAttributes,
                                                   final AuthenticationBuilder authenticationBuilder) {

        LOGGER.debug("Collecting authentication history based on [{}] authentication events", authentications.size());
        authentications.forEach(authn -> {
            final Principal authenticatedPrincipal = authn.getPrincipal();
            LOGGER.debug("Evaluating authentication principal [{}] for inclusion in result", authenticatedPrincipal);

            principalAttributes.putAll(mergeAttributes(principalAttributes, authenticatedPrincipal.getAttributes()));
            LOGGER.debug("Collected principal attributes [{}] for inclusion in this result for principal [{}]",
                principalAttributes, authenticatedPrincipal.getId());

            authenticationAttributes.putAll(mergeAttributes(authenticationAttributes, authn.getAttributes()));
            LOGGER.debug("Finalized authentication attributes [{}] for inclusion in this authentication result", authenticationAttributes);

            authenticationBuilder
                .addSuccesses(authn.getSuccesses())
                .addFailures(authn.getFailures())
                .addCredentials(authn.getCredentials());
        });
    }

    /**
     * Principal id is and must be enforced to be the same for all authentications.
     * Based on that restriction, it's safe to simply grab the first principal id in the chain
     * when composing the authentication chain for the caller.
     */
    private static Principal getPrimaryPrincipal(final PrincipalElectionStrategy principalElectionStrategy,
                                                 final Set<Authentication> authentications,
                                                 final Map<String, Object> principalAttributes) {
        return principalElectionStrategy.nominate(new LinkedHashSet<>(authentications), principalAttributes);
    }

    private static Map<String, Object> mergeAttributes(final Map<String, Object> currentAttributes, final Map<String, Object> attributesToMerge) {
        final IAttributeMerger merger = new MultivaluedAttributeMerger();

        final Map toModify = currentAttributes.entrySet()
            .stream()
            .map(entry -> Pair.of(entry.getKey(), CollectionUtils.toCollection(entry.getValue(), ArrayList.class)))
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        final Map toMerge = attributesToMerge.entrySet()
            .stream()
            .map(entry -> Pair.of(entry.getKey(), CollectionUtils.toCollection(entry.getValue(), ArrayList.class)))
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        LOGGER.debug("Merging current attributes [{}] with [{}]", currentAttributes, attributesToMerge);
        final Map results = merger.mergeAttributes((Map) toModify, (Map) toMerge);
        LOGGER.debug("Merged attributes with the final result as [{}]", results);
        return results;
    }
}
