/*
 * Copyright 2026 the Small CRM authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.smallcrm.security;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import org.smallcrm.domain.AppUser;

/**
 * Authenticates a request from the session cookie.
 *
 * <p>This replaces the built-in form mechanism. That one issued a self-contained cookie holding
 * {@code "<expiry>:<username>"} encrypted with a configured key, which meant sessions could not
 * be ended server side, a password change did not invalidate them, and knowing the key was
 * enough to mint one for any account. Here the cookie is only a lookup key into
 * {@link SessionService}, so all three follow from the design rather than needing extra
 * machinery.
 *
 * <p>Roles are read from the database on every request, so demoting or deactivating somebody
 * takes effect immediately rather than at their next sign-in.
 */
@ApplicationScoped
public class SessionAuthenticationMechanism implements HttpAuthenticationMechanism {

  @Inject SessionService sessions;
  @Inject SessionCookie cookie;

  @Override
  public Uni<SecurityIdentity> authenticate(
      RoutingContext context, IdentityProviderManager identityProviderManager) {
    String token = cookie.read(context);
    if (token == null || token.isBlank()) {
      // Anonymous. The permission layer decides whether that is acceptable for this path.
      return Uni.createFrom().nullItem();
    }
    // Looking the session up touches the database, which cannot happen on the event loop the
    // mechanism is called on, so the blocking part is moved to a worker thread.
    return Uni.createFrom()
        .item(token)
        .emitOn(Infrastructure.getDefaultWorkerPool())
        .map(
            value -> {
              AppUser user = sessions.authenticate(value);
              if (user == null) {
                // Unknown, expired or revoked. Clearing the cookie stops the browser resending
                // it and keeps the "your session ended" experience predictable.
                cookie.clear(context);
                return null;
              }
              return identityOf(user);
            });
  }

  @Override
  public Uni<ChallengeData> getChallenge(RoutingContext context) {
    // A single page application wants a status code, not a redirect to a login page.
    return Uni.createFrom().item(new ChallengeData(401, null, null));
  }

  @Override
  public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
    // The mechanism resolves the identity itself and never delegates to an identity provider.
    return Set.of();
  }

  @Override
  public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
    return Uni.createFrom()
        .item(
            new HttpCredentialTransport(
                HttpCredentialTransport.Type.COOKIE, SessionCookie.NAME, SessionCookie.NAME));
  }

  private static SecurityIdentity identityOf(AppUser user) {
    QuarkusSecurityIdentity.Builder builder =
        QuarkusSecurityIdentity.builder().setPrincipal(new QuarkusPrincipal(user.username));
    for (String role : user.roles.split(",")) {
      String trimmed = role.trim();
      if (!trimmed.isEmpty()) {
        builder.addRole(trimmed);
      }
    }
    return builder.build();
  }
}
