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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.os890.smallcrm.domain.Contact;
import org.os890.smallcrm.domain.Deal;
import org.os890.smallcrm.support.AbstractApiTest;

/** CRUD and ordering for the activity log. */
@QuarkusTest
@TestSecurity(user = "admin", roles = {"ADMIN", "USER"})
class InteractionResourceTest extends AbstractApiTest {

  @Test
  void an_interaction_is_always_attached_to_a_contact() {
    Contact contact = fixtures.createContact("Maria", "Huber", null);

    given()
        .contentType(ContentType.JSON)
        .body(payload("Kickoff call", contact.id, "2026-07-01T09:00:00Z"))
        .when()
        .post("/api/interactions")
        .then()
        .statusCode(201)
        .body("contactName", is("Maria Huber"))
        .body("type", is("CALL"));
  }

  @Test
  void the_contact_is_mandatory_and_must_exist() {
    Map<String, Object> withoutContact = payload("Orphan", null, "2026-07-01T09:00:00Z");
    withoutContact.remove("contactId");

    given()
        .contentType(ContentType.JSON)
        .body(withoutContact)
        .when()
        .post("/api/interactions")
        .then()
        .statusCode(400)
        .body("details.contactId", is("must not be null"));

    given()
        .contentType(ContentType.JSON)
        .body(payload("Orphan", 9999L, "2026-07-01T09:00:00Z"))
        .when()
        .post("/api/interactions")
        .then()
        .statusCode(404);
  }

  @Test
  void an_interaction_cannot_be_logged_for_the_future() {
    Contact contact = fixtures.createContact("Maria", "Huber", null);
    String future = Instant.now().plus(2, ChronoUnit.DAYS).toString();

    given()
        .contentType(ContentType.JSON)
        .body(payload("Time travel", contact.id, future))
        .when()
        .post("/api/interactions")
        .then()
        .statusCode(400)
        .body("code", is("OCCURRED_AT_IN_FUTURE"));
  }

  @Test
  void interactions_are_listed_newest_first_and_can_be_filtered() {
    Contact maria = fixtures.createContact("Maria", "Huber", null);
    Contact bernd = fixtures.createContact("Bernd", "Aigner", null);
    Deal deal = fixtures.createDeal("Website relaunch", maria);

    given().contentType(ContentType.JSON)
        .body(payload("Older call", maria.id, "2026-07-01T09:00:00Z"))
        .when().post("/api/interactions").then().statusCode(201);

    Map<String, Object> newer = payload("Newer call", maria.id, "2026-07-10T09:00:00Z");
    newer.put("dealId", deal.id);
    given().contentType(ContentType.JSON).body(newer)
        .when().post("/api/interactions").then().statusCode(201);

    given().contentType(ContentType.JSON)
        .body(payload("Other contact", bernd.id, "2026-07-05T09:00:00Z"))
        .when().post("/api/interactions").then().statusCode(201);

    given()
        .when()
        .get("/api/interactions")
        .then()
        .body("subject", contains("Newer call", "Other contact", "Older call"));

    given()
        .queryParam("contactId", maria.id)
        .when()
        .get("/api/interactions")
        .then()
        .body("$", hasSize(2));

    given()
        .queryParam("dealId", deal.id)
        .when()
        .get("/api/interactions")
        .then()
        .body("subject", contains("Newer call"));

    given()
        .queryParam("contactId", maria.id)
        .queryParam("dealId", deal.id)
        .when()
        .get("/api/interactions")
        .then()
        .body("$", hasSize(1));
  }

  @Test
  void an_interaction_can_be_edited_and_deleted() {
    Contact contact = fixtures.createContact("Maria", "Huber", null);
    Integer id =
        given()
            .contentType(ContentType.JSON)
            .body(payload("Kickoff call", contact.id, "2026-07-01T09:00:00Z"))
            .when()
            .post("/api/interactions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

    Map<String, Object> edited = payload("Kickoff meeting", contact.id, "2026-07-02T09:00:00Z");
    edited.put("type", "MEETING");
    edited.put("notes", "Agreed on the scope");

    given()
        .contentType(ContentType.JSON)
        .body(edited)
        .when()
        .put("/api/interactions/" + id)
        .then()
        .statusCode(200)
        .body("type", is("MEETING"))
        .body("notes", is("Agreed on the scope"));

    given().when().delete("/api/interactions/" + id).then().statusCode(204);
    given().when().get("/api/interactions/" + id).then().statusCode(404);
  }

  private static Map<String, Object> payload(String subject, Long contactId, String occurredAt) {
    Map<String, Object> body = new HashMap<>();
    body.put("type", "CALL");
    body.put("subject", subject);
    body.put("occurredAt", occurredAt);
    body.put("contactId", contactId);
    return body;
  }
}
