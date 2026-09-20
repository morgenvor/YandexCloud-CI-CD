# Java application delivery

## Project overview

A small Java 17 / Spring Boot service delivered with Docker, Jenkins, and Helmfile. The repository demonstrates a practical Yandex Cloud delivery flow: build and test an application, publish an immutable container image, deploy it to Yandex Managed Kubernetes, verify rollouts, and run an HTTP smoke test.

The Kubernetes bundle is in [`helm/`](helm/), with detailed deployment documentation in [`helm/README.md`](helm/README.md).

## Application

The service listens on port `8080` and uses direct MySQL JDBC access. On startup it creates the `team_members` table if needed and seeds it when empty.

- `GET /get-data` returns team members.
- `POST /update-roles` updates team-member roles.
- Database configuration uses `DB_USER`, `DB_PWD`, `DB_SERVER`, and `DB_NAME`.

## Local development

Copy `.env.example` to `.env`, fill in local values, and keep the file uncommitted. Then run:

```bash
./gradlew clean build
docker compose up --build
```

Docker Compose builds the application image and starts Java on `localhost:8080`, MySQL on `localhost:3306`, and phpMyAdmin on `localhost:8085`. MySQL data is stored in the `mysql-data` volume.

## CI/CD pipeline

A GitHub push triggers Jenkins through the configured GitHub webhook. The inline [`Jenkinsfile`](Jenkinsfile) performs this flow:

```text
GitHub push → GitHub webhook → Jenkins → Gradle build/test
  → Docker image build → Yandex Container Registry
  → immutable image digest → temporary kubeconfig via Yandex Cloud CLI (yc)
  → Helmfile validation/deployment → Kubernetes rollout checks
  → HTTP smoke test: GET /get-data
```

The image is initially tagged with the full Git commit SHA. Jenkins then deploys the resolved registry digest, so Kubernetes does not depend on a mutable tag. The Jenkins agent must provide Java/Gradle, Docker, `yc`, `kubectl`, Helm, Helmfile, `curl`, and Git. No Jenkins Dockerfile or Shared Library is part of this repository.

Jenkins uses a Yandex Cloud Service Account for Yandex Cloud access and a namespace-scoped Kubernetes `Role`/`RoleBinding` in `default`. Those RBAC objects are provisioned externally; this repository does not grant Jenkins cluster-scoped permissions.

## Kubernetes deployment

Helmfile deploys three releases to the existing `default` namespace: MySQL from the Bitnami OCI chart (`14.0.3`), the Java application from the local `myappchart`, and phpMyAdmin from the local `phpmyadminchart`.

`ingress-nginx` is external cluster infrastructure and is not managed by this Helmfile. The application chart creates the application `Ingress` resource and expects the existing `nginx` IngressClass.

Before deployment, `Secret/mysql-secret` must exist in `default`. Required key names are:

```text
mysql-root-password
mysql-password
mysql-replication-password
```

Secret values, cluster bootstrap, and Jenkins RBAC are external to this repository.

## Validation / useful commands

Run Helmfile commands from `helm/`. All three variables are required by the current Helmfile:

```bash
cd helm
APP_IMAGE=cr.yandex/<registry-id>/gradle-app APP_VERSION=dev APP_IMAGE_DIGEST=sha256:dev helmfile lint --skip-deps
APP_IMAGE=cr.yandex/<registry-id>/gradle-app APP_VERSION=dev APP_IMAGE_DIGEST=sha256:dev helmfile template --skip-deps
```

For the full release inventory, persistence settings, Secret contract, deployment behavior, and RBAC scope, see [`helm/README.md`](helm/README.md).
