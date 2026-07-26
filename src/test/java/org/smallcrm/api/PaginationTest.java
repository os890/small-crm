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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.smallcrm.domain.Contact;
import org.smallcrm.domain.Deal;
import org.smallcrm.service.PageRequest;
import org.smallcrm.support.AbstractApiTest;

/**
 * Paging on the list endpoints.
 *
 * <p>The point of these is that no request can return a table whole, however large it has grown:
 * the activity log in particular gains a row for every logged call and is never pruned.
 */
@QuarkusTest
@TestSecurity(user = "admin", roles = {"ADMIN", "USER"})
class PaginationTest extends AbstractApiTest {

  @Test
  void a_list_longer_than_a_page_is_cut_off_and_says_how_many_there_are() {
    Contact contact = fixtures.createContact("Maria", "Huber", null);
    for (int index = 0; index < PageRequest.DEFAULT_SIZE + 5; index++) {
      fixtures.createInteraction(contact, Instant.parse("2026-01-01T09:00:00Z"));
    }

    given()
        .when()
        .get("/api/interactions")
        .then()
        .statusCode(200)
        .header(PagedResponse.TOTAL_COUNT, is(String.valueOf(PageRequest.DEFAULT_SIZE + 5)))
        .header(PagedResponse.PAGE, is("0"))
        .header(PagedResponse.PAGE_SIZE, is(String.valueOf(PageRequest.DEFAULT_SIZE)))
        .body("$", hasSize(PageRequest.DEFAULT_SIZE));
  }

  @Test
  void the_next_page_continues_where_the_previous_one_stopped() {
    for (int index = 0; index < 7; index++) {
      fixtures.createCompany(String.format("Company %02d", index));
    }

    List<String> first =
        given()
            .queryParam("size", 3)
            .when()
            .get("/api/companies")
            .then()
            .statusCode(200)
            .header(PagedResponse.TOTAL_COUNT, is("7"))
            .body("$", hasSize(3))
            .extract()
            .jsonPath()
            .getList("name", String.class);

    List<String> second =
        given()
            .queryParam("size", 3)
            .queryParam("page", 1)
            .when()
            .get("/api/companies")
            .then()
            .statusCode(200)
            .header(PagedResponse.PAGE, is("1"))
            .body("$", hasSize(3))
            .extract()
            .jsonPath()
            .getList("name", String.class);

    given()
        .queryParam("size", 3)
        .queryParam("page", 2)
        .when()
        .get("/api/companies")
        .then()
        .body("$", hasSize(1))
        .body("[0].name", is("Company 06"));

    assertThat(first)
        .containsExactly("Company 00", "Company 01", "Company 02");
    assertThat(second)
        .containsExactly("Company 03", "Company 04", "Company 05");
  }

  @Test
  void a_page_past_the_end_is_empty_rather_than_an_error() {
    fixtures.createCompany("Only one");

    given()
        .queryParam("page", 40)
        .when()
        .get("/api/companies")
        .then()
        .statusCode(200)
        .header(PagedResponse.TOTAL_COUNT, is("1"))
        .body("$", hasSize(0));
  }

  @Test
  void an_ambitious_page_size_is_served_with_the_maximum_instead_of_being_refused() {
    fixtures.createCompany("Only one");

    given()
        .queryParam("size", 100000)
        .when()
        .get("/api/companies")
        .then()
        .statusCode(200)
        .header(PagedResponse.PAGE_SIZE, is(String.valueOf(PageRequest.MAX_SIZE)));
  }

  @Test
  void nonsense_paging_parameters_are_rejected_with_a_readable_message() {
    given()
        .queryParam("page", -1)
        .when()
        .get("/api/contacts")
        .then()
        .statusCode(400)
        .body("code", is("INVALID_PAGE"));

    given()
        .queryParam("size", 0)
        .when()
        .get("/api/contacts")
        .then()
        .statusCode(400)
        .body("code", is("INVALID_PAGE_SIZE"));
  }

  @Test
  void every_list_endpoint_is_paged() {
    for (String path :
        List.of("/api/contacts", "/api/companies", "/api/deals", "/api/interactions",
            "/api/tasks")) {
      given()
          .when()
          .get(path)
          .then()
          .statusCode(200)
          .header(PagedResponse.PAGE_SIZE, is(String.valueOf(PageRequest.DEFAULT_SIZE)));
    }
  }

  /**
   * The pipeline order used to be restored in memory after the query. That silently stops working
   * once a page is fetched, because the database decides which rows the page contains before the
   * re-sort ever sees them.
   */
  @Test
  void the_pipeline_order_holds_across_page_boundaries() {
    Deal won = fixtures.createDeal("Won deal", null);
    fixtures.createDeal("Lead deal", null);
    Deal lost = fixtures.createDeal("Lost deal", null);
    Deal proposal = fixtures.createDeal("Proposal deal", null);
    moveTo(won.id, "WON");
    moveTo(lost.id, "LOST");
    moveTo(proposal.id, "PROPOSAL");

    given()
        .queryParam("size", 2)
        .when()
        .get("/api/deals")
        .then()
        .body("title", contains("Lead deal", "Proposal deal"));

    given()
        .queryParam("size", 2)
        .queryParam("page", 1)
        .when()
        .get("/api/deals")
        .then()
        .body("title", contains("Won deal", "Lost deal"));

    // Same order when it all fits on one page, so the two paths cannot drift apart.
    given()
        .when()
        .get("/api/deals")
        .then()
        .body("title", contains("Lead deal", "Proposal deal", "Won deal", "Lost deal"));
  }

  @Test
  void deals_can_be_narrowed_to_one_contact() {
    Contact maria = fixtures.createContact("Maria", "Huber", null);
    Contact jonas = fixtures.createContact("Jonas", "Berger", null);
    fixtures.createDeal("Website relaunch", maria);
    fixtures.createDeal("Bookkeeping", jonas);

    given()
        .queryParam("contactId", maria.id)
        .when()
        .get("/api/deals")
        .then()
        .header(PagedResponse.TOTAL_COUNT, is("1"))
        .body("title", contains("Website relaunch"));
  }

  private static void moveTo(Long dealId, String stage) {
    Map<String, Object> nothing = new HashMap<>();
    given()
        .contentType(ContentType.JSON)
        .body(nothing)
        .queryParam("value", stage)
        .when()
        .put("/api/deals/" + dealId + "/stage")
        .then()
        .statusCode(200);
  }
}
