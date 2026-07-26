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

package org.smallcrm.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.smallcrm.security.Passwords;

/** Payload an administrator sends to add a new account. */
public record CreateUserRequest(
    @NotBlank
        @Size(min = 3, max = 100)
        @Pattern(
            regexp = "^[a-zA-Z0-9._-]+$",
            message = "may only contain letters, digits, dot, underscore and hyphen")
        String username,
    @NotBlank @Size(min = Passwords.MIN_LENGTH, max = Passwords.MAX_LENGTH)
        String password,
    @Size(max = 150) String fullName,
    @Email @Size(max = 200) String email,
    boolean admin) {}
