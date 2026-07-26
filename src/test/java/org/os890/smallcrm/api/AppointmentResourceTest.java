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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.os890.smallcrm.domain.AppUser;
import org.os890.smallcrm.support.AbstractApiTest;

/**
 * The double booking guard is the feature most likely to hurt a user if it is wrong, so it is
 * covered from every angle: overlapping, touching, self-overlap on edit, other owners and the
 * deliberate override.
 */
@QuarkusTest
@TestSecurity(user = "admin", roles = {"ADMIN", "USER"})
class AppointmentResourceTest extends AbstractApiTest {

  private static final String NINE = "2026-09-01T09:00:00Z";
  private static final String TEN = "2026-09-01T10:00:00Z";
  private static final String HALF_PAST_TEN = "2026-09-01T10:30:00Z";
  private static final String ELEVEN = "2026-09-01T11:00:00Z";
  private static final String TWELVE = "2026-09-01T12:00:00Z";

  @Test
  void an_appointment_can_be_created_and_read_back() {
    Integer id =
        create("Client meeting", TEN, ELEVEN, false)
            .statusCode(201)
            .body("ownerName", is("admin"))
            .body("timeZone", is("UTC"))
            .extract()
            .path("id");

    given()
        .when()
        .get("/api/appointments/" + id)
        .then()
        .statusCode(200)
        .body("title", is("Client meeting"));
  }

  @Test
  void an_overlapping_slot_is_refused_and_names_the_conflict() {
    create("Client meeting", TEN, ELEVEN, false).statusCode(201);

    create("Dentist", HALF_PAST_TEN, TWELVE, false)
        .statusCode(409)
        .body("code", is("APPOINTMENT_CONFLICT"))
        .body("details.conflicts", hasSize(1))
        .body("details.conflicts[0].title", is("Client meeting"));
  }

  @Test
  void back_to_back_appointments_are_allowed() {
    create("Client meeting", TEN, ELEVEN, false).statusCode(201);

    create("Follow-up", ELEVEN, TWELVE, false).statusCode(201);
    create("Earlier call", NINE, TEN, false).statusCode(201);
  }

  @Test
  void an_overlap_can_be_accepted_on_purpose() {
    create("Client meeting", TEN, ELEVEN, false).statusCode(201);

    create("Parallel webinar", HALF_PAST_TEN, TWELVE, true).statusCode(201);

    given().queryParam("from", NINE).queryParam("to", TWELVE)
        .when().get("/api/appointments").then().body("$", hasSize(2));
  }

  @Test
  void editing_an_appointment_does_not_conflict_with_itself() {
    Integer id = create("Client meeting", TEN, ELEVEN, false).extract().path("id");

    given()
        .contentType(ContentType.JSON)
        .body(payload("Client meeting (moved)", TEN, TWELVE))
        .when()
        .put("/api/appointments/" + id)
        .then()
        .statusCode(200)
        .body("title", is("Client meeting (moved)"));
  }

  @Test
  void moving_an_appointment_onto_another_one_is_refused() {
    Integer first = create("Client meeting", TEN, ELEVEN, false).extract().path("id");
    create("Dentist", ELEVEN, TWELVE, false).statusCode(201);

    given()
        .contentType(ContentType.JSON)
        .body(payload("Client meeting", HALF_PAST_TEN, TWELVE))
        .when()
        .put("/api/appointments/" + first)
        .then()
        .statusCode(409)
        .body("details.conflicts[0].title", is("Dentist"));
  }

  @Test
  void another_users_calendar_does_not_block_the_slot() {
    AppUser other = fixtures.createUser("colleague", "colleague-secret-x", false);
    fixtures.createAppointment(
        "Their meeting", Instant.parse(TEN), Instant.parse(ELEVEN), other);

    create("My meeting", TEN, ELEVEN, false).statusCode(201);
  }

  @Test
  void the_conflict_endpoint_previews_a_slot_without_storing_anything() {
    create("Client meeting", TEN, ELEVEN, false).statusCode(201);

    given()
        .queryParam("startsAt", HALF_PAST_TEN)
        .queryParam("endsAt", TWELVE)
        .when()
        .get("/api/appointments/conflicts")
        .then()
        .statusCode(200)
        .body("title", contains("Client meeting"));

    given()
        .queryParam("startsAt", ELEVEN)
        .queryParam("endsAt", TWELVE)
        .when()
        .get("/api/appointments/conflicts")
        .then()
        .statusCode(200)
        .body("$", empty());
  }

  @Test
  void the_conflict_endpoint_can_ignore_the_appointment_being_edited() {
    Integer id = create("Client meeting", TEN, ELEVEN, false).extract().path("id");

    given()
        .queryParam("startsAt", TEN)
        .queryParam("endsAt", ELEVEN)
        .queryParam("excludeId", id)
        .when()
        .get("/api/appointments/conflicts")
        .then()
        .body("$", empty());
  }

  @Test
  void the_end_must_lie_after_the_start() {
    create("Backwards", ELEVEN, TEN, false)
        .statusCode(400)
        .body("code", is("END_BEFORE_START"));

    create("Zero length", TEN, TEN, false).statusCode(400).body("code", is("END_BEFORE_START"));
  }

  @Test
  void start_and_end_are_both_required() {
    Map<String, Object> body = new HashMap<>();
    body.put("title", "Incomplete");

    given()
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post("/api/appointments")
        .then()
        .statusCode(400)
        .body("code", is("VALIDATION_FAILED"));
  }

  @Test
  void the_agenda_returns_appointments_that_intersect_the_window() {
    create("Client meeting", TEN, ELEVEN, false).statusCode(201);

    given()
        .queryParam("from", "2026-09-01T10:45:00Z")
        .queryParam("to", TWELVE)
        .when()
        .get("/api/appointments")
        .then()
        .body("$", hasSize(1));

    given()
        .queryParam("from", ELEVEN)
        .queryParam("to", TWELVE)
        .when()
        .get("/api/appointments")
        .then()
        .body("$", empty());
  }

  @Test
  void an_inverted_or_oversized_window_is_rejected() {
    given()
        .queryParam("from", TWELVE)
        .queryParam("to", TEN)
        .when()
        .get("/api/appointments")
        .then()
        .statusCode(400)
        .body("code", is("INVALID_RANGE"));

    given()
        .queryParam("from", "2026-01-01T00:00:00Z")
        .queryParam("to", "2030-01-01T00:00:00Z")
        .when()
        .get("/api/appointments")
        .then()
        .statusCode(400)
        .body("code", is("RANGE_TOO_WIDE"));
  }

  @Test
  void a_malformed_timestamp_is_reported_clearly() {
    given()
        .queryParam("from", "tomorrow")
        .when()
        .get("/api/appointments")
        .then()
        .statusCode(400)
        .body("code", is("INVALID_TIMESTAMP"));
  }

  @Test
  void an_appointment_can_be_deleted() {
    Integer id = create("Client meeting", TEN, ELEVEN, false).extract().path("id");

    given().when().delete("/api/appointments/" + id).then().statusCode(204);
    given().when().get("/api/appointments/" + id).then().statusCode(404);
    given().when().delete("/api/appointments/" + id).then().statusCode(404);
  }

  private static io.restassured.response.ValidatableResponse create(
      String title, String startsAt, String endsAt, boolean allowConflict) {
    return given()
        .contentType(ContentType.JSON)
        .queryParam("allowConflict", allowConflict)
        .body(payload(title, startsAt, endsAt))
        .when()
        .post("/api/appointments")
        .then();
  }

  private static Map<String, Object> payload(String title, String startsAt, String endsAt) {
    Map<String, Object> body = new HashMap<>();
    body.put("title", title);
    body.put("startsAt", startsAt);
    body.put("endsAt", endsAt);
    return body;
  }
}
