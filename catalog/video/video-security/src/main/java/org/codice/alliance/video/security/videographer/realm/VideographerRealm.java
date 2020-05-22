/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.alliance.video.security.videographer.realm;

import ddf.security.assertion.SecurityAssertion;
import ddf.security.assertion.impl.AttributeDefault;
import ddf.security.assertion.impl.AttributeStatementDefault;
import ddf.security.assertion.impl.DefaultSecurityAssertionBuilder;
import ddf.security.audit.SecurityLogger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.codice.alliance.video.security.videographer.principal.VideographerPrincipal;
import org.codice.alliance.video.security.videographer.token.VideographerAuthenticationToken;
import org.codice.ddf.security.handler.BaseAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VideographerRealm extends AuthenticatingRealm {
  private static final Logger LOGGER = LoggerFactory.getLogger(VideographerRealm.class);

  private Map<URI, List<String>> claimsMap = new HashMap<>();

  private SecurityLogger securityLogger;

  /** Determine if the supplied token is supported by this realm. */
  @Override
  public boolean supports(AuthenticationToken token) {
    boolean supported =
        token != null
            && token.getCredentials() != null
            && token instanceof VideographerAuthenticationToken;

    if (supported) {
      LOGGER.debug("The supplied authentication token is supported by {}.", getClass().getName());
    } else if (token != null) {
      LOGGER.debug(
          "The supplied authentication token is not supported by {}.", getClass().getName());
    } else {
      LOGGER.debug("The supplied authentication token is null. Sending back not supported.");
    }

    return supported;
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken)
      throws AuthenticationException {
    BaseAuthenticationToken baseAuthenticationToken = (BaseAuthenticationToken) authenticationToken;

    SimpleAuthenticationInfo simpleAuthenticationInfo = new SimpleAuthenticationInfo();
    SimplePrincipalCollection principals = createPrincipalFromToken(baseAuthenticationToken);
    simpleAuthenticationInfo.setPrincipals(principals);
    simpleAuthenticationInfo.setCredentials(authenticationToken.getCredentials());

    securityLogger.audit(
        "Guest assertion generated for IP address: " + baseAuthenticationToken.getIpAddress());
    return simpleAuthenticationInfo;
  }

  private SimplePrincipalCollection createPrincipalFromToken(BaseAuthenticationToken token) {
    AttributeStatementDefault attributeStatement = new AttributeStatementDefault();
    for (Map.Entry<URI, List<String>> entry : claimsMap.entrySet()) {
      AttributeDefault attribute = new AttributeDefault();
      attribute.setName(entry.getKey().toString());

      for (String value : entry.getValue()) {
        attribute.addValue(value);
      }
      attributeStatement.addAttribute(attribute);
    }

    DefaultSecurityAssertionBuilder defaultSecurityAssertionBuilder =
        new DefaultSecurityAssertionBuilder();
    defaultSecurityAssertionBuilder.addAttributeStatement(attributeStatement);
    defaultSecurityAssertionBuilder.userPrincipal(new VideographerPrincipal(token.getIpAddress()));
    defaultSecurityAssertionBuilder.issuer("local");
    defaultSecurityAssertionBuilder.notBefore(new Date());
    defaultSecurityAssertionBuilder.notOnOrAfter(new Date(new Date().getTime() + 14400000L));
    defaultSecurityAssertionBuilder.token(token);
    defaultSecurityAssertionBuilder.tokenType("Videographer");

    SecurityAssertion securityAssertion = defaultSecurityAssertionBuilder.build();
    SimplePrincipalCollection principals = new SimplePrincipalCollection();
    Principal principal = securityAssertion.getPrincipal();
    if (principal != null) {
      principals.add(principal.getName(), getName());
    }
    principals.add(securityAssertion, getName());
    return principals;
  }

  public void setAttributes(List<String> attributes) {
    if (attributes != null) {
      LOGGER.debug("Attribute value list was set.");
      List<String> attrs = new ArrayList<>(attributes.size());
      attrs.addAll(attributes);
      initClaimsMap(attrs);
    } else {
      LOGGER.debug("Set attribute value list was null");
    }
  }

  private void initClaimsMap(List<String> attributes) {
    for (String attr : attributes) {
      String[] claimMapping = attr.split("=");
      if (claimMapping.length == 2) {
        try {
          List<String> values = new ArrayList<>();
          if (claimMapping[1].contains("|")) {
            String[] valsArr = claimMapping[1].split("\\|");
            Collections.addAll(values, valsArr);
          } else {
            values.add(claimMapping[1]);
          }
          claimsMap.put(new URI(claimMapping[0]), values);
        } catch (URISyntaxException e) {
          LOGGER.info(
              "Claims mapping cannot be converted to a URI. This claim will be excluded: {}",
              attr,
              e);
        }
      } else {
        LOGGER.warn("Invalid claims mapping entered for guest user: {}", attr);
      }
    }
  }

  public void setSecurityLogger(SecurityLogger securityLogger) {
    this.securityLogger = securityLogger;
  }
}
