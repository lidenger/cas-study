package org.apereo.cas.authentication.support.password;

import org.apereo.cas.authentication.AuthenticationPasswordPolicyHandlingStrategy;
import org.apereo.cas.authentication.MessageDescriptor;
import org.apereo.cas.util.scripting.WatchableGroovyScriptResource;
import org.apereo.cas.util.spring.ApplicationContextProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * This is {@link GroovyPasswordPolicyHandlingStrategy}.
 *
 * @author Misagh Moayyed
 * @since 5.2.0
 */
@Slf4j
@RequiredArgsConstructor
public class GroovyPasswordPolicyHandlingStrategy<AuthenticationResponse> implements
    AuthenticationPasswordPolicyHandlingStrategy<AuthenticationResponse, PasswordPolicyConfiguration> {
    private final transient WatchableGroovyScriptResource watchableScript;

    public GroovyPasswordPolicyHandlingStrategy(final Resource groovyScript) {
        this.watchableScript = new WatchableGroovyScriptResource(groovyScript);
    }

    @Override
    public List<MessageDescriptor> handle(final AuthenticationResponse response,
                                          final PasswordPolicyConfiguration configuration) {
        final ApplicationContext applicationContext = ApplicationContextProvider.getApplicationContext();
        final Object[] args = new Object[]{response, configuration, LOGGER, applicationContext};
        return watchableScript.execute(args, List.class);
    }
}
