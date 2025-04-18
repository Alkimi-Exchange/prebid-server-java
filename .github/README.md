# GitHub Workflows Documentation

This repository uses GitHub Actions workflows to automate Continuous Integration (CI) and Release processes. Below is an overview of the workflows located in `.github/workflows/`, with detailed guidance on configuring the Release workflow using `release-please`.

## CI Workflow (`.github/workflows/ci.yml`)

**Purpose**: Validates code changes on pull requests by running build, test, lint, security scans, and Docker image validation.

**Trigger**: Runs on `pull_request` events targeting the `master` branch.

**Steps**:
1. **Checkout**: Clones the repository using `actions/checkout@v4`.
2. **Setup**: Configures the language environment (e.g., Node.js, Python, Terraform) based on repository topics.
3. **Build**: Compiles or builds the project.
4. **Test**: Runs unit/integration tests and uploads reports as artifacts.
5. **Lint**: Checks code style and formatting.
6. **Docker Validation**:
   - Lints the `Dockerfile` using `hadolint/hadolint-action@v3.1.0`.
   - Builds the Docker image with caching (no push) using `docker/build-push-action@v6`.
   - Runs smoke tests via `docker-compose.yml` (if present).
   - Scans the image with `aquasecurity/trivy-action@master` for critical vulnerabilities, uploading an SBOM report.
7. **Security Scans**: Executes SonarQube (`sonarsource/sonarqube-scan-action@v3`) and Snyk scans.
8. **Slack Notification**: Sends a failure alert to Slack via `rtCamp/action-slack-notify@v2` if any step fails.

**Customization**: Modify the `topics` variable in the Terraform configuration (e.g., `node`, `python`, `terraform`) to adjust language-specific steps. Ensure `Dockerfile` and `docker-compose.yml` exist if Docker validation is required.

## Release Workflow (`.github/workflows/release.yml`)

**Purpose**: Automates versioning with `release-please` and pushes Docker images to Google Container Registry (GCR) upon release.

**Triggers**:
- `push` to `master`: Runs the `release-please` job to propose new versions.
- `release` event (`published`): Triggers the `docker-release` job when a release is finalized.

**Jobs**:
1. **`release-please`**:
   - **What it Does**: Analyzes commits following Conventional Commits, generates a new version (e.g., `1.0.1`), creates/updates a release PR, and tags the repository upon merge.
   - **Tool**: `googleapis/release-please-action@v4`.
   - **Outputs**:
     - `release_created`: `true` if a release PR is merged or a release is created.
     - `version`: The calculated version (e.g., `1.0.1`).

2. **`docker-release`**:
   - **Condition**: Runs if `github.event_name == 'release'` or `needs.release-please.outputs.release_created == 'true'`.
   - **Steps**:
     - **Checkout**: Clones the repository.
     - **Login to GCR**: Authenticates with `docker/login-action@v3` using `GCR_JSON_KEY`.
     - **Setup**: Configures QEMU (`docker/setup-qemu-action@v3`) and Docker Buildx (`docker/setup-buildx-action@v3`) for multi-platform builds.
     - **Metadata**: Extracts tags and labels with `docker/metadata-action@v5`, including:
       - Short SHA (e.g., `sha-abc1234`).
       - Release tag (e.g., `v1.0.1`) if tagged.
       - `release-please` version (e.g., `1.0.1`).
     - **Build and Push**: Builds and pushes the Docker image to `gcr.io/<GCP_PROJECT_ID>/<repo>` using `docker/build-push-action@v6`.
     - **Slack Notification**: Sends a failure alert to Slack if the job fails.

### Configuring Release-Please

The `release-please` job relies on two configuration files in the repository root: `release-please-config.json` and `.release-please-manifest.json`. These files control versioning and release behavior, allowing users to customize the output version and release type.

#### `release-please-config.json`
Defines how versions are calculated and what type of releases are created.

**Default Configuration**:
```json
{
  "packages": {
    ".": {
      "release-type": "simple",
      "bump-minor-pre-major": true,
      "bump-patch-for-minor-pre-major": true
    }
  }
}
```

**Key Options**:
- **`release-type`**:
  - `simple`: Basic semantic versioning (semver) with `feat` for minors and `fix` for patches.
  - `node`: For Node.js packages, updates `package.json`.
  - `python`: For Python packages, updates `setup.py` or `pyproject.toml`.
  - `go`: For Go modules, respects `go.mod`.
  - Full list: [Release-Please Release Types](https://github.com/googleapis/release-please#release-types).
- **`bump-minor-pre-major`**:
  - `true`: Before `1.0.0`, `feat` commits bump the minor version (e.g., `0.1.0` -> `0.2.0`).
  - `false`: `feat` bumps major pre-`1.0.0` (e.g., `0.1.0` -> `1.0.0`).
- **`bump-patch-for-minor-pre-major`**:
  - `true`: Before `1.0.0`, minor bumps are treated as patches (e.g., `0.1.0` -> `0.1.1`).
  - `false`: Minor bumps follow standard semver.

**How to Change**:
- **Switch to Node.js versioning**:
  ```json
  {
    "packages": {
      ".": {
        "release-type": "node"
      }
    }
  }
  ```
- **Major bumps pre-1.0.0**:
  ```json
  {
    "packages": {
      ".": {
        "release-type": "simple",
        "bump-minor-pre-major": false
      }
    }
  }
  ```
- **Multiple Packages**:
  ```json
  {
    "packages": {
      "frontend": {
        "release-type": "node"
      },
      "backend": {
        "release-type": "python"
      }
    }
  }
  ```

#### `.release-please-manifest.json`
Tracks the last released version for each package to determine the next version.

**Default (Empty Start)**:
```json
{}
```

**After a Release**:
```json
{
  ".": "1.0.0"
}
```

**How to Change**:
- **Reset to a Specific Version**:
  - To start at `2.0.0`, set:
    ```json
    {
      ".": "1.9.9"
    }
    ```
  - Next `feat` commit bumps to `2.0.0`.
- **Force a Version**:
  - Manually tag the repo:
    ```bash
    git tag v3.0.0
    git push --tags
    ```
  - Update manifest:
    ```json
    {
      ".": "3.0.0"
    }
    ```

**Commit Conventions**:
- Follow [Conventional Commits](https://www.conventionalcommits.org/):
  - `feat: add feature` -> Minor bump (e.g., `1.0.0` -> `1.1.0`).
  - `fix: bug fix` -> Patch bump (e.g., `1.0.0` -> `1.0.1`).
  - `feat!: breaking change` -> Major bump (e.g., `1.0.0` -> `2.0.0`).
  - `chore: update docs` -> No version bump (unless configured otherwise).

### Customizing Version Output
- **Desired Version**: Adjust `release-type`, `bump-minor-pre-major`, and the manifest to control semver increments.
- **Release Type**: Change `release-type` to match your project (e.g., `node`, `python`) or add custom rules.
- **Docker Tags**: The Docker image is tagged with the `release-please` version (e.g., `1.0.1`), commit SHA, and Git tag (if present).

### Prerequisites
- **Secrets**:
  - `GCR_JSON_KEY`: GCR service account key.
  - `GCP_PROJECT_ID`: GCP project ID for GCR.
  - `SLACK_WEBHOOK_URL`: Slack webhook for notifications.
- **Files**:
  - `Dockerfile`: Required for Docker builds.
  - `docker-compose.yml`: Optional for smoke tests.
  - `release-please-config.json`: Versioning config.
  - `.release-please-manifest.json`: Version tracking.

For advanced configuration, refer to the [Release-Please GitHub Action documentation](https://github.com/googleapis/release-please-action).
