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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.os890.smallcrm.domain.Company;
import org.os890.smallcrm.domain.Contact;
import org.os890.smallcrm.support.AbstractApiTest;

/** CRUD, search, tags and cascade behaviour for contacts. */
@QuarkusTest
@TestSecurity(user = "admin", roles = {"ADMIN", "USER"})
class ContactResourceTest extends AbstractApiTest {

  @Test
  void a_contact_can_be_created_with_a_company_and_tags() {
    Company company = fixtures.createCompany("Muster GmbH");

    Map<String, Object> body = new HashMap<>();
    body.put("firstName", "Maria");
    body.put("lastName", "Huber");
    body.put("email", "maria@example.org");
    body.put("companyId", company.id);
    body.put("tags", List.of("vip", " lead ", "", "vip"));

    given()
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post("/api/contacts")
        .then()
        .statusCode(201)
        .body("displayName", is("Maria Huber"))
        .body("companyName", is("Muster GmbH"))
        .body("tags", containsInAnyOrder("vip", "lead"));
  }

  @Test
  void first_and_last_name_are_mandatory() {
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("firstName", "", "lastName", ""))
        .when()
        .post("/api/contacts")
        .then()
        .statusCode(400)
        .body("code", is("VALIDATION_FAILED"))
        .body("details.firstName", is("must not be blank"))
        .body("details.lastName", is("must not be blank"));
  }

  @Test
  void referencing_a_company_that_does_not_exist_is_rejected() {
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("firstName", "Ghost", "lastName", "Writer", "companyId", 9999))
        .when()
        .post("/api/contacts")
        .then()
        .statusCode(404)
        .body("code", is("NOT_FOUND"));
  }

  @Test
  void contacts_are_sorted_by_last_name_and_can_be_filtered() {
    Company company = fixtures.createCompany("Muster GmbH");
    fixtures.createContact("Anna", "Zimmermann", company);
    fixtures.createContact("Bernd", "Aigner", null);

    given()
        .when()
        .get("/api/contacts")
        .then()
        .statusCode(200)
        .body("lastName", contains("Aigner", "Zimmermann"));

    given()
        .queryParam("search", "zimmer")
        .when()
        .get("/api/contacts")
        .then()
        .body("$", hasSize(1))
        .body("[0].firstName", is("Anna"));

    // The e-mail address is searched too.
    given()
        .queryParam("search", "bernd.aigner@")
        .when()
        .get("/api/contacts")
        .then()
        .body("$", hasSize(1));

    given()
        .queryParam("companyId", company.id)
        .when()
        .get("/api/contacts")
        .then()
        .body("$", hasSize(1))
        .body("[0].lastName", is("Zimmermann"));
  }

  @Test
  void the_tag_endpoint_lists_every_tag_in_use_once() {
    fixtures.createContact("Anna", "Berger", null);
    fixtures.createContact("Bernd", "Aigner", null);

    given()
        .when()
        .get("/api/contacts/tags")
        .then()
        .statusCode(200)
        .body("$", contains("poc"));
  }

  @Test
  void updating_can_clear_the_company_and_replace_the_tags() {
    Company company = fixtures.createCompany("Muster GmbH");
    Contact contact = fixtures.createContact("Maria", "Huber", company);

    given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "firstName", "Maria",
                "lastName", "Huber-Berger",
                "tags", List.of("customer")))
        .when()
        .put("/api/contacts/" + contact.id)
        .then()
        .statusCode(200)
        .body("lastName", is("Huber-Berger"))
        .body("companyId", nullValue())
        .body("tags", contains("customer"));
  }

  @Test
  void deleting_a_contact_removes_its_interactions_and_detaches_the_rest() {
    Contact contact = fixtures.createContact("Maria", "Huber", null);
    var interaction = fixtures.createInteraction(contact, Instant.parse("2026-07-01T09:00:00Z"));
    var deal = fixtures.createDeal("Website relaunch", contact);

    given().when().delete("/api/contacts/" + contact.id).then().statusCode(204);

    given().when().get("/api/contacts/" + contact.id).then().statusCode(404);
    given().when().get("/api/interactions/" + interaction.id).then().statusCode(404);
    given()
        .when()
        .get("/api/deals/" + deal.id)
        .then()
        .statusCode(200)
        .body("contactId", nullValue());
  }
}
