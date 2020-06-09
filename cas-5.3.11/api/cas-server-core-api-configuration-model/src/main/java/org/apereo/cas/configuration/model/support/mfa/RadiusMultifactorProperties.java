package org.apereo.cas.configuration.model.support.mfa;

import org.apereo.cas.configuration.model.support.radius.RadiusClientProperties;
import org.apereo.cas.configuration.model.support.radius.RadiusServerProperties;
import org.apereo.cas.configuration.support.RequiresModule;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * This is {@link RadiusMultifactorProperties}.
 *
 * @author Misagh Moayyed
 * @since 5.2.0
 */
@RequiresModule(name = "cas-server-support-radius-mfa")

@Getter
@Setter
public class RadiusMultifactorProperties extends BaseMultifactorProviderProperties {

    /**
     * Provider id by default.
     */
    public static final String DEFAULT_IDENTIFIER = "mfa-radius";

    private static final long serialVersionUID = 7021301814775348087L;

    /**
     * In the event that radius authentication fails due to a catastrophic event,
     * fail over to the next server in the list.
     */
    private boolean failoverOnException;

    /**
     * In the event that radius authentication fails,
     * fail over to the next server in the list.
     */
    private boolean failoverOnAuthenticationFailure;

    /**
     * RADIUS server settings.
     */
    @NestedConfigurationProperty
    private RadiusServerProperties server = new RadiusServerProperties();

    /**
     * RADIUS client settings.
     */
    @NestedConfigurationProperty
    private RadiusClientProperties client = new RadiusClientProperties();

    /**
     * Indicates whether this provider should support trusted devices.
     */
    private boolean trustedDeviceEnabled;

    /**
     * Total number of allowed authentication attempts
     * with the radius mfa server before the authentication event
     * is considered cancelled. A negative/zero value indicates
     * that no limit is enforced.
     */
    private long allowedAuthenticationAttempts = -1;

    public RadiusMultifactorProperties() {
        setId(DEFAULT_IDENTIFIER);
    }
}
