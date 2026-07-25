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

package org.smallcrm.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.smallcrm.domain.Contact;
import org.smallcrm.domain.Deal;
import org.smallcrm.support.AbstractApiTest;

/** CRUD, pipeline filtering and stage transitions for deals. */
@QuarkusTest
@TestSecurity(user = "admin", roles = {"ADMIN", "USER"})
class DealResourceTest extends AbstractApiTest {

  @Test
  void a_new_deal_starts_as_a_lead_in_euro() {
    Contact contact = fixtures.createContact("Maria", "Huber", null);

    Map<String, Object> body = new HashMap<>();
    body.put("title", "Website relaunch");
    body.put("contactId", contact.id);
    body.put("amount", 4500.00);

    given()
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post("/api/deals")
        .then()
        .statusCode(201)
        .body("stage", is("LEAD"))
        .body("currency", is("EUR"))
        .body("contactName", is("Maria Huber"))
        .body("amount", is(4500.0f));
  }

  @Test
  void the_currency_is_normalised_to_upper_case() {
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("title", "Consulting", "currency", "chf"))
        .when()
        .post("/api/deals")
        .then()
        .statusCode(201)
        .body("currency", is("CHF"));
  }

  @Test
  void a_deal_needs_a_title_and_a_non_negative_amount() {
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("title", "", "amount", -1))
        .when()
        .post("/api/deals")
        .then()
        .statusCode(400)
        .body("details.title", is("must not be blank"))
        .body("details.amount", is("must be greater than or equal to 0"));
  }

  @Test
  void the_pipeline_can_be_filtered_by_stage_and_to_open_deals_only() {
    Deal lead = fixtures.createDeal("Lead deal", null);
    Deal won = fixtures.createDeal("Won deal", null);
    moveTo(won.id, "WON");

    given().when().get("/api/deals").then().body("$", hasSize(2));

    given()
        .queryParam("openOnly", true)
        .when()
        .get("/api/deals")
        .then()
        .body("$", hasSize(1))
        .body("[0].id", is(lead.id.intValue()));

    given()
        .queryParam("stage", "WON")
        .when()
        .get("/api/deals")
        .then()
        .body("title", contains("Won deal"));
  }

  @Test
  void moving_a_deal_to_another_stage_requires_a_target() {
    Deal deal = fixtures.createDeal("Consulting", null);

    given()
        .when()
        .put("/api/deals/" + deal.id + "/stage")
        .then()
        .statusCode(400)
        .body("code", is("STAGE_REQUIRED"));

    moveTo(deal.id, "PROPOSAL");
    given().when().get("/api/deals/" + deal.id).then().body("stage", is("PROPOSAL"));
  }

  @Test
  void deleting_a_deal_detaches_the_records_that_pointed_at_it() {
    Contact contact = fixtures.createContact("Maria", "Huber", null);
    Deal deal = fixtures.createDeal("Website relaunch", contact);
    var interaction = fixtures.createInteraction(contact, java.time.Instant.now());
    attachDeal(interaction.id, contact.id, deal.id);

    given().when().delete("/api/deals/" + deal.id).then().statusCode(204);

    given().when().get("/api/deals/" + deal.id).then().statusCode(404);
    given()
        .when()
        .get("/api/interactions/" + interaction.id)
        .then()
        .statusCode(200)
        .body("dealId", nullValue());
  }

  private static void moveTo(Long dealId, String stage) {
    given()
        .queryParam("value", stage)
        .when()
        .put("/api/deals/" + dealId + "/stage")
        .then()
        .statusCode(200)
        .body("stage", is(stage));
  }

  private static void attachDeal(Long interactionId, Long contactId, Long dealId) {
    Map<String, Object> body = new HashMap<>();
    body.put("type", "CALL");
    body.put("subject", "Kickoff call");
    body.put("occurredAt", "2026-07-01T09:00:00Z");
    body.put("contactId", contactId);
    body.put("dealId", dealId);
    given()
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .put("/api/interactions/" + interactionId)
        .then()
        .statusCode(200);
  }
}
