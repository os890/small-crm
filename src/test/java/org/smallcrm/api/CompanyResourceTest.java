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
import org.smallcrm.support.AbstractApiTest;

/** CRUD, search and detach-on-delete behaviour for companies. */
@QuarkusTest
@TestSecurity(user = "admin", roles = {"ADMIN", "USER"})
class CompanyResourceTest extends AbstractApiTest {

  @Test
  void a_created_company_is_returned_with_an_id_and_an_owner() {
    Map<String, Object> body = new HashMap<>();
    body.put("name", "Muster GmbH");
    body.put("city", "Graz");
    body.put("email", "office@muster.example");

    Integer id =
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/companies")
            .then()
            .statusCode(201)
            .body("name", is("Muster GmbH"))
            .body("ownerName", is("admin"))
            .extract()
            .path("id");

    given()
        .when()
        .get("/api/companies/" + id)
        .then()
        .statusCode(200)
        .body("city", is("Graz"))
        .body("email", is("office@muster.example"));
  }

  @Test
  void a_company_needs_a_name_and_a_well_formed_email() {
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "", "email", "not-an-email"))
        .when()
        .post("/api/companies")
        .then()
        .statusCode(400)
        .body("code", is("VALIDATION_FAILED"))
        .body("details.name", is("must not be blank"));
  }

  @Test
  void companies_are_listed_alphabetically_and_can_be_searched() {
    fixtures.createCompany("Zeta AG");
    fixtures.createCompany("Alpha KG");

    given()
        .when()
        .get("/api/companies")
        .then()
        .statusCode(200)
        .body("name", contains("Alpha KG", "Zeta AG"));

    given()
        .queryParam("search", "zet")
        .when()
        .get("/api/companies")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].name", is("Zeta AG"));

    // The city is searched as well, so "Vienna" finds both fixtures.
    given()
        .queryParam("search", "vien")
        .when()
        .get("/api/companies")
        .then()
        .body("$", hasSize(2));
  }

  @Test
  void updating_replaces_the_editable_fields() {
    Long id = fixtures.createCompany("Old Name").id;

    given()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "New Name", "country", "Austria"))
        .when()
        .put("/api/companies/" + id)
        .then()
        .statusCode(200)
        .body("name", is("New Name"))
        .body("country", is("Austria"))
        .body("city", nullValue());
  }

  @Test
  void deleting_a_company_keeps_its_contacts_but_detaches_them() {
    var company = fixtures.createCompany("Doomed Ltd");
    var contact = fixtures.createContact("Anna", "Berger", company);

    given().when().delete("/api/companies/" + company.id).then().statusCode(204);

    given().when().get("/api/companies/" + company.id).then().statusCode(404);
    given()
        .when()
        .get("/api/contacts/" + contact.id)
        .then()
        .statusCode(200)
        .body("companyId", nullValue());
  }

  @Test
  void unknown_identifiers_yield_a_not_found_error() {
    given().when().get("/api/companies/9999").then().statusCode(404).body("code", is("NOT_FOUND"));
    given().when().delete("/api/companies/9999").then().statusCode(404);
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "Ghost"))
        .when()
        .put("/api/companies/9999")
        .then()
        .statusCode(404);
  }
}
