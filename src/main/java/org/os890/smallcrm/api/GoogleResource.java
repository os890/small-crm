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

package org.os890.smallcrm.api;

import io.quarkus.security.Authenticated;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jboss.logging.Logger;
import org.os890.smallcrm.api.dto.GoogleStatusDto;
import org.os890.smallcrm.api.error.BusinessRuleException;
import org.os890.smallcrm.domain.AppUser;
import org.os890.smallcrm.google.GoogleConnectionService;
import org.os890.smallcrm.google.GoogleStatusService;
import org.os890.smallcrm.google.sync.GoogleSyncService;
import org.os890.smallcrm.google.sync.SyncReport;
import org.os890.smallcrm.security.CurrentUser;
import org.os890.smallcrm.security.SessionCookie;
import org.os890.smallcrm.security.SessionService;

/**
 * Connecting a Google account, disconnecting it, and signing in with one.
 *
 * <p>The consent round trip is a browser redirect, so two of these are navigations rather than
 * API calls: the browser leaves for Google and comes back to {@link #callback}. Everything the
 * callback can do ends in a redirect back into the application with a short reason in the query
 * string, because there is nowhere else to show an error to a browser that has just returned
 * from somebody else's website.
 */
@Path("/google")
@Produces(MediaType.APPLICATION_JSON)
public class GoogleResource {

  private static final Logger LOG = Logger.getLogger(GoogleResource.class);

  @Inject GoogleConnectionService connections;
  @Inject GoogleStatusService status;
  @Inject GoogleSyncService sync;
  @Inject CurrentUser currentUser;
  @Inject SessionService sessions;
  @Inject SessionCookie cookie;
  @Inject RoutingContext routingContext;

  /** Whether the integration is available, and what the signed-in user has connected. */
  @GET
  @Path("/status")
  @Authenticated
  public GoogleStatusDto status() {
    return status.forUser(currentUser.get());
  }

  /**
   * Whether signing in with Google is offered at all.
   *
   * <p>Reachable without a session because the login screen has to ask before anybody has one.
   * It says only whether the feature is configured, never who is connected.
   */
  @GET
  @Path("/available")
  public GoogleStatusDto available() {
    return GoogleStatusDto.availability(connections.isEnabled());
  }

  /** Where to send the browser to connect the signed-in user's Google account. */
  @POST
  @Path("/connect")
  @Authenticated
  public Response connect() {
    return Response.ok(new Redirect(connections.startLinking(currentUser.get()))).build();
  }

  /** Where to send the browser to sign in with an already connected Google account. */
  @POST
  @Path("/signin")
  public Response signIn() {
    return Response.ok(new Redirect(connections.startSignIn())).build();
  }

  /**
   * Runs all three syncs now, and reports each separately.
   *
   * <p>Synchronous on purpose: the person pressed a button and wants to know what happened, and
   * for one user's address book and calendar this is seconds rather than minutes.
   */
  @POST
  @Path("/sync")
  @Authenticated
  public List<SyncReport> syncNow() {
    return sync.syncAll(currentUser.get());
  }

  /** Withdraws the connection here and the grant at Google. */
  @DELETE
  @Path("/connection")
  @Authenticated
  public Response disconnect() {
    connections.disconnect(currentUser.get());
    return Response.noContent().build();
  }

  /**
   * Where Google sends the browser back to.
   *
   * <p>Unauthenticated by necessity: a sign-in arrives here with no session yet. The state value
   * is what makes it safe — it is single use, expires in minutes, and was minted by this
   * installation.
   */
  @GET
  @Path("/callback")
  public Response callback(
      @QueryParam("code") String code,
      @QueryParam("state") String state,
      @QueryParam("error") String error) {
    if (error != null && !error.isBlank()) {
      // The user pressed "Cancel" on Google's consent screen, which is not a failure.
      return redirectTo("/settings", "google", "cancelled");
    }
    try {
      GoogleConnectionService.Outcome outcome = connections.complete(code, state);
      if (outcome instanceof GoogleConnectionService.Outcome.SignedIn signedIn) {
        issueSession(signedIn.user());
        return redirectTo("/", "google", "signed-in");
      }
      return redirectTo("/settings", "google", "connected");
    } catch (BusinessRuleException e) {
      LOG.warnf("Google callback refused: %s", e.getMessage());
      // The code travels in the query string so the interface can say something specific; the
      // message itself is not passed through, since it would end up in browser history and in
      // any proxy log between here and the user.
      return redirectTo(state == null ? "/login" : "/settings", "googleError", e.code());
    }
  }

  /** Signs the user in exactly as a password login does, so there is one kind of session. */
  private void issueSession(AppUser user) {
    SessionService.IssuedSession issued = sessions.issue(user);
    cookie.write(
        routingContext,
        issued.token(),
        Duration.between(Instant.now(), issued.expiresAt()).toSeconds());
  }

  private static Response redirectTo(String path, String parameter, String value) {
    String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8);
    return Response.seeOther(URI.create(path + "?" + parameter + "=" + encoded)).build();
  }

  /** Where the browser should go next; the frontend navigates rather than following a 302. */
  public record Redirect(String url) {}
}
