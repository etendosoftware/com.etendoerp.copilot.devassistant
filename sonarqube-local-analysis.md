# SonarQube Local Analysis — Skill Reference

## Overview

Run SonarQube analysis locally against the remote Etendo SonarQube server (`sonar.etendo.cloud`), using the same rules and quality profiles configured on the server. This lets you catch issues before pushing/CI.

## Prerequisites

### 1. Install SonarScanner CLI

```bash
brew install sonar-scanner
```

Verify: `sonar-scanner --version`

### 2. Configure Token

The token is **per-user** (not per-project). Generate it at:
`https://sonar.etendo.cloud` > Avatar > My Account > Security > Generate Token

Token types:
- **User Token**: works for all projects the user has access to
- **Project Analysis Token**: scoped to a single project

### 3. Store Token Securely

**Option A — Environment variable (recommended for CLI usage):**

Add to `~/.zshrc`:
```bash
export SONAR_TOKEN="squ_xxxxxxxxxxxxxxxxxxxxxxxx"
```
Then `source ~/.zshrc`.

**Option B — macOS Keychain (most secure):**
```bash
# Store
security add-generic-password -a "$USER" -s "sonar-etendo" -w "squ_xxxxxxxxxxxxxxxxxxxxxxxx"

# Retrieve
security find-generic-password -a "$USER" -s "sonar-etendo" -w
```

Use in scripts:
```bash
SONAR_TOKEN=$(security find-generic-password -a "$USER" -s "sonar-etendo" -w)
```

**Option C — `.env` file (per-project, gitignored):**
```bash
echo 'SONAR_TOKEN=squ_xxxxxxxxxxxxxxxxxxxxxxxx' >> .env
echo '.env' >> .gitignore
```

> NEVER commit tokens to git or store them in plain-text memory/config files that are shared.

## Usage

### Basic Analysis (current project)

From the module root (where `sonar-project.properties` exists):

```bash
sonar-scanner \
  -Dsonar.host.url=https://sonar.etendo.cloud \
  -Dsonar.token=$SONAR_TOKEN
```

### PR Analysis (matches CI behavior)

```bash
sonar-scanner \
  -Dsonar.host.url=https://sonar.etendo.cloud \
  -Dsonar.token=$SONAR_TOKEN \
  -Dsonar.pullrequest.key=<PR_NUMBER> \
  -Dsonar.pullrequest.branch=<BRANCH_NAME> \
  -Dsonar.pullrequest.base=main
```

### Branch Analysis (no PR)

```bash
sonar-scanner \
  -Dsonar.host.url=https://sonar.etendo.cloud \
  -Dsonar.token=$SONAR_TOKEN \
  -Dsonar.branch.name=<BRANCH_NAME>
```

## Project Configuration

All Etendo modules should include a `sonar-project.properties` file in their root. The file contains at minimum:

```properties
sonar.projectKey=etendosoftware_<module-javapackage>_<sonar-id>
sonar.java.binaries=.
```

The `projectKey` matches the one registered on the server. No manual setup needed per module — just `cd` into the module and run the scanner.

## How It Works

1. SonarScanner connects to `sonar.etendo.cloud`
2. Downloads the **quality profile** and **rules** configured for the project on the server
3. Analyzes local source code against those rules
4. Uploads results to the server (visible in the SonarQube dashboard)

This means: local analysis uses **the same rules as CI**. No separate config needed.

## Viewing Results

After analysis completes:
- Results appear at: `https://sonar.etendo.cloud/dashboard?id=<projectKey>`
- For PR analysis: `https://sonar.etendo.cloud/dashboard?id=<projectKey>&pullRequest=<PR_NUMBER>`

## Querying Issues via API

After the scanner uploads results, wait a few seconds for server processing, then query:

```bash
curl -s -u "$SONAR_TOKEN:" \
  "https://sonar.etendo.cloud/api/issues/search?componentKeys=<projectKey>&pullRequest=<PR>&resolved=false&ps=50"
```

Parse with python3:
```bash
curl -s -u "$SONAR_TOKEN:" \
  "https://sonar.etendo.cloud/api/issues/search?componentKeys=<projectKey>&pullRequest=<PR>&resolved=false&ps=50" \
  | python3 -c "
import json, sys
data = json.load(sys.stdin)
print(f'Total issues: {data.get(\"total\", 0)}')
for i in data.get('issues', []):
    comp = i.get('component','').split(':')[-1]
    line = i.get('line','?')
    sev = i.get('severity','?')
    typ = i.get('type','?')
    msg = i.get('message','')
    print(f'[{sev}] {typ} - {comp}:{line}')
    print(f'  {msg}')
"
```

### Quality Gate Status

```bash
curl -s -u "$SONAR_TOKEN:" \
  "https://sonar.etendo.cloud/api/qualitygates/project_status?projectKey=<projectKey>&pullRequest=<PR>"
```

## Verified Workflow (tested 2026-03-05)

This full workflow was tested and confirmed working:

1. **Run scanner** from module root — uploads analysis to server (~20s)
2. **Wait 5s** for server processing
3. **Query API** for issues — get structured JSON with severity, type, file, line, message
4. **Fix issues** in code (e.g., extract duplicated literals into constants)
5. **Re-run scanner** to verify fixes

Quality profiles used by the server:
- Java: `Futit`
- Python: `Futit`
- XML: `Futit`
- JSON: `Sonar way`

Common issue types found:
- `CRITICAL CODE_SMELL`: duplicated string literals — extract to `private static final String`
- Missing `sonar.java.libraries` warning (non-blocking, reduces precision)

## Skill Integration Notes

A future `/etendo:sonar` skill should:
1. Auto-detect the module's `sonar-project.properties` and extract `projectKey`
2. Auto-detect current branch and open PR number via `gh pr view --json number,headRefName`
3. Read token from `$SONAR_TOKEN` env var (must be set in `~/.zshrc`)
4. Run `sonar-scanner` with the correct parameters
5. Wait for server processing, then query the API for issues
6. Display issues in a table format with file, line, severity, and message
7. Optionally auto-fix common issues (e.g., extract duplicated literals to constants)
