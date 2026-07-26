# Small CRM — architecture

Diagrams from the inside out: the tables first, then the objects mapped onto them, the code
around those, the conversations between the parts, and finally the one process the whole thing
ships as.

All diagrams are Mermaid and render on GitHub and in any Mermaid-aware viewer.

- [1. Persistence model](#1-persistence-model)
- [2. Domain model](#2-domain-model)
- [3. Backend structure](#3-backend-structure)
- [4. Frontend structure](#4-frontend-structure)
- [5. Behaviour](#5-behaviour)
- [6. Runtime containers](#6-runtime-containers)
- [7. Deployment](#7-deployment)

---

## 1. Persistence model

One H2 file, ten tables, owned by Flyway (`src/main/resources/db/migration`). Hibernate is set to
`validate`, so the schema below is the authority and a drift between it and the entities fails the
start rather than being quietly patched.

Everything a user creates points at its `owner`, but ownership is informational: this is a shared
workspace, and every signed-in user sees every record.

```mermaid
erDiagram
    app_user ||--o{ app_session : "has open"
    app_user ||--o{ company : owns
    app_user ||--o{ contact : owns
    app_user ||--o{ deal : owns
    app_user ||--o{ interaction : owns
    app_user ||--o{ crm_task : owns
    app_user ||--o{ appointment : owns

    company  ||--o{ contact : employs
    company  ||--o{ deal : "is party to"
    contact  ||--o{ contact_tag : "is labelled"
    contact  ||--o{ deal : "is party to"
    contact  ||--o{ interaction : "is logged against"
    contact  ||--o{ crm_task : "is about"
    contact  ||--o{ appointment : "is with"
    deal     ||--o{ interaction : "is logged against"
    deal     ||--o{ crm_task : "is about"
    deal     ||--o{ appointment : "is about"

    app_user {
        bigint id PK
        varchar username UK
        varchar password "bcrypt, cost 12"
        varchar roles "comma separated"
        varchar fullName
        varchar email
        boolean mustChangePassword
        boolean active
        int failedLoginCount "lockout counter"
        timestamp lockedUntil "null unless locked out"
        bigint version
        timestamp createdAt
        timestamp updatedAt
    }
    app_session {
        varchar token_hash PK "SHA-256 of the cookie value"
        bigint user_id FK
        timestamp createdAt
        timestamp lastSeenAt "idle timeout"
        timestamp expiresAt "absolute timeout"
    }
    company {
        bigint id PK
        varchar name
        varchar vatId
        varchar website
        varchar email
        varchar phone
        varchar street
        varchar postalCode
        varchar city
        varchar country
        varchar notes
        bigint owner_id FK
        bigint version
        timestamp createdAt
        timestamp updatedAt
    }
    contact {
        bigint id PK
        varchar firstName
        varchar lastName
        varchar email
        varchar phone
        varchar mobile
        varchar position
        bigint company_id FK
        varchar notes
        bigint owner_id FK
        bigint version
        timestamp createdAt
        timestamp updatedAt
    }
    contact_tag {
        bigint contact_id FK
        varchar tag
    }
    deal {
        bigint id PK
        varchar title
        bigint contact_id FK
        bigint company_id FK
        numeric amount "named amount; VALUE is reserved in H2"
        varchar currency
        varchar stage "LEAD QUALIFIED PROPOSAL WON LOST"
        date expectedCloseDate
        varchar notes
        bigint owner_id FK
        bigint version
        timestamp createdAt
        timestamp updatedAt
    }
    interaction {
        bigint id PK
        varchar type "CALL EMAIL MEETING NOTE"
        timestamp occurredAt "indexed"
        varchar subject
        varchar notes
        bigint contact_id FK "required"
        bigint deal_id FK
        bigint owner_id FK
        bigint version
        timestamp createdAt
        timestamp updatedAt
    }
    crm_task {
        bigint id PK
        varchar title
        varchar description
        date dueDate "indexed"
        boolean done
        timestamp completedAt
        varchar priority "LOW NORMAL HIGH"
        bigint contact_id FK
        bigint deal_id FK
        bigint owner_id FK
        bigint version
        timestamp createdAt
        timestamp updatedAt
    }
    appointment {
        bigint id PK
        varchar title
        timestamp startsAt "indexed with endsAt"
        timestamp endsAt
        varchar timeZone
        varchar location
        varchar notes
        bigint contact_id FK
        bigint deal_id FK
        varchar externalCalendarId "reserved for calendar sync"
        varchar externalEventId
        varchar externalEtag
        timestamp lastSyncedAt
        bigint owner_id FK
        bigint version
        timestamp createdAt
        timestamp updatedAt
    }
    app_setting {
        varchar setting_key PK
        varchar setting_value
    }
```

`app_setting` stands apart: a two-column key/value table holding the settings a user can change
from the interface, currently only the backup retention period. It has no foreign keys and no
owner because it belongs to the installation rather than to anybody in it.

Deleting never cascades into other people's work. Removing a company detaches its contacts and
deals; removing a contact deletes only the interactions logged against it, which have no meaning
without it, and detaches everything else.

---

## 2. Domain model

Panache active-record entities with public fields — Quarkus' idiom, and the reason there are no
repository classes. `BaseEntity` carries what every business record has; `AppUser` and
`AppSession` sit outside it because accounts are not business data and are deliberately excluded
from backups.

```mermaid
classDiagram
    direction LR

    class PanacheEntityBase {
        <<Panache>>
        +persist()
        +delete()
        +findById(id)$
        +find(query, sort, params)$
    }

    class BaseEntity {
        <<abstract>>
        +Long id
        +AppUser owner
        +long version
        +Instant createdAt
        +Instant updatedAt
        #onCreate() void
        #onUpdate() void
    }

    class AppUser {
        +Long id
        +String username
        +String password
        +String roles
        +String fullName
        +boolean mustChangePassword
        +boolean active
        +int failedLoginCount
        +Instant lockedUntil
        +isAdmin() boolean
        +isLockedOut(now) boolean
    }

    class AppSession {
        +String tokenHash
        +AppUser user
        +Instant lastSeenAt
        +Instant expiresAt
    }

    class Company {
        +String name
        +String vatId
        +String city
    }

    class Contact {
        +String firstName
        +String lastName
        +Company company
        +Set~String~ tags
        +displayName() String
    }

    class Deal {
        +String title
        +Contact contact
        +Company company
        +BigDecimal amount
        +String currency
        +DealStage stage
        +LocalDate expectedCloseDate
    }

    class Interaction {
        +InteractionType type
        +Instant occurredAt
        +String subject
        +Contact contact
        +Deal deal
    }

    class CrmTask {
        +String title
        +LocalDate dueDate
        +boolean done
        +TaskPriority priority
        +Contact contact
        +Deal deal
    }

    class Appointment {
        +String title
        +Instant startsAt
        +Instant endsAt
        +String timeZone
        +Contact contact
        +Deal deal
        +overlaps(from, to) boolean
    }

    class AppSetting {
        +String key
        +String value
    }

    class DealStage {
        <<enumeration>>
        LEAD
        QUALIFIED
        PROPOSAL
        WON
        LOST
        +isClosed() boolean
        +order() int
    }

    class InteractionType {
        <<enumeration>>
        CALL
        EMAIL
        MEETING
        NOTE
    }

    class TaskPriority {
        <<enumeration>>
        LOW
        NORMAL
        HIGH
    }

    class Clocks {
        <<utility>>
        +now()$ Instant
        +use(clock)$
    }

    PanacheEntityBase <|-- BaseEntity
    PanacheEntityBase <|-- AppUser
    PanacheEntityBase <|-- AppSession
    PanacheEntityBase <|-- AppSetting
    BaseEntity <|-- Company
    BaseEntity <|-- Contact
    BaseEntity <|-- Deal
    BaseEntity <|-- Interaction
    BaseEntity <|-- CrmTask
    BaseEntity <|-- Appointment

    BaseEntity ..> Clocks : timestamps itself with
    AppSession --> AppUser
    Contact --> Company
    Deal --> Contact
    Deal --> Company
    Deal --> DealStage
    Interaction --> Contact
    Interaction --> Deal
    Interaction --> InteractionType
    CrmTask --> Contact
    CrmTask --> Deal
    CrmTask --> TaskPriority
    Appointment --> Contact
    Appointment --> Deal
```

`Clocks` is a static holder rather than an injected dependency because `@PrePersist` and
`@PreUpdate` have no injection point of their own. `ClockProducer` fills it at startup, so a test
with a fixed clock gets fixed timestamps.

---

## 3. Backend structure

Four packages, one direction of dependency. Resources speak HTTP and know nothing about
persistence; services own the business rules and the transactions; entities own the data and the
rules that belong on it. `security` and `backup` are cross-cutting and hang off the request
lifecycle rather than being called from the middle of it.

```mermaid
flowchart TB
    subgraph api["api — HTTP"]
        direction LR
        REST["ContactResource<br/>CompanyResource<br/>DealResource<br/>InteractionResource<br/>CrmTaskResource<br/>AppointmentResource<br/>DashboardResource<br/>UserResource<br/>AuthResource<br/>BackupResource"]
        DTO["dto/*<br/>records, bean validation"]
        ERR["error/ApiExceptionMappers<br/>one ApiError shape"]
        PAGE["PagedResponse<br/>X-Total-Count"]
    end

    subgraph service["service — rules and transactions"]
        direction LR
        SVC["ContactService<br/>CompanyService<br/>DealService<br/>InteractionService<br/>CrmTaskService<br/>AppointmentService<br/>DashboardService<br/>UserService"]
        HELP["PageRequest / Paged<br/>Versions · ReferenceResolver<br/>ClockProducer"]
    end

    subgraph security["security — cross-cutting"]
        direction LR
        MECH["SessionAuthenticationMechanism"]
        SESS["SessionService"]
        LOGIN["LoginService<br/>lockout + timing"]
        PW["Passwords<br/>bcrypt cost 12"]
        FILT["AccountStateFilter<br/>CrossOriginWriteFilter"]
        BOOT["BootstrapAdminService<br/>StartupChecks · DataDirectoryCheck"]
    end

    subgraph backup["backup — durability"]
        direction LR
        TRIG["DataChangeFilter<br/>AutoBackupTrigger"]
        BSVC["BackupService"]
        XML["BackupXml / BackupModel"]
        SNAP["DatabaseSnapshot<br/>Durability"]
    end

    subgraph domain["domain — entities"]
        ENT["Company · Contact · Deal<br/>Interaction · CrmTask · Appointment<br/>AppUser · AppSession · AppSetting"]
    end

    DB[("H2 file<br/>data/smallcrm.mv.db")]
    FS[("backup/<br/>XML + snapshots")]

    api --> service
    service --> domain
    domain --> DB
    api -.->|"request filters"| security
    api -.->|"response filter"| backup
    security --> domain
    backup --> domain
    backup --> FS
    SNAP -.->|"BACKUP TO"| DB
```

The one dependency worth pointing out is the dotted one from `api` to `backup`: nothing calls the
backup code. `DataChangeFilter` watches successful writes go past and tells `AutoBackupTrigger`
something changed, which is why adding an endpoint cannot accidentally leave it out of the
backups.

### Request pipeline

The order matters, and two of the steps exist because of specific failure modes.

```mermaid
flowchart LR
    A["HTTP request"] --> B{"path"}
    B -->|"/q/*"| H["health, metrics"]
    B -->|"not /api/*"| Q["Quinoa<br/>static bundle,<br/>SPA fallback to index.html"]
    B -->|"/api/*"| C["SessionAuthenticationMechanism<br/>cookie → session row → identity"]
    C --> D["HTTP permission policy<br/>public vs authenticated"]
    D --> E["CrossOriginWriteFilter<br/>refuses foreign-Origin writes"]
    E --> F["AccountStateFilter<br/>forced password change"]
    F --> G["resource → service → entity"]
    G --> I["DataChangeFilter<br/>schedules a backup"]
    I --> J["security headers<br/>CSP, HSTS, nosniff"]
    J --> K["HTTP response"]
    Q --> J
```

`quarkus.rest.path=/api` is what makes the second branch possible: the REST layer never sees a
front-end URL, so an unmatched path falls through to Quinoa's single-page fallback instead of
being answered with a 404 by the API's own error handling.

---

## 4. Frontend structure

Angular 22, standalone components, signals, zoneless change detection. `core` holds everything
shared and stateful; each screen is one lazily-loaded component under `features`; `shared` holds
the three pieces of interaction vocabulary every screen reuses.

```mermaid
flowchart TB
    subgraph shell["Application shell"]
        APP["AppComponent"]
        ROUTES["app.routes<br/>lazy per feature"]
        SHELLC["ShellComponent<br/>navigation, language, sign out"]
    end

    subgraph core["core"]
        API["ApiService<br/>every call, one place"]
        AUTH["AuthService<br/>who is signed in"]
        GUARD["auth.guards<br/>signedIn · admin · mustChangePassword"]
        INT["session.interceptor<br/>401 → login with returnUrl"]
        I18N["I18nService + translations<br/>en / de at runtime"]
        FMT["FormatService<br/>dates, money per locale"]
        PROB["problem.ts<br/>HttpErrorResponse → Problem"]
        TOAST["ToastService"]
        MOD["models.ts<br/>Contact, Deal, Page&lt;T&gt;, …"]
    end

    subgraph features["features — one per screen"]
        F1["dashboard"]
        F2["contacts + contact detail"]
        F3["companies"]
        F4["deals"]
        F5["tasks"]
        F6["calendar"]
        F7["backups"]
        F8["users"]
        F9["login · change password"]
    end

    subgraph sharedui["shared"]
        CONF["ConfirmService +<br/>ConfirmHostComponent<br/>native &lt;dialog&gt;"]
        PAGER["PagerComponent<br/>51–100 of 812"]
        PICK["EntityPickerComponent<br/>typeahead, not a &lt;select&gt;"]
        TH["ToastHostComponent"]
    end

    APP --> ROUTES --> SHELLC
    SHELLC --> features
    ROUTES -.->|"canActivate"| GUARD
    GUARD --> AUTH
    features --> API
    features --> sharedui
    features --> I18N
    features --> FMT
    features --> TOAST
    AUTH --> API
    API --> MOD
    INT --> AUTH
    INT --> PROB
    TOAST --> PROB
    PROB --> I18N
    APP --> TH
    APP --> CONF
```

Two rules hold across every screen: nothing is fetched that is not shown (the pickers look records
up as you type rather than filling a dropdown from the whole table), and no list is trusted to be
complete without the server saying how many there are.

---

## 5. Behaviour

### Signing in, and what the cookie is worth

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant NG as Angular
    participant AR as AuthResource
    participant LS as LoginService
    participant SS as SessionService
    participant DB as H2

    U->>NG: user name + password
    NG->>AR: POST /api/auth/login (form encoded)
    AR->>LS: authenticate(username, password)
    LS->>DB: find the account
    alt locked out
        LS-->>AR: refused
    else wrong password
        LS->>DB: failedLoginCount++, lockedUntil after 5
        Note over LS: a miss still verifies a dummy hash,<br/>so the timing says nothing
        LS-->>AR: refused
    else correct
        LS->>DB: reset the counter
        LS-->>AR: the account
        AR->>SS: issue(user)
        SS->>SS: 256 random bits
        SS->>DB: store SHA-256 of the token
        SS-->>AR: token + expiry
        AR-->>NG: 204, Set-Cookie smallcrm_session<br/>HttpOnly, SameSite=Strict
    end
    NG->>AR: GET /api/auth/me
    AR-->>NG: the profile
    NG->>NG: mustChangePassword ? /change-password : returnUrl
```

The cookie carries a random token and nothing else. Everything about the session lives in
`app_session`, which is why a password change, a deactivation or an administrator's reset can end
a session immediately — and why there is no key anywhere that could be used to mint one.

### Booking an appointment without double-booking

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant NG as Calendar screen
    participant AR as AppointmentResource
    participant AS as AppointmentService
    participant DB as H2

    U->>NG: date and times
    NG->>AR: GET /api/appointments/conflicts (debounced, while typing)
    AR->>AS: conflicts(excludeId, from, to)
    AS->>DB: overlapping slots of this owner
    DB-->>NG: the clashes, shown before saving

    U->>NG: Save
    NG->>AR: POST /api/appointments
    AR->>AS: create(dto, allowConflict = false)
    AS->>DB: SELECT the owner's account row FOR UPDATE
    Note over AS,DB: the lock is what makes check-and-insert atomic:<br/>two requests for the same slot would otherwise<br/>both find it free
    AS->>DB: overlapping slots
    alt slot taken
        AS-->>NG: 409 APPOINTMENT_CONFLICT + the clashes
        U->>NG: "Save anyway"
        NG->>AR: POST again, allowConflict = true
    else free
        AS->>DB: INSERT
        AS-->>NG: 201
    end
```

### A change reaching a backup file

```mermaid
sequenceDiagram
    autonumber
    participant R as Any write endpoint
    participant F as DataChangeFilter
    participant T as AutoBackupTrigger
    participant B as BackupService
    participant S as DatabaseSnapshot
    participant FS as backup/

    R->>F: 2xx response for a POST/PUT/DELETE
    F->>F: first path segment not auth/users/backups?
    F->>T: dataChanged()
    Note over T: returns at once —<br/>the user never waits for a file
    T->>T: schedule once, 30 s later<br/>(a burst becomes one file)
    T->>B: writeNow()
    B->>B: export everything in one transaction
    B->>FS: write .part, fsync, atomic rename
    B->>S: snapshot alongside
    S->>FS: H2 BACKUP TO zip (accounts included)
    B->>FS: delete files past the retention period
```

On shutdown a still-pending write is flushed first, so the last change before a service stop is
not the one that never made it to disk.

### Restoring

```mermaid
stateDiagram-v2
    [*] --> Chosen: administrator picks a file from the folder, or uploads one
    Chosen --> Parsed: size, format version and unique ids checked
    Parsed --> Refused: anything wrong
    Refused --> [*]: nothing touched
    Parsed --> Locked: take the backup-folder lock
    Locked --> SafetyCopy: write before-restore-TIMESTAMP.xml
    SafetyCopy --> Replaced: delete all business data, insert the file's, restore its timestamps
    Replaced --> Relinked: match owners by user name, note the ones not found
    Relinked --> [*]: report the count, the skipped records and the unresolved owners
```

The safety copy and the replacement happen inside one lock. Without it, a record saved between the
two would be wiped by the restore and appear in neither the safety copy nor any automatic backup.

---

## 6. Runtime containers

One process. The Angular bundle is not a separate deployment: Quinoa builds it during
`mvn package` and it is served from the same jar, on the same port, as static resources.

```mermaid
flowchart TB
    subgraph browser["Browser"]
        SPA["Angular single-page application<br/>bundle + session cookie"]
    end

    subgraph jvm["One JVM — quarkus-run.jar"]
        direction TB
        VERTX["Vert.x HTTP server<br/>port 8080"]
        QUINOA["Quinoa static handler<br/>+ SPA fallback"]
        RESTL["Quarkus REST — /api"]
        SEC["session mechanism<br/>+ filters"]
        BIZ["services"]
        ORM["Hibernate ORM + Panache"]
        FLY["Flyway<br/>migrates at startup"]
        SCHED["backup scheduler"]
        HEALTH["/q/health"]
    end

    DB[("H2 embedded<br/>data/smallcrm.mv.db")]
    BK[("backup/<br/>*.xml + *.zip")]
    LOG[("logs/<br/>rotated, production only")]

    SPA -->|"HTTPS · JSON · /api/*"| VERTX
    SPA -->|"HTML, JS, CSS"| VERTX
    VERTX --> QUINOA
    VERTX --> RESTL
    VERTX --> HEALTH
    RESTL --> SEC --> BIZ --> ORM --> DB
    FLY --> DB
    SCHED --> BK
    SCHED --> DB
    jvm --> LOG
```

No database server, no message broker, no cache, no container runtime. That is a deliberate
product decision: the audience is one self-employed person, and every additional moving part is
one more thing they would have to keep alive.

---

## 7. Deployment

The two ways this is actually run — on the owner's own machine, which is the usual case, and on
a small server behind a reverse proxy. Everything the installation consists of lives in three
directories beside the jar.

```mermaid
flowchart TB
    subgraph laptop["On the owner's machine"]
        direction TB
        B1["Browser<br/>localhost:8080"]
        subgraph n1["Java 25 runtime"]
            P1["quarkus-run.jar<br/>profile: prod"]
        end
        subgraph d1["Working directory"]
            F1[("data/")]
            F2[("backup/")]
            F3[("logs/")]
        end
        B1 -->|"HTTP, loopback only"| P1
        P1 --- F1
        P1 --- F2
        P1 --- F3
    end

    subgraph server["On a small server"]
        direction TB
        B2["Browser<br/>crm.example.org"]
        subgraph n2["Host"]
            PROXY["Reverse proxy<br/>TLS termination"]
            subgraph n3["Java 25 runtime"]
                P2["quarkus-run.jar<br/>SMALLCRM_HTTPS=true<br/>SMALLCRM_BEHIND_PROXY=true"]
            end
        end
        subgraph d2["Data directory"]
            G1[("data/")]
            G2[("backup/")]
            G3[("logs/")]
        end
        OFF[("Off-site copy<br/>still manual — see todo.md")]
        B2 -->|"HTTPS"| PROXY
        PROXY -->|"HTTP, loopback<br/>X-Forwarded-*"| P2
        P2 --- G1
        P2 --- G2
        P2 --- G3
        G2 -.->|"copied by the operator"| OFF
    end
```

What changes between the two is configuration, not code:

| | Own machine | Behind a proxy |
| --- | --- | --- |
| `SMALLCRM_HTTPS` | `false` — a `Secure` cookie would be dropped over plain HTTP | `true` |
| `SMALLCRM_BEHIND_PROXY` | `false` | `true`, and the proxy must strip client-supplied `X-Forwarded-*` |
| `SMALLCRM_DATA_DIR` | `./data` | wherever the backed-up volume is |
| First password | printed once to the console | printed once to the log file |
| Off-site backups | the owner copies `backup/` | the operator copies `backup/` |

A future Google Calendar synchronisation is the only outbound connection the design anticipates;
the schema already carries the fields for it (`externalEventId`, `externalEtag`, `lastSyncedAt`)
so appointments entered today can be adopted without a migration.
