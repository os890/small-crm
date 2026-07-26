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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.os890.smallcrm.domain.Contact;
import org.os890.smallcrm.support.AbstractApiTest;

/** The single call that fills the start page. */
@QuarkusTest
@TestSecurity(user = "admin", roles = {"ADMIN", "USER"})
class DashboardResourceTest extends AbstractApiTest {

  @Test
  void an_empty_installation_reports_zeroes_rather_than_nulls() {
    given()
        .when()
        .get("/api/dashboard")
        .then()
        .statusCode(200)
        .body("contactCount", is(0))
        .body("companyCount", is(0))
        .body("openDealCount", is(0))
        .body("openDealValue", is(0))
        .body("overdueTasks", empty())
        .body("tasksDueToday", empty())
        .body("upcomingAppointments", empty())
        .body("recentInteractions", empty());
  }

  @Test
  void the_summary_counts_only_open_deals_and_splits_tasks_by_urgency() {
    Contact contact = fixtures.createContact("Maria", "Huber", null);
    fixtures.createCompany("Muster GmbH");

    createDeal("Open deal", 1000, null);
    createDeal("Won deal", 5000, "WON");

    createTask("Overdue task", LocalDate.now().minusDays(2));
    createTask("Due today", LocalDate.now());
    createTask("Later", LocalDate.now().plusDays(5));

    Instant soon = Instant.now().plus(Duration.ofHours(2)).truncatedTo(java.time.temporal
        .ChronoUnit.SECONDS);
    createAppointment("Client meeting", soon, soon.plus(Duration.ofHours(1)));
    fixtures.createInteraction(contact, Instant.now().minus(Duration.ofHours(1)));

    given()
        .when()
        .get("/api/dashboard")
        .then()
        .statusCode(200)
        .body("contactCount", is(1))
        .body("companyCount", is(1))
        .body("openDealCount", is(1))
        .body("openDealValue", is(1000.0f))
        .body("overdueTasks.title", contains("Overdue task"))
        .body("tasksDueToday.title", contains("Due today"))
        .body("upcomingAppointments.title", contains("Client meeting"))
        .body("recentInteractions", hasSize(1));
  }

  private static void createDeal(String title, int amount, String stage) {
    Map<String, Object> body = new HashMap<>();
    body.put("title", title);
    body.put("amount", amount);
    if (stage != null) {
      body.put("stage", stage);
    }
    given().contentType(ContentType.JSON).body(body)
        .when().post("/api/deals").then().statusCode(201);
  }

  private static void createTask(String title, LocalDate dueDate) {
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("title", title, "dueDate", dueDate.toString()))
        .when()
        .post("/api/tasks")
        .then()
        .statusCode(201);
  }

  private static void createAppointment(String title, Instant startsAt, Instant endsAt) {
    Map<String, Object> body = new HashMap<>();
    body.put("title", title);
    body.put("startsAt", startsAt.toString());
    body.put("endsAt", endsAt.toString());
    given().contentType(ContentType.JSON).body(body)
        .when().post("/api/appointments").then().statusCode(201);
  }
}
