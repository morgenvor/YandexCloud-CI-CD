# Java application delivery

This repository contains a Java 17 Spring Boot application, its Docker and
local Docker Compose configuration, an inline Jenkins pipeline, and the
Helmfile bundle used to deploy the application to Yandex Managed Kubernetes.

## Application

The service exposes `GET /get-data` to return team members and
`POST /update-roles` to update their roles. At startup it creates and seeds
the `team_members` table if necessary.

Database configuration is supplied only by `DB_USER`, `DB_PWD`, `DB_SERVER`,
and `DB_NAME`. The service listens on port `8080` and uses direct JDBC rather
than an ORM.

## Local development

`docker-compose.yaml` starts MySQL `9.7.2` on port `3306`, the application on
port `8080`, and phpMyAdmin `5.2` on port `8085`. MySQL data is stored in the
local `mysql-data` volume. Supply database variables through your environment
or an untracked environment file; never commit passwords.

```sh
./gradlew clean build
docker compose up --build
```

## Deployment flow

Jenkins builds and tests with the Gradle wrapper, tags and pushes the image to
Yandex Container Registry with the full Git commit SHA, records its digest,
then deploys the immutable digest through Helmfile in `helm/`.

The pipeline obtains a temporary kubeconfig through `yc`, checks that
`mysql-secret` exists, runs Helmfile lint/template, applies Helmfile with wait
and atomic rollback, waits for the two Deployments and two MySQL StatefulSets,
then runs an HTTP smoke test through the configured public endpoint.

The Helmfile directory is the sole owner of Kubernetes application resources.
It deploys all releases to `default`; raw manifests are not used.

## Required cluster Secret

Before deployment, the target namespace must already contain a Secret named
`mysql-secret` with these keys:

- `mysql-root-password`
- `mysql-password`
- `mysql-replication-password`

The Secret is intentionally not stored in Git or rendered by the Helm charts.
Create it through a protected environment/bootstrap process using the actual
rotated values. Never commit the values or place them in a sample file.

Jenkins authenticates with a Yandex Cloud Service Account. It requires
`k8s.cluster-api.viewer` for cluster API authentication and a namespace-scoped
Kubernetes Role and RoleBinding for deployment. It must not receive
cluster-wide Kubernetes RBAC rights. The least-privilege manifest and full
resource inventory are documented in [helm/README.md](helm/README.md).

`ingress-nginx` is operated separately; this repository manages only
`Ingress/myapp` in `default`.

## Jenkins agent requirements

The Jenkins agent must also include `curl` for the post-deployment smoke test.

The agent needs Docker with Buildx, `yc`, `kubectl`, Helm, Helmfile, `curl`,
and the Java/Gradle execution requirements. The repository was validated with
Helm `4.2.4` and Helmfile `1.7.4`.

## Helmfile validation

```sh
cd helm
APP_VERSION=dev APP_IMAGE_DIGEST=sha256:dev helmfile lint --skip-deps
APP_VERSION=dev APP_IMAGE_DIGEST=sha256:dev helmfile template --skip-deps
```

See [helm/README.md](helm/README.md) for release configuration and RBAC.
