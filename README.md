# tutorMe

tutorMe is a local tutoring-management demo for fictional tutor and family records. The role selector and its API header are **insecure for production**. Anyone with access to the API can select either role. Use fictional information and sample PDFs only.

## Windows setup

Install a Java 21 JDK, Maven 3.9+, and Node.js 22.12+ with npm. Add the Maven, Node, and JDK binaries to PATH and set JAVA_HOME to the JDK folder. Open PowerShell in this directory and confirm the tools with `java -version`, `mvn -v`, and `node -v`. [Microsoft OpenJDK downloads](https://learn.microsoft.com/en-us/java/openjdk/download-major-urls)

In one PowerShell window, start the API from the repository root.

```powershell
Set-Location backend
mvn spring-boot:run
```

In another PowerShell window, start the frontend from the repository root.

```powershell
npm.cmd ci
npm.cmd run dev
```

Open http://localhost:5173. Vite proxies `/api` to Spring Boot on `127.0.0.1:8080`. The API seeds three fictional sessions when the database is empty. The file-backed H2 database, including uploaded PDFs, is saved under `backend/data/` when the API is run from the backend directory. Keep the working directory consistent across restarts.

## Tests and builds

Run these commands from the repository root.

```powershell
mvn -f backend/pom.xml clean verify
npm.cmd ci
npm.cmd test
npm.cmd run build
```

Maven runs the JUnit unit and MockMvc/JPA integration tests and creates `backend/target/tutorme-1.0.0.jar`. The integration tests use isolated in-memory H2. The frontend tests cover calendar dates, session form conversion, payment totals, and API error handling. Vite builds the frontend into `dist/`.

To run the built app locally, use `java -jar target/tutorme-1.0.0.jar` from `backend/` and `npm.cmd run preview` from the repository root. Preview also proxies API requests.

## Architecture

React renders distinct Tutor and Family dashboards, with shared calendar, resource, and payment views. Session times are stored as UTC instants and displayed in the browser's local time zone. The tutor can create, edit, and cancel sessions, set CAD hourly rates, attach existing Zoom links, upload PDFs, record cumulative payments, and post reminders that appear in the family dashboard.

Spring MVC controllers handle role checks and REST responses. Services enforce business rules, JPA repositories persist entities to H2, and DTOs expose session data and PDF metadata. PDF bytes are stored in H2 and fetched by numeric ID. Uploads have a 5 MB limit, a PDFBox structural check, and sanitized filenames.

| REST resource | Methods |
| --- | --- |
| `/api/sessions` | GET for both roles, POST for Tutor |
| `/api/sessions/{id}` | PUT for Tutor |
| `/api/sessions/{id}/cancel` | POST for Tutor |
| `/api/sessions/{id}/payment` | PUT for Tutor with total paid to date |
| `/api/sessions/{id}/reminder` | POST for Tutor |
| `/api/handouts` | GET for both roles, multipart POST for Tutor with a `file` part |
| `/api/handouts/{id}` | GET for both roles, with optional `?download=true` |

Supply `X-Demo-Role: TUTOR` or `X-Demo-Role: FAMILY` with API requests. Fees are calculated from duration and hourly rate, rounded to cents. Recording a full payment clears the open reminder. To cancel a session with a payment recorded, first enter zero as the total paid once any refund has been handled externally.

## Limitations

- The demo shares one set of records between the two roles. Production use requires authentication and per-family authorization.
- Payment records are cumulative totals. There is no transaction history, payment processing, or refund processing. Reminders stay in the app and show the latest reminder per session.
- Zoom links are entered manually. The app does not create Zoom meetings or prevent overlapping sessions.
- PDF validation checks file size and structure and rejects encrypted files. Malware scanning is outside the demo's scope. In-app preview depends on browser PDF support.
- H2 and automatic schema updates are intended for a local demo. There are no migration, backup, or multi-instance deployment workflows.
