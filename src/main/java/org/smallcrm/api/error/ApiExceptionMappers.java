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

package org.smallcrm.api.error;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Translates the application's exceptions into the single {@link ApiError} response shape, so the
 * frontend only ever has to understand one error format.
 */
public class ApiExceptionMappers {

  private static final Logger LOG = Logger.getLogger(ApiExceptionMappers.class);

  @ServerExceptionMapper
  public Response handleNotFound(NotFoundException exception) {
    return Response.status(Response.Status.NOT_FOUND)
        .entity(ApiError.of("NOT_FOUND", exception.getMessage()))
        .build();
  }

  @ServerExceptionMapper
  public Response handleBusinessRule(BusinessRuleException exception) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(new ApiError(exception.code(), exception.getMessage(), exception.details()))
        .build();
  }

  @ServerExceptionMapper
  public Response handleConflict(ConflictException exception) {
    return Response.status(Response.Status.CONFLICT)
        .entity(ApiError.of(exception.code(), exception.getMessage()))
        .build();
  }

  /**
   * Two people saved the same record at once. Hibernate detects it at flush; the client is told
   * to reload rather than being left believing its write landed.
   */
  @ServerExceptionMapper
  public Response handleOptimisticLock(OptimisticLockException exception) {
    return Response.status(Response.Status.CONFLICT)
        .entity(
            ApiError.of(
                "STALE_VERSION",
                "This record was changed by somebody else while you were editing it"))
        .build();
  }

  /**
   * Last resort, so an unforeseen failure still arrives in the shape the frontend understands
   * instead of a framework page. The cause is logged rather than returned.
   */
  @ServerExceptionMapper(priority = Priorities.USER + 1000)
  public Response handleAnythingElse(Throwable exception) {
    if (exception instanceof WebApplicationException web) {
      return web.getResponse();
    }
    LOG.error("Unhandled failure while serving a request", exception);
    return Response.serverError()
        .entity(ApiError.of("INTERNAL", "Something went wrong. Nothing was saved."))
        .build();
  }

  @ServerExceptionMapper
  public Response handleAppointmentConflict(AppointmentConflictException exception) {
    return Response.status(Response.Status.CONFLICT)
        .entity(
            new ApiError(
                "APPOINTMENT_CONFLICT",
                exception.getMessage(),
                Map.of("conflicts", exception.conflicts())))
        .build();
  }

  /**
   * Replaces the built-in bean validation mapper so that field errors arrive as a simple
   * {@code field -> message} map the Angular forms can bind to directly.
   */
  @ServerExceptionMapper(priority = Priorities.USER - 100)
  public Response handleConstraintViolation(ConstraintViolationException exception) {
    Map<String, Object> fields = new LinkedHashMap<>();
    for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
      fields.put(lastPathSegment(violation), violation.getMessage());
    }
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(new ApiError("VALIDATION_FAILED", "One or more fields are invalid", fields))
        .build();
  }

  private static String lastPathSegment(ConstraintViolation<?> violation) {
    String path = violation.getPropertyPath().toString();
    int lastDot = path.lastIndexOf('.');
    return lastDot < 0 ? path : path.substring(lastDot + 1);
  }
}
