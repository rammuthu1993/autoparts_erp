# AI Assistant trusted execution-context test fix

## Included files

- `src/main/java/com/autoparts/erp/ai/service/AiAssistantService.java`
- `src/test/java/com/autoparts/erp/ai/service/AiAssistantServiceTest.java`

## Apply

From the project root, back up the two existing files, then copy these files
over the corresponding paths in your working tree.

The production change only clarifies the authenticated `userSubject` mismatch
exception message. The mandatory trusted execution-context check remains in
place.

The test changes update the legacy tests to supply a trusted execution context
and mock the four-argument `ErpAiAgentService.chat(...)` overload.

## Verify on Windows PowerShell

Run the focused test first:

```powershell
.\mvnw.cmd "-Dtest=AiAssistantServiceTest" test
```

Then run the full suite:

```powershell
.\mvnw.cmd clean test
```

Do not treat this patch as verified until the commands complete successfully
in your local project. The patch does not include changes to migrations,
application configuration, repositories, or controllers.
