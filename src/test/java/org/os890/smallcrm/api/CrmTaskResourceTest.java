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
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.os890.smallcrm.domain.Contact;
import org.os890.smallcrm.support.AbstractApiTest;

/** CRUD, ordering, completion and overdue flagging for follow-up tasks. */
@QuarkusTest
@TestSecurity(user = "admin", roles = {"ADMIN", "USER"})
class CrmTaskResourceTest extends AbstractApiTest {

  @Test
  void a_new_task_defaults_to_normal_priority_and_is_open() {
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("title", "Send the offer"))
        .when()
        .post("/api/tasks")
        .then()
        .statusCode(201)
        .body("priority", is("NORMAL"))
        .body("done", is(false))
        .body("overdue", is(false))
        .body("completedAt", nullValue());
  }

  @Test
  void a_task_needs_a_title() {
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("title", " ".repeat(1)))
        .when()
        .post("/api/tasks")
        .then()
        .statusCode(400)
        .body("details.title", is("must not be blank"));
  }

  @Test
  void a_task_due_in_the_past_is_flagged_as_overdue() {
    create("Chase the invoice", LocalDate.now().minusDays(3));

    given()
        .when()
        .get("/api/tasks")
        .then()
        .statusCode(200)
        .body("[0].overdue", is(true));
  }

  @Test
  void tasks_without_a_due_date_are_listed_last() {
    create("Someday", null);
    create("Tomorrow", LocalDate.now().plusDays(1));

    given()
        .when()
        .get("/api/tasks")
        .then()
        .body("title", contains("Tomorrow", "Someday"));
  }

  @Test
  void completing_a_task_stamps_the_completion_time_and_reopening_clears_it() {
    Integer id = create("Send the offer", LocalDate.now()).extract().path("id");

    given()
        .when()
        .put("/api/tasks/" + id + "/done")
        .then()
        .statusCode(200)
        .body("done", is(true))
        .body("overdue", is(false))
        .body("completedAt", notNullValue());

    given()
        .queryParam("value", false)
        .when()
        .put("/api/tasks/" + id + "/done")
        .then()
        .statusCode(200)
        .body("done", is(false))
        .body("completedAt", nullValue());
  }

  @Test
  void the_list_can_be_narrowed_to_open_tasks_and_to_one_contact() {
    Contact contact = fixtures.createContact("Maria", "Huber", null);
    Integer done = create("Already done", null).extract().path("id");
    given().when().put("/api/tasks/" + done + "/done").then().statusCode(200);

    Map<String, Object> forContact = new HashMap<>();
    forContact.put("title", "Call Maria back");
    forContact.put("contactId", contact.id);
    given().contentType(ContentType.JSON).body(forContact).when().post("/api/tasks").then()
        .statusCode(201)
        .body("contactName", is("Maria Huber"));

    given().when().get("/api/tasks").then().body("$", hasSize(2));

    given()
        .queryParam("openOnly", true)
        .when()
        .get("/api/tasks")
        .then()
        .body("title", contains("Call Maria back"));

    given()
        .queryParam("contactId", contact.id)
        .when()
        .get("/api/tasks")
        .then()
        .body("$", hasSize(1));
  }

  @Test
  void a_task_can_be_edited_and_deleted() {
    Integer id = create("Draft the offer", null).extract().path("id");

    given()
        .contentType(ContentType.JSON)
        .body(Map.of("title", "Send the offer", "priority", "HIGH", "done", true))
        .when()
        .put("/api/tasks/" + id)
        .then()
        .statusCode(200)
        .body("priority", is("HIGH"))
        .body("done", is(true))
        .body("completedAt", notNullValue());

    given().when().delete("/api/tasks/" + id).then().statusCode(204);
    given().when().get("/api/tasks/" + id).then().statusCode(404);
  }

  private static io.restassured.response.ValidatableResponse create(
      String title, LocalDate dueDate) {
    Map<String, Object> body = new HashMap<>();
    body.put("title", title);
    if (dueDate != null) {
      body.put("dueDate", dueDate.toString());
    }
    return given()
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post("/api/tasks")
        .then()
        .statusCode(201);
  }
}
